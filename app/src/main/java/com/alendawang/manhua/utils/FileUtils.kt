package com.alendawang.manhua.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.alendawang.manhua.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import android.os.Build

// --- 图片文件扩展名 ---
private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

// --- 判断是否为图片文件 ---
private fun isImageFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in imageExtensions
}

// --- 根据文件名获取漫画来源类型 ---
fun getComicSourceType(fileName: String): ComicSourceType {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "zip", "cbz" -> ComicSourceType.ZIP
        "rar", "cbr" -> ComicSourceType.RAR
        "pdf" -> ComicSourceType.PDF
        else -> ComicSourceType.FOLDER
    }
}

// --- 查找压缩包内的"叶子目录"（包含图片但没有子目录的目录）---
private fun findLeafDirectoriesInArchive(entries: List<String>): List<String> {
    // 收集所有图片文件的目录路径
    val dirsWithImages = entries
        .filter { !it.endsWith('/') && isImageFile(it) }
        .map { path ->
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash >= 0) path.substring(0, lastSlash + 1) else ""
        }
        .toSet()
    
    // 如果所有图片都在根目录，返回空列表（作为单章节处理）
    if (dirsWithImages.isEmpty() || (dirsWithImages.size == 1 && dirsWithImages.first() == "")) {
        return emptyList()
    }
    
    // 找出叶子目录：有图片且没有子目录的目录
    return dirsWithImages.filter { dir ->
        // 检查是否有其他目录是它的子目录
        dirsWithImages.none { other ->
            other != dir && other.startsWith(dir) && other.length > dir.length
        }
    }.sortedWith { a, b -> compareNatural(a.trimEnd('/').substringAfterLast('/'), b.trimEnd('/').substringAfterLast('/')) }
}

// ---以此尝试检测 ZIP 的编码 (UTF-8 vs GBK) ---
private fun getZipCharset(context: Context, zipUri: Uri): Charset {
    // 默认使用 UTF-8
    var charset = StandardCharsets.UTF_8
    
    // API 24+ 支持指定编码
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            // 尝试用 UTF-8 读取
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        // 检查是否存在乱码特征字符 (这里简单检查是否包含 replacement character)
                        // 注意: ZipInputStream在此处解码失败通常会抛出 IllegalArgumentException 或者产生乱码
                        // 如果是乱码，通常表现为包含  (U+FFFD)
                        if (entry.name.contains("")) {
                            charset = Charset.forName("GBK")
                            return charset
                        }
                        return charset // 只要成功读取第一个条目且无乱码，就认为是 UTF-8 (或兼容)
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            // UTF-8 解码失败，很可能是 GBK
            try {
                charset = Charset.forName("GBK")
            } catch (_: Exception) {
                // 如果不支持 GBK，回退到 UTF-8
            }
        } catch (e: Exception) {
            // 其他错误，忽略
        }
    }
    return charset
}

// --- 获取 ZipInputStream (带编码处理) ---
private fun getZipInputStream(context: Context, zipUri: Uri, charset: Charset): ZipInputStream? {
    val inputStream = context.contentResolver.openInputStream(zipUri) ?: return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
       ZipInputStream(inputStream, charset)
    } else {
       ZipInputStream(inputStream)
    }
}

