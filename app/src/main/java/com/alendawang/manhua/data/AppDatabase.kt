package com.alendawang.manhua.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ComicHistoryEntity::class, NovelHistoryEntity::class, AudioHistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicHistoryDao(): ComicHistoryDao
    abstract fun novelHistoryDao(): NovelHistoryDao
    abstract fun audioHistoryDao(): AudioHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manhua_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
