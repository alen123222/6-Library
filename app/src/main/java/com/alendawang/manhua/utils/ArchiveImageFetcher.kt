package com.alendawang.manhua.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.alendawang.manhua.model.ArchiveImageRef
import com.alendawang.manhua.model.ComicSourceType
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.source
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile as JZipFile

// =============================================================
// Coil 自定义 Fetcher — 直接从压缩包按需读取图片，无需预解压到文件
// =============================================================

class ArchiveImageFetcher(
    private val data: ArchiveImageRef,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val context = options.context
        val buffer = Buffer()

        when (data.sourceType) {
            ComicSourceType.ZIP -> fetchFromZip(context, buffer)
            ComicSourceType.RAR -> fetchFromRar(context, buffer)
            else -> throw IllegalArgumentException("ArchiveImageFetcher 不支持: ${data.sourceType}")
        }

        SourceResult(
            source = ImageSource(buffer, context),
            mimeType = guessMimeType(data.entryName),
            dataSource = DataSource.DISK
        )
    }

    /** 从缓存的 ZIP 文件中随机访问指定条目 */
    private fun fetchFromZip(context: Context, buffer: Buffer) {
        val localFile = getOrCacheZipFile(context, data.archiveUri)
            ?: throw IOException("无法缓存 ZIP 文件")

        val charset = ZipCharsetCache.get(context, data.archiveUri)
        val zipFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            JZipFile(localFile, charset)
        } else {
            JZipFile(localFile)
        }

        try {
            val entry = zipFile.getEntry(data.entryName)
                ?: throw IOException("ZIP 条目未找到: ${data.entryName}")
            zipFile.getInputStream(entry).use { inputStream ->
                buffer.writeAll(inputStream.source())
            }
        } finally {
            zipFile.close()
        }
    }

    /** 从缓存的 RAR 文件中读取指定条目 */
    private fun fetchFromRar(context: Context, buffer: Buffer) {
        val cacheFile = getCachedArchiveFile(context, data.archiveUri, "rar")
            ?: throw IOException("无法缓存 RAR 文件")

        val archive = Archive(cacheFile)
        try {
            val header = archive.fileHeaders.find { it.fileName == data.entryName }
                ?: throw IOException("RAR 条目未找到: ${data.entryName}")
            archive.getInputStream(header).use { inputStream ->
                buffer.writeAll(inputStream.source())
            }
        } finally {
            archive.close()
        }
    }

    private fun guessMimeType(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }
}

// --- Fetcher.Factory ---
class ArchiveImageFetcherFactory : Fetcher.Factory<ArchiveImageRef> {
    override fun create(data: ArchiveImageRef, options: Options, imageLoader: ImageLoader): Fetcher {
        return ArchiveImageFetcher(data, options)
    }
}

// --- Keyer (Coil 内存缓存 key) ---
class ArchiveImageKeyer : Keyer<ArchiveImageRef> {
    override fun key(data: ArchiveImageRef, options: Options): String = data.cacheKey()
}

// =============================================================
// ZIP Charset 缓存 — 避免每次 Fetch 都重新检测编码
// =============================================================
internal object ZipCharsetCache {
    private val cache = ConcurrentHashMap<String, Charset>()

    fun get(context: Context, zipUri: Uri): Charset {
        return cache.getOrPut(zipUri.toString()) { getZipCharset(context, zipUri) }
    }

    fun clear() = cache.clear()
}

// =============================================================
// ZIP 文件本地缓存 — 将 SAF URI 的 ZIP 复制到本地以支持 ZipFile 随机访问
// =============================================================
internal fun getOrCacheZipFile(context: Context, zipUri: Uri): File? {
    return try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(zipUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }

        val cacheDir = File(context.cacheDir, "zip_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val cacheFile = File(cacheDir, "$hash.zip")
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile

        // 写到临时文件再原子重命名，防止并发写入
        val tempFile = File(cacheDir, "$hash.tmp")
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        }

        if (cacheFile.exists()) {
            // 另一个线程抢先完成了
            tempFile.delete()
        } else {
            tempFile.renameTo(cacheFile)
        }

        cacheFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
