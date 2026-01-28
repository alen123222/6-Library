package com.alendawang.manhua.navigation

import androidx.compose.runtime.saveable.Saver
import com.alendawang.manhua.model.MediaType

// --- 屏幕路由 ---
sealed class Screen {
    object Home : Screen()
    data class Details(val mediaType: MediaType, val mediaId: String, val highlightTrackIndex: Int? = null) : Screen()
    data class ComicReader(val comicId: String, val chapterIndex: Int, val initialScrollIndex: Int) : Screen()
    data class NovelReader(val novelId: String, val chapterIndex: Int, val initialScrollPosition: Int) : Screen()
    data class AudioPlayer(val audioId: String, val trackIndex: Int, val initialPosition: Long, val showLyricsInitially: Boolean = false) : Screen()
}

val ScreenSaver: Saver<Screen, List<Any>> = Saver(
    save = { screen ->
        when (screen) {
            Screen.Home -> listOf("home")
            is Screen.Details -> listOf("details", screen.mediaType.name, screen.mediaId, screen.highlightTrackIndex ?: -1)
            is Screen.ComicReader -> listOf("comic", screen.comicId, screen.chapterIndex, screen.initialScrollIndex)
            is Screen.NovelReader -> listOf("novel", screen.novelId, screen.chapterIndex, screen.initialScrollPosition)
            is Screen.AudioPlayer -> listOf("audio", screen.audioId, screen.trackIndex, screen.initialPosition, screen.showLyricsInitially)
        }
    },
    restore = { saved ->
        val type = saved.getOrNull(0) as? String ?: return@Saver Screen.Home
        when (type) {
            "details" -> {
                val mediaTypeName = saved.getOrNull(1) as? String
                val mediaType = mediaTypeName?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
                    ?: MediaType.COMIC
                val mediaId = saved.getOrNull(2) as? String ?: return@Saver Screen.Home
                val highlightTrackIndex = (saved.getOrNull(3) as? Number)?.toInt()?.takeIf { it >= 0 }
                Screen.Details(mediaType, mediaId, highlightTrackIndex)
            }
            "comic" -> {
                val comicId = saved.getOrNull(1) as? String ?: return@Saver Screen.Home
                val chapterIndex = (saved.getOrNull(2) as? Number)?.toInt() ?: 0
                val initialScrollIndex = (saved.getOrNull(3) as? Number)?.toInt() ?: 0
                Screen.ComicReader(comicId, chapterIndex, initialScrollIndex)
            }
            "novel" -> {
                val novelId = saved.getOrNull(1) as? String ?: return@Saver Screen.Home
                val chapterIndex = (saved.getOrNull(2) as? Number)?.toInt() ?: 0
                val initialScrollPosition = (saved.getOrNull(3) as? Number)?.toInt() ?: 0
                Screen.NovelReader(novelId, chapterIndex, initialScrollPosition)
            }
            "audio" -> {
                val audioId = saved.getOrNull(1) as? String ?: return@Saver Screen.Home
                val trackIndex = (saved.getOrNull(2) as? Number)?.toInt() ?: 0
                val initialPosition = (saved.getOrNull(3) as? Number)?.toLong() ?: 0L
                val showLyricsInitially = saved.getOrNull(4) as? Boolean ?: false
                Screen.AudioPlayer(audioId, trackIndex, initialPosition, showLyricsInitially)
            }
            else -> Screen.Home
        }
    }
)
