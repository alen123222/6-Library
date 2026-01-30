package com.alendawang.manhua.utils

import android.content.Context
import android.net.Uri
import com.alendawang.manhua.model.NovelChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

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
internal fun detectEncoding(bytes: ByteArray): Charset {
    return try {
        val detector = org.mozilla.universalchardet.UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        
        val detectedCharset = detector.detectedCharset
        detector.reset()
        
        if (detectedCharset != null) {
            Charset.forName(detectedCharset)
        } else {
            StandardCharsets.UTF_8
        }
    } catch (e: Exception) {
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

// --- 查找同级文件 (用于查找歌词) ---
fun findSiblingLrcFile(context: Context, audioUri: Uri): String? {
    return try {
        // 1. 尝试直接文件路径 (适用于 file:// Uri)
        if (audioUri.scheme == "file") {
            val path = audioUri.path ?: return null
            val lrcPath = path.substringBeforeLast('.') + ".lrc"
            val file = java.io.File(lrcPath)
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
internal fun readLrcWithEncoding(inputStream: java.io.InputStream): String {
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
        if (!decoded.contains('\uFFFD')) decoded else null
    } catch (_: Exception) { null }
    
    if (utf8Result != null) return utf8Result
    
    // 尝试 GBK
    return try {
        String(bytes, charset("GBK"))
    } catch (_: Exception) {
        String(bytes, Charsets.ISO_8859_1)
    }
}
