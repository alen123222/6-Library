package com.alendawang.manhua.utils

import android.content.Context
import android.net.Uri
import com.alendawang.manhua.model.NovelChapter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

// --- EPUB解析 (流式读取，不缓存EPUB文件) ---
fun parseEpubFile(context: Context, uri: Uri): Pair<List<NovelChapter>, String?>? {
    return try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        var containerXml: String? = null
        var opfPath: String? = null
        var opfContent: String? = null
        var ncxContent: String? = null
        val entryContents = mutableMapOf<String, ByteArray>()
        
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
        
        if (containerXml != null) {
            val parsedOpfPath = parseContainerXml(containerXml!!)
            if (parsedOpfPath != null && parsedOpfPath != opfPath) {
                opfPath = parsedOpfPath
                opfContent = null
            }
        }
        
        if (opfPath == null) return null
        
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
        
        val titleMap = ncxContent?.let { parseNcx(it) }
        
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

internal fun normalizePath(path: String): String {
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
        val imagesToExtract = mutableListOf<Pair<String, String>>()
        
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
        
        if (imagesToExtract.isNotEmpty()) {
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
