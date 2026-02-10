package com.alendawang.manhua.data

import androidx.room.TypeConverter
import com.alendawang.manhua.model.AudioTrack
import com.alendawang.manhua.model.ComicChapter
import com.alendawang.manhua.model.NovelChapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromComicChapterList(value: List<ComicChapter>): String = gson.toJson(value)

    @TypeConverter
    fun toComicChapterList(value: String): List<ComicChapter> {
        val type = object : TypeToken<List<ComicChapter>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromNovelChapterList(value: List<NovelChapter>): String = gson.toJson(value)

    @TypeConverter
    fun toNovelChapterList(value: String): List<NovelChapter> {
        val type = object : TypeToken<List<NovelChapter>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromAudioTrackList(value: List<AudioTrack>): String = gson.toJson(value)

    @TypeConverter
    fun toAudioTrackList(value: String): List<AudioTrack> {
        val type = object : TypeToken<List<AudioTrack>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
