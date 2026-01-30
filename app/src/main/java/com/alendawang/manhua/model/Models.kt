package com.alendawang.manhua.model

import androidx.compose.runtime.Immutable

// --- 媒体历史接口 ---
interface MediaHistory {
    val id: String
    val name: String
    val uriString: String
    val coverUriString: String?
    val timestamp: Long
    val isFavorite: Boolean
    val isNsfw: Boolean
}

// --- 漫画来源类型 ---
enum class ComicSourceType {
    FOLDER,  // 文件夹中的图片
    ZIP,     // ZIP/CBZ 压缩包
    RAR,     // RAR/CBR 压缩包
    PDF      // PDF 文件
}

// --- 漫画数据模型 ---
data class ComicChapter(
    val name: String,
    val uriString: String,
    val sourceType: ComicSourceType = ComicSourceType.FOLDER,
    val internalPath: String? = null  // Path within archive (e.g., "a1/a11/")
)

@Immutable
data class ComicHistory(
    override val id: String,
    override val name: String,
    override val uriString: String,
    override val coverUriString: String?,
    override val timestamp: Long,
    val lastReadIndex: Int = 0,
    val chapters: List<ComicChapter> = emptyList(),
    val lastReadChapterIndex: Int = 0,
    override val isFavorite: Boolean = false,
    override val isNsfw: Boolean = false,
    // 缓存的进度数据，避免每次渲染时 IO 读取
    val cachedTotalPages: Int = 0,
    val cachedCurrentPage: Int = 0,
    // 增量扫描：记录最后扫描时间戳
    val lastScannedAt: Long = 0
) : MediaHistory

// --- 小说数据模型 ---
data class NovelChapter(
    val name: String,
    val uriString: String,
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val isEpubChapter: Boolean = false,
    val htmlContent: String? = null,
    val internalPath: String? = null
)

@Immutable
data class NovelHistory(
    override val id: String,
    override val name: String,
    override val uriString: String,
    override val coverUriString: String?,
    override val timestamp: Long,
    val chapters: List<NovelChapter> = emptyList(),
    val lastReadChapterIndex: Int = 0,
    val lastReadScrollPosition: Int = 0,
    override val isFavorite: Boolean = false,
    override val isNsfw: Boolean = false
) : MediaHistory

// --- 音频数据模型 ---
data class AudioTrack(
    val name: String,
    val uriString: String,
    val duration: Long = 0,
    val isFavorite: Boolean = false
)

@Immutable
data class AudioHistory(
    override val id: String,
    override val name: String,
    override val uriString: String,
    override val coverUriString: String?,
    override val timestamp: Long,
    val tracks: List<AudioTrack> = emptyList(),
    val lastPlayedIndex: Int = 0,
    val lastPlayedPosition: Long = 0,
    override val isFavorite: Boolean = false,
    override val isNsfw: Boolean = false,
    val customBackgroundUriString: String? = null
) : MediaHistory
