package com.alendawang.manhua.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

// ============================================================
// 漫画历史 Entity
// ============================================================
@Entity(tableName = "comic_history")
@TypeConverters(Converters::class)
data class ComicHistoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val coverUriString: String?,
    val timestamp: Long,
    val lastReadIndex: Int = 0,
    val chaptersJson: String = "[]",  // List<ComicChapter> as JSON
    val lastReadChapterIndex: Int = 0,
    val isFavorite: Boolean = false,
    val isNsfw: Boolean = false,
    val cachedTotalPages: Int = 0,
    val cachedCurrentPage: Int = 0,
    val lastScannedAt: Long = 0
)

// ============================================================
// 小说历史 Entity
// ============================================================
@Entity(tableName = "novel_history")
@TypeConverters(Converters::class)
data class NovelHistoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val coverUriString: String?,
    val timestamp: Long,
    val chaptersJson: String = "[]",  // List<NovelChapter> as JSON
    val lastReadChapterIndex: Int = 0,
    val lastReadScrollPosition: Int = 0,
    val isFavorite: Boolean = false,
    val isNsfw: Boolean = false
)

// ============================================================
// 音频历史 Entity
// ============================================================
@Entity(tableName = "audio_history")
@TypeConverters(Converters::class)
data class AudioHistoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val coverUriString: String?,
    val timestamp: Long,
    val tracksJson: String = "[]",  // List<AudioTrack> as JSON
    val lastPlayedIndex: Int = 0,
    val lastPlayedPosition: Long = 0,
    val isFavorite: Boolean = false,
    val isNsfw: Boolean = false,
    val customBackgroundUriString: String? = null
)