// --- 从ZIP/CBZ加载图片 (流式读取，支持指定内部路径) ---
suspend fun loadImagesFromZip(context: Context, zipUri: Uri, internalPath: String? = null): List<Uri> = withContext(Dispatchers.IO) {
    try {
        // 使用 URI 和 internalPath 的 hash 作为缓存目录名
        val hashSource = zipUri.toString() + (internalPath ?: "")
        val hash = MessageDigest.getInstance("MD5")
            .digest(hashSource.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheDir = File(context.cacheDir, "comic_images/$hash")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        // 先检测编码
        val charset = getZipCharset(context, zipUri)

        // 先收集所有图片条目信息（过滤内部路径）
        val imageEntries = mutableListOf<String>()
        // 使用带编码的 ZipInputStream
        getZipInputStream(context, zipUri, charset)?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        // 如果指定了 internalPath，只收集该路径下的图片
                        if (internalPath == null || entry.name.startsWith(internalPath)) {
                            imageEntries.add(entry.name)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        
        
        // 按文件名自然排序
        imageEntries.sortWith { a, b -> 
            compareNatural(a.substringAfterLast('/'), b.substringAfterLast('/')) 
        }
        
        // 创建 name -> sorted index 的映射
        val nameToIndex = imageEntries.mapIndexed { index, name -> name to index }.toMap()
        
        // 检查哪些文件已经存在
        val existingFiles = cacheDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val allExist = imageEntries.all { name ->
            val sortedIndex = nameToIndex[name] ?: 0
            val ext = name.substringAfterLast('.', "jpg")
            existingFiles.contains("${"%04d".format(sortedIndex)}.$ext")
        }
        
        // 如果不是所有文件都存在，重新提取
        if (!allExist) {
            getZipInputStream(context, zipUri, charset)?.use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && isImageFile(entry.name)) {
                            val sortedIndex = nameToIndex[entry.name]
                            if (sortedIndex != null) {
                                val ext = entry.name.substringAfterLast('.', "jpg")
                                val imageFile = File(cacheDir, "${"%04d".format(sortedIndex)}.$ext")
                                if (!imageFile.exists()) {
                                    imageFile.outputStream().use { output ->
                                        zis.copyTo(output)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        
        // 返回排序后的 URI 列表
        imageEntries.mapIndexed { index, name ->
            val ext = name.substringAfterLast('.', "jpg")
            Uri.fromFile(File(cacheDir, "${"%04d".format(index)}.$ext"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// --- 计算ZIP/CBZ中的图片数量 (流式读取，支持指定内部路径) ---
suspend fun countImagesInZip(context: Context, zipUri: Uri, internalPath: String? = null): Int = withContext(Dispatchers.IO) {
    try {
        var count = 0
        val charset = getZipCharset(context, zipUri)
        getZipInputStream(context, zipUri, charset)?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        if (internalPath == null || entry.name.startsWith(internalPath)) {
                            count++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        count
    } catch (e: Exception) {
        0
    }
}

// --- 收集ZIP/CBZ中所有条目名称 ---
private suspend fun collectZipEntries(context: Context, zipUri: Uri): List<String> = withContext(Dispatchers.IO) {
    try {
        val entries = mutableListOf<String>()
        val charset = getZipCharset(context, zipUri)
        getZipInputStream(context, zipUri, charset)?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries.add(entry.name)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        entries
    } catch (e: Exception) {
        emptyList()
    }
}

// --- 收集RAR/CBR中所有条目名称 ---
private suspend fun collectRarEntries(context: Context, rarUri: Uri): List<String> = withContext(Dispatchers.IO) {
    try {
        val cacheFile = getCachedArchiveFile(context, rarUri, "rar") ?: return@withContext emptyList()
        val archive = com.github.junrar.Archive(cacheFile)
        val entries = archive.fileHeaders.map { it.fileName }
        archive.close()
        entries
    } catch (e: Exception) {
        emptyList()
    }
}

// --- 获取ZIP/CBZ的封面 (流式读取，支持指定内部路径) ---
suspend fun getCoverFromZip(context: Context, zipUri: Uri, internalPath: String? = null): String? = withContext(Dispatchers.IO) {
    try {
        val hashSource = zipUri.toString() + (internalPath ?: "")
        val hash = MessageDigest.getInstance("MD5")
            .digest(hashSource.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        val coverDir = File(context.cacheDir, "comic_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val coverFile = File(coverDir, "${hash}_cover.jpg")
        
        if (coverFile.exists()) return@withContext coverFile.absolutePath
        
        // 先检测编码
        val charset = getZipCharset(context, zipUri)
        
        // 先收集所有图片名称以找到排序后的第一个（过滤内部路径）
        val imageNames = mutableListOf<String>()
        getZipInputStream(context, zipUri, charset)?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        if (internalPath == null || entry.name.startsWith(internalPath)) {
                            imageNames.add(entry.name)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        
        if (imageNames.isEmpty()) return@withContext null
        
        imageNames.sortWith { a, b -> 
            compareNatural(a.substringAfterLast('/'), b.substringAfterLast('/')) 
        }
        val firstImageName = imageNames.first()
        
        // 再次读取以提取封面
        getZipInputStream(context, zipUri, charset)?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == firstImageName) {
                        coverFile.outputStream().use { output ->
                            zis.copyTo(output)
                        }
                        return@withContext coverFile.absolutePath
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        
        null
    } catch (e: Exception) {
        null
    }
}

// --- 缓存压缩包文件到本地 (仅用于 RAR/CBR, 因为 junrar 库需要本地文件) ---
private fun getCachedArchiveFile(context: Context, uri: Uri, ext: String): File? {
    try {
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val cacheDir = File(context.cacheDir, "archives")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val file = File(cacheDir, "$hash.$ext")
        if (file.exists() && file.length() > 0) return file
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// --- 加载漫画图片 (统一入口，支持文件夹/ZIP/RAR/PDF) ---
suspend fun loadComicImages(context: Context, chapterUri: Uri, sourceType: ComicSourceType = ComicSourceType.FOLDER, internalPath: String? = null, onResult: (List<Uri>) -> Unit) {
    withContext(Dispatchers.IO) {
        val images = when (sourceType) {
            ComicSourceType.ZIP -> loadImagesFromZip(context, chapterUri, internalPath)
            ComicSourceType.RAR -> loadImagesFromRar(context, chapterUri, internalPath)
            ComicSourceType.PDF -> loadImagesFromPdf(context, chapterUri)
            ComicSourceType.FOLDER -> loadImagesFromFolder(context, chapterUri)
        }
        withContext(Dispatchers.Main) { onResult(images) }
    }
}

// --- 加载文件夹中的图片 (原有逻辑) ---
private suspend fun loadImagesFromFolder(context: Context, folderUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
    val images = mutableListOf<Uri>()
    try {
        val docFile = DocumentFile.fromTreeUri(context, folderUri)
        docFile?.listFiles()?.forEach { file -> if (file.isFile && file.type?.startsWith("image/") == true) images.add(file.uri) }
        images.sortWith { u1, u2 -> compareNatural(u1.lastPathSegment ?: "", u2.lastPathSegment ?: "") }
    } catch (e: Exception) { e.printStackTrace() }
    images
}

// --- 从RAR/CBR加载图片 (支持指定内部路径) ---
suspend fun loadImagesFromRar(context: Context, rarUri: Uri, internalPath: String? = null): List<Uri> = withContext(Dispatchers.IO) {
    try {
        val cacheFile = getCachedArchiveFile(context, rarUri, "rar") ?: return@withContext emptyList()
        
        // 使用 URI 和 internalPath 的 hash 作为缓存目录名
        val hashSource = rarUri.toString() + (internalPath ?: "")
        val hash = MessageDigest.getInstance("MD5")
            .digest(hashSource.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheDir = File(context.cacheDir, "comic_images/$hash")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val archive = com.github.junrar.Archive(cacheFile)
        val imageHeaders = archive.fileHeaders
            .filter { header -> 
                !header.isDirectory && isImageFile(header.fileName) &&
                (internalPath == null || header.fileName.startsWith(internalPath))
            }
            .sortedWith { a, b -> compareNatural(a.fileName.substringAfterLast('/'), b.fileName.substringAfterLast('/')) }
        
        val uris = imageHeaders.mapIndexed { index, header ->
            val ext = header.fileName.substringAfterLast('.', "jpg")
            val imageFile = File(cacheDir, "${"%04d".format(index)}.$ext")
            if (!imageFile.exists()) {
                archive.getInputStream(header).use { input ->
                    imageFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Uri.fromFile(imageFile)
        }
        
        archive.close()
        uris
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// --- 计算RAR/CBR中的图片数量 (支持指定内部路径) ---
suspend fun countImagesInRar(context: Context, rarUri: Uri, internalPath: String? = null): Int = withContext(Dispatchers.IO) {
    try {
        val cacheFile = getCachedArchiveFile(context, rarUri, "rar") ?: return@withContext 0
        val archive = com.github.junrar.Archive(cacheFile)
        val count = archive.fileHeaders.count { header -> 
            !header.isDirectory && isImageFile(header.fileName) &&
            (internalPath == null || header.fileName.startsWith(internalPath))
        }
        archive.close()
        count
    } catch (e: Exception) {
        0
    }
}

// --- 获取RAR/CBR的封面 (支持指定内部路径) ---
suspend fun getCoverFromRar(context: Context, rarUri: Uri, internalPath: String? = null): String? = withContext(Dispatchers.IO) {
    try {
        val cacheFile = getCachedArchiveFile(context, rarUri, "rar") ?: return@withContext null
        val archive = com.github.junrar.Archive(cacheFile)
        
        val firstImage = archive.fileHeaders
            .filter { header -> 
                !header.isDirectory && isImageFile(header.fileName) &&
                (internalPath == null || header.fileName.startsWith(internalPath))
            }
            .sortedWith { a, b -> compareNatural(a.fileName.substringAfterLast('/'), b.fileName.substringAfterLast('/')) }
            .firstOrNull()
        
        if (firstImage == null) {
            archive.close()
            return@withContext null
        }
        
        // 使用 URI 和 internalPath 的 hash 作为封面文件名
        val hashSource = rarUri.toString() + (internalPath ?: "")
        val hash = MessageDigest.getInstance("MD5")
            .digest(hashSource.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        val coverDir = File(context.cacheDir, "comic_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val coverFile = File(coverDir, "${hash}_cover.jpg")
        
        if (!coverFile.exists()) {
            archive.getInputStream(firstImage).use { input ->
                coverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        archive.close()
        coverFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

// --- 清理单个漫画的图片缓存 (退出阅读器时调用，保留封面) ---
fun clearComicImageCache(context: Context, chapterUri: Uri) {
    try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(chapterUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        // 删除解压/渲染的图片目录
        val imagesDir = File(context.cacheDir, "comic_images/$hash")
        if (imagesDir.exists() && imagesDir.isDirectory) {
            imagesDir.deleteRecursively()
        }
        
        // 如果是 RAR，也删除压缩包副本
        val archiveFile = File(context.cacheDir, "archives/$hash.rar")
        if (archiveFile.exists()) {
            archiveFile.delete()
        }
        
        // 注意: 保留封面图片 (在 comic_covers/ 目录)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- 清理所有漫画图片缓存 (应用启动时调用，保留封面) ---
fun clearAllComicImageCaches(context: Context) {
    try {
        // 删除所有漫画图片缓存
        val comicImagesDir = File(context.cacheDir, "comic_images")
        if (comicImagesDir.exists() && comicImagesDir.isDirectory) {
            comicImagesDir.deleteRecursively()
        }
        
        // 删除所有 RAR 压缩包副本
        val archivesDir = File(context.cacheDir, "archives")
        if (archivesDir.exists() && archivesDir.isDirectory) {
            archivesDir.deleteRecursively()
        }
        
        // 注意: 保留 comic_covers/ 目录中的封面
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- 从PDF加载图片 (直接读取，不缓存PDF文件) ---
suspend fun loadImagesFromPdf(context: Context, pdfUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
    try {
        // 使用 URI 的 hash 作为缓存目录名
        val hash = MessageDigest.getInstance("MD5")
            .digest(pdfUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheDir = File(context.cacheDir, "comic_images/$hash")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        // 直接从 ContentResolver 打开文件描述符
        val fd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext emptyList()
        val renderer = PdfRenderer(fd)
        
        val uris = (0 until renderer.pageCount).map { pageIndex ->
            val imageFile = File(cacheDir, "${"%04d".format(pageIndex)}.png")
            if (!imageFile.exists()) {
                val page = renderer.openPage(pageIndex)
                // 使用 2x 缩放以获得更好的质量
                val scale = 2
                val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                imageFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                bitmap.recycle()
            }
            Uri.fromFile(imageFile)
        }
        
        renderer.close()
        fd.close()
        uris
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// --- 计算PDF的页数 (直接读取) ---
suspend fun countPagesInPdf(context: Context, pdfUri: Uri): Int = withContext(Dispatchers.IO) {
    try {
        val fd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext 0
        val renderer = PdfRenderer(fd)
        val count = renderer.pageCount
        renderer.close()
        fd.close()
        count
    } catch (e: Exception) {
        0
    }
}

// --- 获取PDF的封面 (直接读取) ---
suspend fun getCoverFromPdf(context: Context, pdfUri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(pdfUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        val coverDir = File(context.cacheDir, "comic_covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val coverFile = File(coverDir, "${hash}_cover.jpg")
        
        if (coverFile.exists()) return@withContext coverFile.absolutePath
        
        val fd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext null
        val renderer = PdfRenderer(fd)
        
        if (renderer.pageCount > 0) {
            val page = renderer.openPage(0)
            val scale = 2
            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            coverFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
        }
        
        renderer.close()
        fd.close()
        
        if (coverFile.exists()) coverFile.absolutePath else null
    } catch (e: Exception) {
        null
    }
}


// --- 计算文件夹中的图片数量 (仅用于文件夹类型) ---
suspend fun countImagesInFolder(context: Context, folderUri: Uri): Int {
    return withContext(Dispatchers.IO) {
        try {
            val docFile = DocumentFile.fromTreeUri(context, folderUri)
            docFile?.listFiles()?.count { it.isFile && it.type?.startsWith("image/") == true } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}

// --- 统一计算漫画页数 (支持所有来源类型和内部路径) ---
suspend fun countComicPages(context: Context, chapterUri: Uri, sourceType: ComicSourceType, internalPath: String? = null): Int {
    return when (sourceType) {
        ComicSourceType.ZIP -> countImagesInZip(context, chapterUri, internalPath)
        ComicSourceType.RAR -> countImagesInRar(context, chapterUri, internalPath)
        ComicSourceType.PDF -> countPagesInPdf(context, chapterUri)
        ComicSourceType.FOLDER -> countImagesInFolder(context, chapterUri)
    }
}


// --- 计算漫画阅读进度 ---
suspend fun computeComicProgress(context: Context, history: ComicHistory): Pair<Int, Int> {
    val chapterCounts = mutableListOf<Int>()
    for (chapter in history.chapters) {
        chapterCounts.add(countComicPages(context, chapter.uriString.toUri(), chapter.sourceType, chapter.internalPath))
    }
    val totalPages = chapterCounts.sum()
    if (totalPages <= 0) return 0 to 0

    val safeChapterIndex = history.lastReadChapterIndex.coerceIn(0, maxOf(0, history.chapters.size - 1))
    val pagesBefore = chapterCounts.take(safeChapterIndex).sum()
    val currentChapterPages = chapterCounts.getOrNull(safeChapterIndex) ?: 0
    val isUnread = history.lastReadChapterIndex == 0 && history.lastReadIndex <= 0
    val currentInChapter = if (isUnread) {
        0
    } else {
        (history.lastReadIndex + 1).coerceIn(0, currentChapterPages)
    }
    val currentPage = (pagesBefore + currentInChapter).coerceAtMost(totalPages)
    return totalPages to currentPage
}

// --- 漫画扫描 (支持增量更新, 支持文件夹/ZIP/RAR/PDF) ---
fun scanComicsFlow(
    context: Context, 
    rootTreeUri: Uri, 
    existingComics: Map<String, ComicHistory>,  // 改为 Map 便于查找
    onFolderScanning: (String) -> Unit
): Flow<ComicHistory> = flow {
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@flow
    val level1Files = rootDoc.listFiles()
    val currentTime = System.currentTimeMillis()
    
    // 处理压缩包扩展名
    val archiveExtensions = setOf("zip", "cbz", "rar", "cbr", "pdf")
    
    for (level1File in level1Files) {
        currentCoroutineContext().ensureActive()
        
        val fileName = level1File.name ?: continue
        val fileUri = level1File.uri.toString()
        val existingComic = existingComics[fileUri]
        val fileLastModified = level1File.lastModified()
        
        // 增量扫描：如果文件未变化且已扫描过，跳过
        if (existingComic != null && 
            existingComic.lastScannedAt > 0 && 
            fileLastModified <= existingComic.lastScannedAt) {
            continue
        }
        
        // 检查是否为压缩包文件
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (level1File.isFile && ext in archiveExtensions) {
            // 处理压缩包文件
            withContext(Dispatchers.Main) { onFolderScanning(fileName) }
            
            val sourceType = getComicSourceType(fileName)
            val comicName = fileName.substringBeforeLast('.')
            
            // PDF 不支持章节检测，直接作为单章节处理
            if (sourceType == ComicSourceType.PDF) {
                val coverUri = getCoverFromPdf(context, level1File.uri)
                val totalPages = countPagesInPdf(context, level1File.uri)
                if (totalPages > 0) {
                    val chapters = listOf(ComicChapter("全一册", fileUri, sourceType))
                    val comic = if (existingComic != null) {
                        existingComic.copy(
                            name = comicName,
                            coverUriString = coverUri ?: existingComic.coverUriString,
                            chapters = chapters,
                            cachedTotalPages = totalPages,
                            lastScannedAt = currentTime
                        )
                    } else {
                        ComicHistory(
                            id = fileUri,
                            name = comicName,
                            uriString = fileUri,
                            coverUriString = coverUri,
                            timestamp = currentTime,
                            chapters = chapters,
                            cachedTotalPages = totalPages,
                            cachedCurrentPage = 0,
                            lastScannedAt = currentTime
                        )
                    }
                    emit(comic)
                }
                continue
            }
            
            // ZIP/RAR: 收集所有条目名称，检测章节
            val allEntries = when (sourceType) {
                ComicSourceType.ZIP -> collectZipEntries(context, level1File.uri)
                ComicSourceType.RAR -> collectRarEntries(context, level1File.uri)
                else -> emptyList()
            }
            
            // 查找叶子目录（章节）
            val leafDirs = findLeafDirectoriesInArchive(allEntries)
            
            val chapters: List<ComicChapter>
            var totalPages = 0
            var coverUri: String? = null
            
            if (leafDirs.isEmpty()) {
                // 没有子目录，作为单章节处理
                val pageCount = when (sourceType) {
                    ComicSourceType.ZIP -> countImagesInZip(context, level1File.uri)
                    ComicSourceType.RAR -> countImagesInRar(context, level1File.uri)
                    else -> 0
                }
                if (pageCount > 0) {
                    chapters = listOf(ComicChapter("全一册", fileUri, sourceType))
                    totalPages = pageCount
                    coverUri = when (sourceType) {
                        ComicSourceType.ZIP -> getCoverFromZip(context, level1File.uri)
                        ComicSourceType.RAR -> getCoverFromRar(context, level1File.uri)
                        else -> null
                    }
                } else {
                    continue // 没有图片，跳过
                }
            } else {
                // 有子目录，每个叶子目录作为一个章节
                val chapterList = mutableListOf<ComicChapter>()
                for (dir in leafDirs) {
                    val chapterName = dir.trimEnd('/').substringAfterLast('/')
                    val pageCount = when (sourceType) {
                        ComicSourceType.ZIP -> countImagesInZip(context, level1File.uri, dir)
                        ComicSourceType.RAR -> countImagesInRar(context, level1File.uri, dir)
                        else -> 0
                    }
                    if (pageCount > 0) {
                        chapterList.add(ComicChapter(chapterName, fileUri, sourceType, dir))
                        totalPages += pageCount
                    }
                }
                if (chapterList.isEmpty()) continue // 没有章节，跳过
                chapters = chapterList
                
                // 使用第一个章节的第一张图片作为封面
                val firstChapterPath = chapters.first().internalPath
                coverUri = when (sourceType) {
                    ComicSourceType.ZIP -> getCoverFromZip(context, level1File.uri, firstChapterPath)
                    ComicSourceType.RAR -> getCoverFromRar(context, level1File.uri, firstChapterPath)
                    else -> null
                }
            }
            
            val comic = if (existingComic != null) {
                existingComic.copy(
                    name = comicName,
                    coverUriString = coverUri ?: existingComic.coverUriString,
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    lastScannedAt = currentTime
                )
            } else {
                ComicHistory(
                    id = fileUri,
                    name = comicName,
                    uriString = fileUri,
                    coverUriString = coverUri,
                    timestamp = currentTime,
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    cachedCurrentPage = 0,
                    lastScannedAt = currentTime
                )
            }
            emit(comic)
            continue
        }

        // 处理文件夹（原有逻辑）
        if (!level1File.isDirectory) continue
        
        val comicName = fileName
        withContext(Dispatchers.Main) { onFolderScanning(comicName) }

        try {
            if (level1File.findFile(".nomedia") == null) {
                level1File.createFile("application/octet-stream", ".nomedia")
            }
        } catch (e: Exception) { }

        val subFiles = level1File.listFiles()
        val chapters = mutableListOf<ComicChapter>()
        var coverUri: String? = null
        var totalPages = 0
        
        val folderChapters = subFiles.filter { it.isDirectory }
        if (folderChapters.isNotEmpty()) {
            folderChapters.forEach { chFolder -> 
                chapters.add(ComicChapter(chFolder.name ?: "Chapter", chFolder.uri.toString(), ComicSourceType.FOLDER))
                val chapterImageCount = chFolder.listFiles().count { it.isFile && it.type?.startsWith("image/") == true }
                totalPages += chapterImageCount
            }
            val firstChapterImages = folderChapters.first().listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
            if (firstChapterImages.isNotEmpty()) coverUri = firstChapterImages.sortedWith { a, b -> compareNatural(a.name?:"", b.name?:"") }.first().uri.toString()
        } else {
            val images = subFiles.filter { it.isFile && it.type?.startsWith("image/") == true }
            if (images.isNotEmpty()) {
                chapters.add(ComicChapter("全一册", level1File.uri.toString(), ComicSourceType.FOLDER))
                coverUri = images.sortedWith { a, b -> compareNatural(a.name?:"", b.name?:"") }.first().uri.toString()
                totalPages = images.size
            }
        }
        chapters.sortWith { c1, c2 -> compareNatural(c1.name, c2.name) }
        
        if (chapters.isNotEmpty()) {
            // 如果是更新已有漫画，保留用户设置的属性
            val comic = if (existingComic != null) {
                existingComic.copy(
                    name = comicName,
                    coverUriString = coverUri ?: existingComic.coverUriString,
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    lastScannedAt = currentTime
                    // 保留: timestamp, lastReadIndex, lastReadChapterIndex, isFavorite, isNsfw, cachedCurrentPage
                )
            } else {
                ComicHistory(
                    id = fileUri, 
                    name = comicName, 
                    uriString = fileUri, 
                    coverUriString = coverUri, 
                    timestamp = currentTime, 
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    cachedCurrentPage = 0,
                    lastScannedAt = currentTime
                )
            }
            emit(comic)
        }
    }
}.flowOn(Dispatchers.IO)


// 兼容旧接口（转换 Set 为 Map）
fun scanComicsFlow(context: Context, rootTreeUri: Uri, existingUris: Set<String>, onFolderScanning: (String) -> Unit): Flow<ComicHistory> {
    // 旧接口没有完整的 ComicHistory 信息，所以创建空 Map，相当于全量扫描
    val emptyMap = existingUris.associateWith { uri -> 
        ComicHistory(id = uri, name = "", uriString = uri, coverUriString = null, timestamp = 0, lastScannedAt = Long.MAX_VALUE)
    }
    return scanComicsFlow(context, rootTreeUri, emptyMap, onFolderScanning)
}

// --- 小说扫描 ---
fun scanNovelsFlow(context: Context, rootTreeUri: Uri, existingUris: Set<String>, onFolderScanning: (String) -> Unit): Flow<NovelHistory> = flow {
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@flow
    withContext(Dispatchers.Main) { onFolderScanning(rootDoc.name ?: "小说") }

    try {
        if (rootDoc.findFile(".nomedia") == null) {
            rootDoc.createFile("application/octet-stream", ".nomedia")
        }
    } catch (_: Exception) { }

    val files = rootDoc.listFiles()
    
    val novelFiles = files
        .filter { 
            it.isFile && (
                it.name?.endsWith(".txt", ignoreCase = true) == true || 
                it.name?.endsWith(".epub", ignoreCase = true) == true ||
                it.type?.startsWith("text/") == true ||
                it.type == "application/epub+zip"
            )
        }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }

    val coverUri = files
        .filter { it.isFile && it.type?.startsWith("image/") == true }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
        .firstOrNull()
        ?.uri
        ?.toString()

    if (novelFiles.isNotEmpty()) {
        novelFiles.forEach { file ->
            val fileUri = file.uri.toString()
            if (existingUris.contains(fileUri)) return@forEach
            val fileName = file.name ?: "Chapter"
            
            val isEpub = fileName.endsWith(".epub", ignoreCase = true)
            
            if (isEpub) {
                val epubResult = parseEpubFile(context, file.uri)
                if (epubResult != null) {
                    val (parsedChapters, epubCoverUri) = epubResult
                    emit(
                        NovelHistory(
                            id = fileUri,
                            name = fileName.removeSuffix(".epub").removeSuffix(".EPUB"),
                            uriString = fileUri,
                            coverUriString = epubCoverUri ?: coverUri,
                            timestamp = System.currentTimeMillis(),
                            chapters = parsedChapters
                        )
                    )
                }
            } else {
                val rawText = readNovelText(context, file.uri)
                val parsedChapters = rawText?.let { parseNovelChapters(it, fileUri, fileName) }
                    ?: listOf(NovelChapter(fileName, fileUri))
                emit(
                    NovelHistory(
                        id = fileUri,
                        name = fileName,
                        uriString = fileUri,
                        coverUriString = coverUri,
                        timestamp = System.currentTimeMillis(),
                        chapters = parsedChapters
                    )
                )
            }
        }
    }
}.flowOn(Dispatchers.IO)

// --- 音频扫描 (支持专辑文件夹) ---
fun scanAudiosFlow(context: Context, rootTreeUri: Uri, existingUris: Set<String>, onFolderScanning: (String) -> Unit): Flow<AudioHistory> = flow {
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@flow
    withContext(Dispatchers.Main) { onFolderScanning(rootDoc.name ?: "音频") }

    try {
        if (rootDoc.findFile(".nomedia") == null) {
            rootDoc.createFile("application/octet-stream", ".nomedia")
        }
    } catch (_: Exception) { }

    val files = rootDoc.listFiles()
    
    // 1. 扫描根目录下的单曲（直接在根目录的音频文件）
    val rootAudioFiles = files
        .filter { it.isFile && it.type?.startsWith("audio/") == true }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
    
    val rootCoverUri = files
        .filter { it.isFile && it.type?.startsWith("image/") == true }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
        .firstOrNull()?.uri?.toString()

    // 根目录单曲：每个文件一个 AudioHistory
    rootAudioFiles.forEach { file ->
        val fileUri = file.uri.toString()
        if (existingUris.contains(fileUri)) return@forEach
        
        val coverUri = extractEmbeddedCover(context, file.uri, file.name ?: "audio") ?: rootCoverUri
        emit(
            AudioHistory(
                id = fileUri,
                name = file.name ?: "Track",
                uriString = fileUri,
                coverUriString = coverUri,
                timestamp = System.currentTimeMillis(),
                tracks = listOf(AudioTrack(file.name ?: "Track", fileUri))
            )
        )
    }

    // 2. 扫描子文件夹（每个子文件夹视为一个专辑）
    val subfolders = files.filter { it.isDirectory }
    
    subfolders.forEach { subfolder ->
        val subfiles = subfolder.listFiles()
        val albumAudioFiles = subfiles
            .filter { it.isFile && it.type?.startsWith("audio/") == true }
            .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
        
        if (albumAudioFiles.isEmpty()) return@forEach
        
        // 使用文件夹名称作为专辑 ID
        val albumId = subfolder.uri.toString()
        if (existingUris.contains(albumId)) return@forEach
        
        // 专辑封面：优先内嵌 -> 文件夹图片
        val folderCoverUri = subfiles
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
            .firstOrNull()?.uri?.toString()
        
        // 尝试从第一首歌提取内嵌封面
        val firstAudioCover = extractEmbeddedCover(context, albumAudioFiles.first().uri, albumAudioFiles.first().name ?: "audio")
        val albumCoverUri = firstAudioCover ?: folderCoverUri
        
        // 构建曲目列表
        val tracks = albumAudioFiles.map { audioFile ->
            AudioTrack(audioFile.name ?: "Track", audioFile.uri.toString())
        }
        
        emit(
            AudioHistory(
                id = albumId,
                name = subfolder.name ?: "Album",
                uriString = albumId,
                coverUriString = albumCoverUri,
                timestamp = System.currentTimeMillis(),
                tracks = tracks
            )
        )
    }
}.flowOn(Dispatchers.IO)

// 提取音频文件的内嵌封面并保存到缓存
private fun extractEmbeddedCover(context: Context, audioUri: Uri, fileName: String): String? {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, audioUri)
            val coverBytes = retriever.embeddedPicture
            if (coverBytes != null) {
                // 保存到缓存目录
                val cacheDir = File(context.cacheDir, "audio_covers")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                
                // 使用文件名的哈希作为缓存文件名
                val hash = fileName.hashCode().toString(16)
                val coverFile = File(cacheDir, "$hash.jpg")
                
                if (!coverFile.exists()) {
                    coverFile.writeBytes(coverBytes)
                }
                
                coverFile.absolutePath
            } else {
                null
            }
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    } catch (e: Exception) {
        null
    }
}

// --- 小说文本读取 ---
suspend fun loadNovelText(context: Context, fileUri: Uri, onResult: (String) -> Unit) {
    val text = readNovelText(context, fileUri) ?: ""
    withContext(Dispatchers.Main) { onResult(text) }
}

fun readNovelText(context: Context, fileUri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(fileUri)?.use { input ->
            val bytes = input.readBytes()
            val encoding = detectEncoding(bytes)
            String(bytes, encoding)
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun saveNovelText(context: Context, fileUri: Uri, text: String) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                output.write(text.toByteArray())
                output.flush()
            }
        } catch (_: Exception) { }
    }
}

// --- 编码检测 (使用 juniversalchardet 库) ---
private fun detectEncoding(bytes: ByteArray): Charset {
    return try {
        val detector = org.mozilla.universalchardet.UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        
        val detectedCharset = detector.detectedCharset
        detector.reset()
        
        if (detectedCharset != null) {
            Charset.forName(detectedCharset)
        } else {
            // 如果无法检测，默认使用 UTF-8
            StandardCharsets.UTF_8
        }
    } catch (e: Exception) {
        // 发生异常时回退到 UTF-8
        StandardCharsets.UTF_8
    }
}

// --- 章节解析辅助 ---
private val chapterHeadingRegex = Regex(
    "^\\s*(第\\s*[0-9零一二三四五六七八九十百千万两]{1,9}\\s*[章节卷回篇幕集].{0,30}|(Chapter|CHAPTER)\\s+\\d+\\b.*)\\s*$"
)
private val standaloneChapterHeadings = setOf("序章", "楔子", "前言", "后记", "尾声", "终章", "序")

private fun isChapterHeading(line: String): Boolean {
    if (line.isBlank()) return false
    if (line.length > 40) return false
    return standaloneChapterHeadings.contains(line) || chapterHeadingRegex.matches(line)
}

fun parseNovelChapters(text: String, uriString: String, fallbackName: String): List<NovelChapter> {
    val markers = mutableListOf<Pair<String, Int>>()
    val lines = text.split("\n")
    var offset = 0

    for (line in lines) {
        val trimmed = line.trim()
        if (isChapterHeading(trimmed)) {
            val title = trimmed.take(60)
            markers.add(title to offset)
        }
        offset += line.length + 1
    }

    if (markers.isEmpty()) {
        return listOf(NovelChapter(fallbackName, uriString, 0, text.length))
    }

    val chapters = mutableListOf<NovelChapter>()
    for (i in markers.indices) {
        val (title, start) = markers[i]
        val end = if (i + 1 < markers.size) markers[i + 1].second - 1 else text.length
        chapters.add(NovelChapter(title, uriString, start, end))
    }
    return chapters
}

fun extractChapterText(fullText: String, chapter: NovelChapter): String {
    val safeStart = chapter.startIndex.coerceIn(0, fullText.length)
    val safeEnd = chapter.endIndex.coerceIn(safeStart, fullText.length)
    return fullText.substring(safeStart, safeEnd)
}

fun isWholeFileChapter(chapter: NovelChapter): Boolean {
    return chapter.startIndex == 0 && chapter.endIndex > 0
}

fun replaceChapterText(fullText: String, chapter: NovelChapter, newText: String): String {
    val safeStart = chapter.startIndex.coerceIn(0, fullText.length)
    val safeEnd = chapter.endIndex.coerceIn(safeStart, fullText.length)
    return buildString {
        append(fullText.substring(0, safeStart))
        append(newText)
        append(fullText.substring(safeEnd))
    }
}

// --- EPUB解析 (流式读取，不缓存EPUB文件) ---
fun parseEpubFile(context: Context, uri: Uri): Pair<List<NovelChapter>, String?>? {
    return try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        // 第一遍：收集所有需要的内容
        var containerXml: String? = null
        var opfPath: String? = null
        var opfContent: String? = null
        var ncxContent: String? = null
        val entryContents = mutableMapOf<String, ByteArray>() // 用于封面图片
        
        // 首先读取 container.xml 找到 opf 路径
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "META-INF/container.xml" -> {
                            containerXml = String(zis.readBytes())
                        }
                        entry.name.endsWith(".opf") && opfPath == null -> {
                            opfPath = entry.name
                            opfContent = String(zis.readBytes())
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        
        // 解析 container.xml 获取 opf 路径
        if (containerXml != null) {
            val parsedOpfPath = parseContainerXml(containerXml!!)
            if (parsedOpfPath != null && parsedOpfPath != opfPath) {
                opfPath = parsedOpfPath
                opfContent = null // 需要重新读取
            }
        }
        
        if (opfPath == null) return null
        
        // 如果 opf 内容还没读取，再次读取
        if (opfContent == null) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == opfPath) {
                            opfContent = String(zis.readBytes())
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        
        if (opfContent == null) return null
        
        val opfDir = opfPath!!.substringBeforeLast("/", "")
        val (manifest, spine, coverHref) = parseOpfFile(opfContent!!)
        
        // 读取 NCX 和封面图片
        val ncxHref = manifest.entries.find { it.value.endsWith(".ncx", true) }?.value 
            ?: manifest.entries.find { it.key.equals("ncx", true) }?.value
        val ncxPath = if (ncxHref != null && opfDir.isNotEmpty()) "$opfDir/$ncxHref" else ncxHref
        val coverPath = if (coverHref != null && opfDir.isNotEmpty()) "$opfDir/$coverHref" else coverHref
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val normalizedName = normalizePath(entry.name)
                    when {
                        ncxPath != null && normalizedName == normalizePath(ncxPath) -> {
                            ncxContent = String(zis.readBytes())
                        }
                        coverPath != null && normalizedName == normalizePath(coverPath) -> {
                            entryContents[entry.name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        
        // 保存封面图片
        var coverUriString: String? = null
        if (coverPath != null && entryContents.isNotEmpty()) {
            val coverHash = Integer.toHexString(coverPath.hashCode())
            val coverCacheFile = File(context.cacheDir, "covers/${hash}_$coverHash.jpg")
            if (!coverCacheFile.exists()) {
                coverCacheFile.parentFile?.mkdirs()
                entryContents.values.firstOrNull()?.let { bytes ->
                    coverCacheFile.writeBytes(bytes)
                }
            }
            if (coverCacheFile.exists()) {
                coverUriString = coverCacheFile.absolutePath
            }
        }
        
        // 解析 NCX 获取章节标题
        val titleMap = ncxContent?.let { parseNcx(it) }
        
        // 构建章节列表
        val chapters = mutableListOf<NovelChapter>()
        spine.forEachIndexed { index, itemId ->
            val href = manifest[itemId] ?: return@forEachIndexed
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            val normalizedPath = normalizePath(fullPath)
            
            val title = titleMap?.get(href) 
                ?: titleMap?.get(href.substringAfterLast("/")) 
                ?: "第${index + 1}章"
            
            chapters.add(
                NovelChapter(
                    name = title,
                    uriString = uri.toString(),
                    startIndex = index,
                    endIndex = index,
                    isEpubChapter = true,
                    htmlContent = null,
                    internalPath = normalizedPath
                )
            )
        }
        
        Pair(chapters, coverUriString)
        
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun parseContainerXml(xml: String): String? {
    return try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            parser.next()
        }
        null
    } catch (e: Exception) { null }
}

private fun parseOpfFile(opfContent: String): Triple<Map<String, String>, List<String>, String?> {
    val manifest = mutableMapOf<String, String>()
    val spine = mutableListOf<String>()
    var coverHref: String? = null
    var coverId: String? = null
    
    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(opfContent.reader())
        
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = href
                        }
                    }
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) {
                            spine.add(idref)
                        }
                    }
                    "meta" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val content = parser.getAttributeValue(null, "content")
                        if (name == "cover" && content != null) {
                            coverId = content
                        }
                    }
                }
            }
            parser.next()
        }
        
        if (coverId != null) {
            coverHref = manifest[coverId]
        }
        
        if (coverHref == null) {
            manifest.entries.find { it.key.contains("cover", ignoreCase = true) && 
                (it.value.endsWith(".jpg", true) || it.value.endsWith(".png", true) || it.value.endsWith(".jpeg", true)) 
            }?.let { coverHref = it.value }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return Triple(manifest, spine, coverHref)
}

private fun normalizePath(path: String): String {
    val parts = path.split("/").toMutableList()
    val result = mutableListOf<String>()
    for (part in parts) {
        when (part) {
            ".." -> if (result.isNotEmpty()) result.removeAt(result.size - 1)
            ".", "" -> { }
            else -> result.add(part)
        }
    }
    return result.joinToString("/")
}

// --- EPUB章节内容加载 (流式读取) ---
fun getEpubChapterContent(context: Context, uri: Uri, internalPath: String): String {
    return try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        var htmlContent: String? = null
        val imagesToExtract = mutableListOf<Pair<String, String>>() // src -> fullPath
        
        // 第一遍：读取章节 HTML 内容
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (normalizePath(entry.name) == normalizePath(internalPath)) {
                        htmlContent = String(zis.readBytes())
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        
        if (htmlContent == null) return ""
        
        // 解析 HTML 中的图片引用
        val imgRegex = Regex("<img[^>]+src=\"([^\"]+)\"[^>]*>", RegexOption.IGNORE_CASE)
        val matches = imgRegex.findAll(htmlContent!!)
        val entryDir = internalPath.substringBeforeLast("/", "")
        
        matches.forEach { match ->
            val src = match.groupValues[1]
            if (!src.startsWith("http") && !src.startsWith("data:")) {
                val fullPath = if (entryDir.isNotEmpty()) "$entryDir/$src" else src
                imagesToExtract.add(src to normalizePath(fullPath))
            }
        }
        
        // 如果有图片需要提取
        if (imagesToExtract.isNotEmpty()) {
            val imagePathMap = imagesToExtract.toMap()
            val pathToSrc = imagesToExtract.associate { it.second to it.first }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val normalizedEntryName = normalizePath(entry.name)
                        val matchingSrc = pathToSrc[normalizedEntryName]
                        
                        if (matchingSrc != null) {
                            val imageHash = Integer.toHexString(normalizedEntryName.hashCode())
                            val ext = matchingSrc.substringAfterLast('.', "jpg")
                            val imageCacheFile = File(context.cacheDir, "epub_images/${hash}_$imageHash.$ext")
                            
                            if (!imageCacheFile.exists()) {
                                imageCacheFile.parentFile?.mkdirs()
                                try {
                                    imageCacheFile.outputStream().buffered(8192).use { output ->
                                        zis.copyTo(output, 8192)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            
                            if (imageCacheFile.exists()) {
                                htmlContent = htmlContent!!.replace("src=\"$matchingSrc\"", "src=\"file://${imageCacheFile.absolutePath}\"")
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        
        htmlContent ?: ""
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

// --- NCX解析 ---
fun parseNcx(ncxContent: String): Map<String, String> {
    val titleMap = mutableMapOf<String, String>()
    try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ncxContent.reader())
        
        var currentLabel: String? = null
        var currentSrc: String? = null
        
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "text") {
                        currentLabel = parser.nextText()
                    } else if (parser.name == "content") {
                        currentSrc = parser.getAttributeValue(null, "src")
                        if (currentLabel != null && currentSrc != null) {
                            val cleanSrc = currentSrc!!.substringBefore("#")
                            titleMap[cleanSrc] = currentLabel!!
                        }
                    } else if (parser.name == "navPoint") {
                        currentLabel = null
                    }
                }
            }
            parser.next()
        }
    } catch (e: Exception) { e.printStackTrace() }
    return titleMap
}

// --- 查找同级文件 (用于查找歌词) ---
fun findSiblingLrcFile(context: Context, audioUri: Uri): String? {
    return try {
        // 1. 尝试直接文件路径 (适用于 file:// Uri)
        if (audioUri.scheme == "file") {
            val path = audioUri.path ?: return null
            val lrcPath = path.substringBeforeLast('.') + ".lrc"
            val file = File(lrcPath)
            if (file.exists() && file.canRead()) {
                return readLrcWithEncoding(file.inputStream())
            }
        }
        
        // 2. 尝试 SAF DocumentFile
        var fileName: String? = null
        val cursor = context.contentResolver.query(audioUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) fileName = it.getString(index)
            }
        }
        
        if (fileName != null) {
             val lrcName = fileName!!.substringBeforeLast('.') + ".lrc"
             val uriString = audioUri.toString()
             val encodedName = Uri.encode(fileName)
             val encodedLrcName = Uri.encode(lrcName)
             
             if (uriString.contains(encodedName)) {
                 val lrcUriString = uriString.replace(encodedName, encodedLrcName)
                 val lrcUri = Uri.parse(lrcUriString)
                 try {
                     context.contentResolver.openInputStream(lrcUri)?.use { 
                         return readLrcWithEncoding(it) 
                     }
                 } catch (_: Exception) {}
             }
        }
        
        null
    } catch (e: Exception) {
        null
    }
}

// 尝试多种编码读取LRC文件
private fun readLrcWithEncoding(inputStream: java.io.InputStream): String {
    val bytes = inputStream.readBytes()
    
    // 尝试检测 BOM
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        // UTF-8 with BOM
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        // UTF-16LE BOM
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        // UTF-16BE BOM
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    
    // 尝试 UTF-8 解码
    val utf8Result = try {
        val decoded = String(bytes, Charsets.UTF_8)
        // 检查是否有替换字符（通常表示解码错误）
        if (!decoded.contains('\uFFFD')) decoded else null
    } catch (_: Exception) { null }
    
    if (utf8Result != null) return utf8Result
    
    // 尝试 GBK (常用于中文)
    return try {
        String(bytes, charset("GBK"))
    } catch (_: Exception) {
        // 最后回退到 ISO-8859-1
        String(bytes, Charsets.ISO_8859_1)
    }
}
