@file:Suppress("UNCHECKED_CAST")
package com.alendawang.manhua.utils

import android.content.Context
import android.net.Uri
import com.alendawang.manhua.model.NovelChapter
import nl.siegmann.epublib.epub.EpubReader
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.SpineReference
import nl.siegmann.epublib.domain.TOCReference
import java.io.File
import java.security.MessageDigest

// --- EPUB解析 (使用 epublib 库) ---
fun parseEpubFile(context: Context, uri: Uri): Pair<List<NovelChapter>, String?>? {
    return try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }

        val book = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            EpubReader().readEpub(inputStream)
        } ?: return null

        // 提取封面
        val coverUriString = extractEpubCover(context, book, hash)

        // 构建章节列表
        val chapters = buildChapterList(book, uri.toString())

        if (chapters.isEmpty()) return null

        Pair(chapters, coverUriString)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 提取封面图片
private fun extractEpubCover(context: Context, book: Book, hash: String): String? {
    return try {
        val coverImage: Resource? = book.coverImage
        if (coverImage == null) return null
        val coverBytes: ByteArray = coverImage.data ?: return null

        val ext = coverImage.mediaType?.defaultExtension ?: "jpg"
        val coverCacheFile = File(context.cacheDir, "covers/${hash}_cover.$ext")

        if (!coverCacheFile.exists()) {
            coverCacheFile.parentFile?.mkdirs()
            coverCacheFile.writeBytes(coverBytes)
        }

        if (coverCacheFile.exists()) coverCacheFile.absolutePath else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 从TOC构建章节列表
private fun buildChapterList(book: Book, uriString: String): List<NovelChapter> {
    val toc = book.tableOfContents
    val tocRefs: List<TOCReference>? = toc?.tocReferences as? List<TOCReference>

    // 优先使用 TOC (目录)
    if (!tocRefs.isNullOrEmpty()) {
        val chapters = mutableListOf<NovelChapter>()
        flattenToc(tocRefs, chapters, uriString)
        if (chapters.isNotEmpty()) return chapters
    }

    // 如果没有TOC，使用 spine (阅读顺序)
    val spine = book.spine ?: return emptyList()
    val spineRefs: List<SpineReference> = spine.spineReferences as? List<SpineReference> ?: return emptyList()
    val chapters = mutableListOf<NovelChapter>()

    for ((i, ref) in spineRefs.withIndex()) {
        val resource: Resource = ref.resource ?: continue
        val href: String = resource.href ?: continue

        // 跳过封面页面 (通常第一个且包含 "cover")
        if (i == 0 && href.contains("cover", ignoreCase = true)) continue

        val title: String = (resource.title as? String)?.takeIf { it.isNotBlank() }
            ?: "第${chapters.size + 1}章"

        chapters.add(
            NovelChapter(
                name = title,
                uriString = uriString,
                startIndex = i,
                endIndex = i,
                isEpubChapter = true,
                htmlContent = null,
                internalPath = href
            )
        )
    }

    return chapters
}

// 递归展开 TOC 树结构
private fun flattenToc(
    refs: List<TOCReference>,
    result: MutableList<NovelChapter>,
    uriString: String
) {
    for (ref in refs) {
        val resource: Resource? = ref.resource
        val href: String = resource?.href ?: continue
        val title: String = (ref.title as? String)?.takeIf { it.isNotBlank() }
            ?: (resource.title as? String)?.takeIf { it.isNotBlank() }
            ?: "第${result.size + 1}章"

        // 去除锚点 fragment，只保留文件路径
        val cleanHref = href.substringBefore("#")

        // 避免重复添加同一个文件的不同锚点
        if (result.none { it.internalPath == cleanHref }) {
            result.add(
                NovelChapter(
                    name = title,
                    uriString = uriString,
                    startIndex = result.size,
                    endIndex = result.size,
                    isEpubChapter = true,
                    htmlContent = null,
                    internalPath = cleanHref
                )
            )
        }

        // 递归处理子目录
        val children: List<TOCReference>? = ref.children as? List<TOCReference>
        if (!children.isNullOrEmpty()) {
            flattenToc(children, result, uriString)
        }
    }
}

// --- EPUB章节内容加载 (使用 epublib) ---
fun getEpubChapterContent(context: Context, uri: Uri, internalPath: String): String {
    return try {
        val hash = MessageDigest.getInstance("MD5")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }

        val book = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            EpubReader().readEpub(inputStream)
        } ?: return ""

        // 查找目标章节资源
        val resource = findResource(book, internalPath) ?: return ""
        val rawData: ByteArray = resource.data ?: return ""
        var htmlContent = String(rawData, Charsets.UTF_8)

        // 处理章节中的图片引用
        val imgRegex = Regex("""<img[^>]+src="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
        val svgImgRegex = Regex("""xlink:href="([^"]+)"""", RegexOption.IGNORE_CASE)
        val allMatches = imgRegex.findAll(htmlContent) + svgImgRegex.findAll(htmlContent)

        val entryDir = internalPath.substringBeforeLast("/", "")

        for (match in allMatches) {
            val src = match.groupValues[1]
            if (src.startsWith("http") || src.startsWith("data:")) continue

            // 解析相对路径
            val fullPath = if (entryDir.isNotEmpty()) "$entryDir/$src" else src
            val normalizedPath = normalizePath(fullPath)

            // 从 epublib 中查找图片资源
            val imgResource = findResource(book, normalizedPath)
                ?: findResource(book, src) // 也尝试直接用 src
                ?: continue

            val imgData: ByteArray = imgResource.data ?: continue

            // 保存到缓存
            val imageHash = Integer.toHexString(normalizedPath.hashCode())
            val imgExt = imgResource.mediaType?.defaultExtension
                ?: src.substringAfterLast('.', "jpg")
            val imageCacheFile = File(context.cacheDir, "epub_images/${hash}_$imageHash.$imgExt")

            if (!imageCacheFile.exists()) {
                imageCacheFile.parentFile?.mkdirs()
                try {
                    imageCacheFile.writeBytes(imgData)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (imageCacheFile.exists()) {
                htmlContent = htmlContent.replace(
                    "\"$src\"",
                    "\"file://${imageCacheFile.absolutePath}\""
                )
            }
        }

        htmlContent
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

// 在 Book 中查找资源 (支持多种路径匹配)
private fun findResource(book: Book, href: String): Resource? {
    if (href.isBlank()) return null

    val normalized = normalizePath(href)
    val decoded = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (_: Exception) { href }

    // 遍历 spine 查找
    val spine = book.spine
    if (spine != null) {
        val spineRefs = spine.spineReferences as? List<*> ?: emptyList<Any>()
        for (item in spineRefs) {
            val ref = item as? SpineReference ?: continue
            val resource: Resource = ref.resource ?: continue
            val resHref: String = resource.href ?: continue
            if (matchesHref(resHref, href, normalized, decoded)) {
                return resource
            }
        }
    }

    // 遍历所有资源 (包括图片等非 spine 资源)
    try {
        val allResources = book.resources.getAll() as? Collection<*> ?: emptyList<Any>()
        for (item in allResources) {
            val resource = item as? Resource ?: continue
            val resHref: String = resource.href ?: continue
            if (matchesHref(resHref, href, normalized, decoded)) {
                return resource
            }
        }
    } catch (_: Exception) {}

    return null
}

// 检查 href 是否匹配
private fun matchesHref(resHref: String, originalHref: String, normalizedHref: String, decodedHref: String): Boolean {
    val normalizedRes = normalizePath(resHref)
    return normalizedRes == normalizedHref ||
            normalizedRes == normalizePath(decodedHref) ||
            resHref == originalHref ||
            normalizedRes.endsWith("/$normalizedHref") ||
            normalizedHref.endsWith("/$normalizedRes")
}

// 路径规范化 (处理 ".." 和 "./" 等相对路径)
internal fun normalizePath(path: String): String {
    val decoded = try {
        java.net.URLDecoder.decode(path, "UTF-8")
    } catch (_: Exception) { path }

    val parts = decoded.split("/").toMutableList()
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
