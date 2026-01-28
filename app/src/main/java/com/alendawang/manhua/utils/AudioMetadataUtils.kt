package com.alendawang.manhua.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverBitmap: Bitmap? = null
)

data class LyricLine(
    val startTime: Long,
    val text: String
)

object AudioMetadataUtils {

    suspend fun getAudioMetadata(context: Context, uri: Uri): AudioMetadata {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val coverBytes = retriever.embeddedPicture
                val coverBitmap = if (coverBytes != null) {
                    try {
                        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                AudioMetadata(title, artist, album, coverBitmap)
            } catch (e: Exception) {
                AudioMetadata()
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun findAndParseLyrics(context: Context, audioUri: Uri): List<LyricLine> {
        return withContext(Dispatchers.IO) {
            try {
                val lrcContent = readLrcFile(context, audioUri)
                if (lrcContent != null) {
                    parseLrc(lrcContent)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun readLrcFile(context: Context, audioUri: Uri): String? {
        // 先尝试 FileUtils 中的通用查找逻辑
        return findSiblingLrcFile(context, audioUri)
    }
}

fun parseLrc(lrcContent: String): List<LyricLine> {
    val lyrics = mutableListOf<LyricLine>()
    // 匹配 [mm:ss.xx] 或 [mm:ss:xx]
    val regex = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})](.*)")
    
    lrcContent.lines().forEach { line ->
        val matcher = regex.matcher(line)
        if (matcher.find()) {
            try {
                val min = matcher.group(1)?.toLong() ?: 0
                val sec = matcher.group(2)?.toLong() ?: 0
                val millsStr = matcher.group(3) ?: "00"
                //处理两位或三位毫秒
                val mills = if (millsStr.length == 2) {
                    millsStr.toLong() * 10
                } else {
                    millsStr.toLong()
                }
                
                val text = matcher.group(4)?.trim() ?: ""
                val time = min * 60000 + sec * 1000 + mills
                if (text.isNotEmpty()) {
                    lyrics.add(LyricLine(time, text))
                }
            } catch (_: Exception) { }
        }
    }
    return lyrics.sortedBy { it.startTime }
}
