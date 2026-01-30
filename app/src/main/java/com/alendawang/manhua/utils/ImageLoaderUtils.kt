package com.alendawang.manhua.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.Build
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.alendawang.manhua.model.ComicSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipInputStream

// --- 图片文件扩展名 ---
private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

// --- 判断是否为图片文件 ---
internal fun isImageFile(fileName: String): Boolean {
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
internal fun findLeafDirectoriesInArchive(entries: List<String>): List<String> {
    val dirsWithImages = entries
        .filter { !it.endsWith('/') && isImageFile(it) }
        .map { path ->
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash >= 0) path.substring(0, lastSlash + 1) else ""
        }
        .toSet()
    
    if (dirsWithImages.isEmpty() || (dirsWithImages.size == 1 && dirsWithImages.first() == "")) {
        return emptyList()
    }
    
    return dirsWithImages.filter { dir ->
        dirsWithImages.none { other ->
            other != dir && other.startsWith(dir) && other.length > dir.length
        }
    }.sortedWith { a, b -> compareNatural(a.trimEnd('/').substringAfterLast('/'), b.trimEnd('/').substringAfterLast('/')) }
}

// --- 检测 ZIP 的编码 (UTF-8 vs GBK) ---
internal fun getZipCharset(context: Context, zipUri: Uri): Charset {
    var charset = StandardCharsets.UTF_8
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.contains("")) {
                            charset = Charset.forName("GBK")
                            return charset
                        }
                        return charset
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            try {
                charset = Charset.forName("GBK")
            } catch (_: Exception) { }
        } catch (e: Exception) { }
    }
    return charset
}

// --- 获取 ZipInputStream (带编码处理) ---
internal fun getZipInputStream(context: Context, zipUri: Uri, charset: Charset): ZipInputStream? {
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
        val hashSource = zipUri.toString() + (internalPath ?: "")
        val hash = MessageDigest.getInstance("MD5")
            .digest(hashSource.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheDir = File(context.cacheDir, "comic_images/$hash")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val charset = getZipCharset(context, zipUri)

        val imageEntries = mutableListOf<String>()
        getZipInputStream(context, zipUri, charset)?.use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        if (internalPath == null || entry.name.startsWith(internalPath)) {
                            imageEntries.add(entry.name)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        
        imageEntries.sortWith { a, b -> 
            compareNatural(a.substringAfterLast('/'), b.substringAfterLast('/')) 
        }
        
        val nameToIndex = imageEntries.mapIndexed { index, name -> name to index }.toMap()
        
        val existingFiles = cacheDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val allExist = imageEntries.all { name ->
            val sortedIndex = nameToIndex[name] ?: 0
            val ext = name.substringAfterLast('.', "jpg")
            existingFiles.contains("${"%04d".format(sortedIndex)}.$ext")
        }
        
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
        
        imageEntries.mapIndexed { index, name ->
            val ext = name.substringAfterLast('.', "jpg")
            Uri.fromFile(File(cacheDir, "${"%04d".format(index)}.$ext"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// --- 计算ZIP/CBZ中的图片数量 ---
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
internal suspend fun collectZipEntries(context: Context, zipUri: Uri): List<String> = withContext(Dispatchers.IO) {
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
internal suspend fun collectRarEntries(context: Context, rarUri: Uri): List<String> = withContext(Dispatchers.IO) {
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

// --- 获取ZIP/CBZ的封面 ---
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
        
        val charset = getZipCharset(context, zipUri)
        
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

// --- 缓存压缩包文件到本地 (仅用于 RAR/CBR) ---
internal fun getCachedArchiveFile(context: Context, uri: Uri, ext: String): File? {
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

// --- 加载漫画图片 (统一入口) ---
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

// --- 加载文件夹中的图片 ---
private suspend fun loadImagesFromFolder(context: Context, folderUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
    val images = mutableListOf<Uri>()
    try {
        val docFile = DocumentFile.fromTreeUri(context, folderUri)
        docFile?.listFiles()?.forEach { file -> if (file.isFile && file.type?.startsWith("image/") == true) images.add(file.uri) }
        images.sortWith { u1, u2 -> compareNatural(u1.lastPathSegment ?: "", u2.lastPathSegment ?: "") }
    } catch (e: Exception) { e.printStackTrace() }
    images
}

// --- 从RAR/CBR加载图片 ---
suspend fun loadImagesFromRar(context: Context, rarUri: Uri, internalPath: String? = null): List<Uri> = withContext(Dispatchers.IO) {
    try {
        val cacheFile = getCachedArchiveFile(context, rarUri, "rar") ?: return@withContext emptyList()
        
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

// --- 计算RAR/CBR中的图片数量 ---
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

// --- 获取RAR/CBR的封面 ---
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

// --- 从PDF加载图片 ---
suspend fun loadImagesFromPdf(context: Context, pdfUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
    try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(pdfUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheDir = File(context.cacheDir, "comic_images/$hash")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val fd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext emptyList()
        val renderer = PdfRenderer(fd)
        
        val uris = (0 until renderer.pageCount).map { pageIndex ->
            val imageFile = File(cacheDir, "${"%04d".format(pageIndex)}.png")
            if (!imageFile.exists()) {
                val page = renderer.openPage(pageIndex)
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

// --- 计算PDF的页数 ---
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

// --- 获取PDF的封面 ---
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

// --- 计算文件夹中的图片数量 ---
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

// --- 统一计算漫画页数 ---
suspend fun countComicPages(context: Context, chapterUri: Uri, sourceType: ComicSourceType, internalPath: String? = null): Int {
    return when (sourceType) {
        ComicSourceType.ZIP -> countImagesInZip(context, chapterUri, internalPath)
        ComicSourceType.RAR -> countImagesInRar(context, chapterUri, internalPath)
        ComicSourceType.PDF -> countPagesInPdf(context, chapterUri)
        ComicSourceType.FOLDER -> countImagesInFolder(context, chapterUri)
    }
}
