package com.alendawang.manhua.data

import androidx.room.*

@Dao
interface ComicHistoryDao {
    @Query("SELECT * FROM comic_history")
    suspend fun getAll(): List<ComicHistoryEntity>

    @Query("SELECT * FROM comic_history WHERE id = :id")
    suspend fun getById(id: String): ComicHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ComicHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<ComicHistoryEntity>)

    @Query("DELETE FROM comic_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM comic_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM comic_history")
    suspend fun deleteAll()
}

@Dao
interface NovelHistoryDao {
    @Query("SELECT * FROM novel_history")
    suspend fun getAll(): List<NovelHistoryEntity>

    @Query("SELECT * FROM novel_history WHERE id = :id")
    suspend fun getById(id: String): NovelHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: NovelHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<NovelHistoryEntity>)

    @Query("DELETE FROM novel_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM novel_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM novel_history")
    suspend fun deleteAll()
}

@Dao
interface AudioHistoryDao {
    @Query("SELECT * FROM audio_history")
    suspend fun getAll(): List<AudioHistoryEntity>

    @Query("SELECT * FROM audio_history WHERE id = :id")
    suspend fun getById(id: String): AudioHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AudioHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<AudioHistoryEntity>)

    @Query("DELETE FROM audio_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM audio_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM audio_history")
    suspend fun deleteAll()
}
