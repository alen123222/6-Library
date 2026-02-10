package com.alendawang.manhua.data

import android.content.Context
import com.alendawang.manhua.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaRepository — 封装 Room DAO 操作，并处理 Entity ↔ Model 转换
 * 首次启动时自动从 SharedPreferences 迁移历史数据至 Room
 */
class MediaRepository(private val db: AppDatabase) {

    private val gson = Gson()
    private val comicDao get() = db.comicHistoryDao()
    private val novelDao get() = db.novelHistoryDao()
    private val audioDao get() = db.audioHistoryDao()

    // ============================================================
    // SharedPreferences → Room 一次性迁移
    // ============================================================

    suspend fun migrateFromSharedPreferencesIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("comic_history_prefs", Context.MODE_PRIVATE)
        val migrationDone = prefs.getBoolean("room_migration_done", false)
        if (migrationDone) return@withContext

        // 迁移漫画数据
        val comicJson = prefs.getString("history_list_v2", null)
        if (comicJson != null) {
            try {
                val list: List<ComicHistory> = gson.fromJson(comicJson, object : TypeToken<List<ComicHistory>>() {}.type)
                comicDao.insertOrUpdateAll(list.map { it.toEntity() })
            } catch (_: Exception) {}
        }

        // 迁移小说数据
        val novelJson = prefs.getString("novel_history_list", null)
        if (novelJson != null) {
            try {
                val list: List<NovelHistory> = gson.fromJson(novelJson, object : TypeToken<List<NovelHistory>>() {}.type)
                novelDao.insertOrUpdateAll(list.map { it.toEntity() })
            } catch (_: Exception) {}
        }

        // 迁移音频数据
        val audioJson = prefs.getString("audio_history_list", null)
        if (audioJson != null) {
            try {
                val list: List<AudioHistory> = gson.fromJson(audioJson, object : TypeToken<List<AudioHistory>>() {}.type)
                audioDao.insertOrUpdateAll(list.map { it.toEntity() })
            } catch (_: Exception) {}
        }

        // 标记迁移完成，清理旧数据
        prefs.edit()
            .putBoolean("room_migration_done", true)
            .remove("history_list_v2")
            .remove("novel_history_list")
            .remove("audio_history_list")
            .apply()
    }

    // ============================================================
    // 漫画操作
    // ============================================================

    suspend fun loadAllComics(): List<ComicHistory> = withContext(Dispatchers.IO) {
        comicDao.getAll().map { it.toModel() }
    }

    suspend fun saveComic(comic: ComicHistory) = withContext(Dispatchers.IO) {
        comicDao.insertOrUpdate(comic.toEntity())
    }

    suspend fun saveAllComics(list: List<ComicHistory>) = withContext(Dispatchers.IO) {
        comicDao.insertOrUpdateAll(list.map { it.toEntity() })
    }

    suspend fun deleteComic(id: String) = withContext(Dispatchers.IO) {
        comicDao.deleteById(id)
    }

    suspend fun deleteComicsByIds(ids: List<String>) = withContext(Dispatchers.IO) {
        comicDao.deleteByIds(ids)
    }

    suspend fun clearAllComics() = withContext(Dispatchers.IO) {
        comicDao.deleteAll()
    }

    // ============================================================
    // 小说操作
    // ============================================================

    suspend fun loadAllNovels(): List<NovelHistory> = withContext(Dispatchers.IO) {
        novelDao.getAll().map { it.toModel() }
    }

    suspend fun saveAllNovels(list: List<NovelHistory>) = withContext(Dispatchers.IO) {
        novelDao.insertOrUpdateAll(list.map { it.toEntity() })
    }

    suspend fun clearAllNovels() = withContext(Dispatchers.IO) {
        novelDao.deleteAll()
    }

    // ============================================================
    // 音频操作
    // ============================================================

    suspend fun loadAllAudio(): List<AudioHistory> = withContext(Dispatchers.IO) {
        audioDao.getAll().map { it.toModel() }
    }

    suspend fun saveAllAudio(list: List<AudioHistory>) = withContext(Dispatchers.IO) {
        audioDao.insertOrUpdateAll(list.map { it.toEntity() })
    }

    suspend fun clearAllAudio() = withContext(Dispatchers.IO) {
        audioDao.deleteAll()
    }

    // ============================================================
    // Entity ↔ Model 转换
    // ============================================================

    private fun ComicHistory.toEntity() = ComicHistoryEntity(
        id = id, name = name, uriString = uriString,
        coverUriString = coverUriString, timestamp = timestamp,
        lastReadIndex = lastReadIndex, chaptersJson = gson.toJson(chapters),
        lastReadChapterIndex = lastReadChapterIndex,
        isFavorite = isFavorite, isNsfw = isNsfw,
        cachedTotalPages = cachedTotalPages, cachedCurrentPage = cachedCurrentPage,
        lastScannedAt = lastScannedAt
    )

    private fun ComicHistoryEntity.toModel() = ComicHistory(
        id = id, name = name, uriString = uriString,
        coverUriString = coverUriString, timestamp = timestamp,
        lastReadIndex = lastReadIndex,
        chapters = gson.fromJson(chaptersJson, object : TypeToken<List<ComicChapter>>() {}.type) ?: emptyList(),
        lastReadChapterIndex = lastReadChapterIndex,
        isFavorite = isFavorite, isNsfw = isNsfw,
        cachedTotalPages = cachedTotalPages, cachedCurrentPage = cachedCurrentPage,
        lastScannedAt = lastScannedAt
    )

    private fun NovelHistory.toEntity() = NovelHistoryEntity(
        id = id, name = name, uriString = uriString,
        coverUriString = coverUriString, timestamp = timestamp,
        chaptersJson = gson.toJson(chapters),
        lastReadChapterIndex = lastReadChapterIndex,
        lastReadScrollPosition = lastReadScrollPosition,
        isFavorite = isFavorite, isNsfw = isNsfw
    )

    private fun NovelHistoryEntity.toModel() = NovelHistory(
        id = id, name = name, uriString = uriString,
        coverUriString = coverUriString, timestamp = timestamp,
        chapters = gson.fromJson(chaptersJson, object : TypeToken<List<NovelChapter>>() {}.type) ?: emptyList(),
        lastReadChapterIndex = lastReadChapterIndex,
        lastReadScrollPosition = lastReadScrollPosition,
        isFavorite = isFavorite, isNsfw = isNsfw
    )

    private fun AudioHistory.toEntity() = AudioHistoryEntity(
        id = id, name = name, uriString = uriString,
        coverUriString = coverUriString, timestamp = timestamp,
        tracksJson = gson.toJson(tracks),
        lastPlayedIndex = lastPlayedIndex, lastPlayedPosition = lastPlayedPosition,
        isFavorite = isFavorite, isNsfw = isNsfw,
        customBackgroundUriString = customBackgroundUriString
    )

    private fun AudioHistoryEntity.toModel() = AudioHistory(
        id = id, name = name, uriString = uriString,
        coverUriString = coverUriString, timestamp = timestamp,
        tracks = gson.fromJson(tracksJson, object : TypeToken<List<AudioTrack>>() {}.type) ?: emptyList(),
        lastPlayedIndex = lastPlayedIndex, lastPlayedPosition = lastPlayedPosition,
        isFavorite = isFavorite, isNsfw = isNsfw,
        customBackgroundUriString = customBackgroundUriString
    )
}
