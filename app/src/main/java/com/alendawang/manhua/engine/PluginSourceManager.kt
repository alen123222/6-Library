package com.alendawang.manhua.engine

import android.content.Context
import android.content.SharedPreferences
import com.alendawang.manhua.model.plugin.LegadoSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PluginSourceManager {
    private const val PREFS_NAME = "plugin_sources_prefs"
    private const val KEY_SOURCES = "sources_list"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSources(context: Context): List<LegadoSource> {
        val json = getPrefs(context).getString(KEY_SOURCES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LegadoSource>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveSources(context: Context, sources: List<LegadoSource>) {
        val json = Gson().toJson(sources)
        getPrefs(context).edit().putString(KEY_SOURCES, json).apply()
    }

    /**
     * 从网络 URL 导入 JSON 字符串
     */
    suspend fun importFromUrl(context: Context, url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("网络请求失败: ${response.code}"))
            }
            val jsonStr = response.body?.string() ?: return@withContext Result.failure(Exception("返回内容为空"))
            return@withContext importSources(context, jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    /**
     * 导入 JSON 字符串（支持单个对象或数组）
     */
    fun importSources(context: Context, jsonStr: String): Result<Int> {
        return try {
            val gson = Gson()
            val newSources = mutableListOf<LegadoSource>()

            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                val type = object : TypeToken<List<LegadoSource>>() {}.type
                val list: List<LegadoSource> = gson.fromJson(trimmed, type)
                newSources.addAll(list)
            } else if (trimmed.startsWith("{")) {
                val source = gson.fromJson(trimmed, LegadoSource::class.java)
                newSources.add(source)
            } else {
                return Result.failure(Exception("无法识别的 JSON 格式"))
            }

            if (newSources.isEmpty()) {
                return Result.failure(Exception("未找到任何有效书源"))
            }

            // 合并并去重 (以 URL 作为主键)
            val existing = getSources(context).toMutableList()
            var addedCount = 0
            for (source in newSources) {
                if (source.bookSourceUrl.isBlank()) continue
                // 如果已存在，则替换；否则添加
                val index = existing.indexOfFirst { it.bookSourceUrl == source.bookSourceUrl }
                if (index >= 0) {
                    existing[index] = source
                } else {
                    existing.add(source)
                }
                addedCount++
            }

            saveSources(context, existing)
            Result.success(addedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun deleteSource(context: Context, sourceUrl: String) {
        val existing = getSources(context).toMutableList()
        existing.removeAll { it.bookSourceUrl == sourceUrl }
        saveSources(context, existing)
    }

    fun deleteSources(context: Context, sourceUrls: Set<String>) {
        val existing = getSources(context).toMutableList()
        existing.removeAll { it.bookSourceUrl in sourceUrls }
        saveSources(context, existing)
    }
}
