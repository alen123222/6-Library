package com.alendawang.manhua.utils

import android.content.Context
import androidx.core.content.edit
import com.alendawang.manhua.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.Collator
import java.util.Locale

// --- 常量定义 ---
private const val PREFS_NAME = "comic_history_prefs"
private const val KEY_HISTORY = "history_list_v2"
private const val KEY_NOVEL_HISTORY = "novel_history_list"
private const val KEY_AUDIO_HISTORY = "audio_history_list"
private const val KEY_DISPLAY_MODE = "display_mode"
private const val KEY_COMIC_DISPLAY_MODE = "comic_display_mode"
private const val KEY_NOVEL_DISPLAY_MODE = "novel_display_mode"
private const val KEY_SORT_OPTION = "sort_option"
private const val KEY_THEME = "app_theme"
private const val KEY_PASSWORD = "app_nsfw_password"
private const val KEY_DETAIL_OVERLAY_ALPHA = "detail_overlay_alpha"
private const val KEY_HOME_BACKGROUND = "home_background_uri"
private const val KEY_HOME_BACKGROUND_ALPHA = "home_background_alpha"
private const val KEY_LAST_MEDIA_TYPE = "last_media_type"
private const val KEY_APP_LANGUAGE = "app_language"
private const val KEY_LANGUAGE_INITIALIZED = "language_initialized"
private const val KEY_UNLOCK_METHOD = "unlock_method"

private const val PREFS_READER_CONFIG = "reader_config_prefs"
private const val KEY_READER_CONFIG = "reader_config_v1"

private const val PREFS_AUDIO_PLAYER_CONFIG = "audio_player_config_prefs"
private const val KEY_AUDIO_PLAYER_CONFIG = "audio_player_config_v1"

// --- 阅读器配置存储 ---
fun saveReaderConfig(context: Context, config: ReaderConfig) {
    val configJson = Gson().toJson(config)
    context.getSharedPreferences(PREFS_READER_CONFIG, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_READER_CONFIG, configJson)
        .apply()
}

fun loadReaderConfig(context: Context): ReaderConfig {
    val json = context.getSharedPreferences(PREFS_READER_CONFIG, Context.MODE_PRIVATE)
        .getString(KEY_READER_CONFIG, null)
    return if (json != null) {
        try {
            Gson().fromJson(json, ReaderConfig::class.java)
        } catch (e: Exception) {
            ReaderConfig()
        }
    } else {
        ReaderConfig()
    }
}

// --- 音频播放器配置存储 ---
fun saveAudioPlayerConfig(context: Context, config: AudioPlayerConfig) {
    val configJson = Gson().toJson(config)
    context.getSharedPreferences(PREFS_AUDIO_PLAYER_CONFIG, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_AUDIO_PLAYER_CONFIG, configJson)
        .apply()
}

fun loadAudioPlayerConfig(context: Context): AudioPlayerConfig {
    val json = context.getSharedPreferences(PREFS_AUDIO_PLAYER_CONFIG, Context.MODE_PRIVATE)
        .getString(KEY_AUDIO_PLAYER_CONFIG, null)
    return if (json != null) {
        try {
            Gson().fromJson(json, AudioPlayerConfig::class.java)
        } catch (e: Exception) {
            AudioPlayerConfig()
        }
    } else {
        AudioPlayerConfig()
    }
}

// --- 过滤函数 ---
fun <T : MediaHistory> filterHistory(
    list: List<T>,
    isHiddenModeUnlocked: Boolean,
    searchQuery: String
): List<T> {
    val filteredByMode = if (isHiddenModeUnlocked) {
        list.filter { it.isNsfw }
    } else {
        list.filter { !it.isNsfw }
    }
    return if (searchQuery.isNotBlank()) {
        filteredByMode.filter { it.name.contains(searchQuery, ignoreCase = true) }
    } else {
        filteredByMode
    }
}

// --- 音频过滤函数（支持歌曲名搜索）---
/**
 * Filter audio history with track-level search.
 * Returns matching albums and a map of album ID -> list of matching track indices.
 */
fun filterAudioHistoryWithTracks(
    list: List<AudioHistory>,
    isHiddenModeUnlocked: Boolean,
    searchQuery: String
): Pair<List<AudioHistory>, Map<String, List<Int>>> {
    val filteredByMode = if (isHiddenModeUnlocked) {
        list.filter { it.isNsfw }
    } else {
        list.filter { !it.isNsfw }
    }
    
    if (searchQuery.isBlank()) {
        return Pair(filteredByMode, emptyMap())
    }
    
    val matchingAlbums = mutableListOf<AudioHistory>()
    val matchingTracksMap = mutableMapOf<String, List<Int>>()
    
    for (audio in filteredByMode) {
        val albumMatches = audio.name.contains(searchQuery, ignoreCase = true)
        val matchingTrackIndices = audio.tracks.mapIndexedNotNull { index, track ->
            if (track.name.contains(searchQuery, ignoreCase = true)) index else null
        }
        
        if (albumMatches || matchingTrackIndices.isNotEmpty()) {
            matchingAlbums.add(audio)
            if (matchingTrackIndices.isNotEmpty()) {
                matchingTracksMap[audio.id] = matchingTrackIndices
            }
        }
    }
    
    return Pair(matchingAlbums, matchingTracksMap)
}

// --- 多语言排序器 ---
private val englishCollator: Collator = Collator.getInstance(Locale.ENGLISH)
private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA)
private val japaneseCollator: Collator = Collator.getInstance(Locale.JAPAN)

/**
 * 检测字符串的主要语言类型
 * 通过分析首个非空白、非数字、非标点字符来判断
 */
private fun detectLanguage(text: String): Locale {
    for (char in text) {
        when {
            // 跳过空白、数字和常见标点
            char.isWhitespace() || char.isDigit() || char in ".,!?;:\"'()[]{}-_=+@#$%^&*~`/\\|<>" -> continue
            // 日语假名（平假名和片假名）
            char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' -> return Locale.JAPAN
            // 中文汉字（CJK统一汉字）
            char in '\u4E00'..'\u9FFF' -> return Locale.CHINA
            // 日语特有汉字扩展区（少见，但为完整性保留）
            char in '\u3400'..'\u4DBF' -> return Locale.CHINA
            // 拉丁字母（英语及其他西方语言）
            char in 'A'..'Z' || char in 'a'..'z' -> return Locale.ENGLISH
        }
    }
    // 默认使用英语排序
    return Locale.ENGLISH
}

/**
 * 获取对应语言的排序器
 */
private fun getCollatorForLocale(locale: Locale): Collator = when (locale) {
    Locale.JAPAN -> japaneseCollator
    Locale.CHINA -> chineseCollator
    else -> englishCollator
}

/**
 * 智能比较两个字符串，根据各自语言使用对应排序规则
 * 比较时：先按语言分组（英语 < 中文 < 日语），同语言内按对应规则排序
 */
private fun smartCompare(a: String, b: String): Int {
    val localeA = detectLanguage(a)
    val localeB = detectLanguage(b)
    
    // 如果语言相同，使用对应的排序器
    if (localeA == localeB) {
        return getCollatorForLocale(localeA).compare(a, b)
    }
    
    // 语言不同时，按语言优先级排序：英语 -> 中文 -> 日语
    val languageOrder = mapOf(Locale.ENGLISH to 0, Locale.CHINA to 1, Locale.JAPAN to 2)
    return (languageOrder[localeA] ?: 0) - (languageOrder[localeB] ?: 0)
}

// --- 漫画排序函数 ---
fun applySort(list: List<ComicHistory>, option: SortOption): List<ComicHistory> {
    val optionComparator: Comparator<ComicHistory> = when (option) {
        SortOption.TimeDesc -> compareByDescending { it.timestamp }
        SortOption.TimeAsc -> compareBy { it.timestamp }
        SortOption.NameAsc -> Comparator { a, b -> smartCompare(a.name, b.name) }
        SortOption.NameDesc -> Comparator { a, b -> smartCompare(b.name, a.name) }
    }
    return list.sortedWith(compareByDescending<ComicHistory> { it.isFavorite }.then(optionComparator))
}

fun applyNovelSort(list: List<NovelHistory>, option: SortOption): List<NovelHistory> {
    val optionComparator: Comparator<NovelHistory> = when (option) {
        SortOption.TimeDesc -> compareByDescending { it.timestamp }
        SortOption.TimeAsc -> compareBy { it.timestamp }
        SortOption.NameAsc -> Comparator { a, b -> smartCompare(a.name, b.name) }
        SortOption.NameDesc -> Comparator { a, b -> smartCompare(b.name, a.name) }
    }
    return list.sortedWith(compareByDescending<NovelHistory> { it.isFavorite }.then(optionComparator))
}

fun applyAudioSort(list: List<AudioHistory>, option: SortOption): List<AudioHistory> {
    val optionComparator: Comparator<AudioHistory> = when (option) {
        SortOption.TimeDesc -> compareByDescending { it.timestamp }
        SortOption.TimeAsc -> compareBy { it.timestamp }
        SortOption.NameAsc -> Comparator { a, b -> smartCompare(a.name, b.name) }
        SortOption.NameDesc -> Comparator { a, b -> smartCompare(b.name, a.name) }
    }
    return list.sortedWith(compareByDescending<AudioHistory> { it.isFavorite }.then(optionComparator))
}

// --- 漫画历史管理 ---
fun updateComicHistoryAndSort(context: Context, currentList: List<ComicHistory>, newItem: ComicHistory, sortOption: SortOption): List<ComicHistory> {
    val updatedList = currentList.toMutableList()
    val index = updatedList.indexOfFirst { it.id == newItem.id }
    if (index != -1) {
        val oldItem = updatedList[index]
        updatedList[index] = newItem.copy(isNsfw = if (newItem.timestamp == oldItem.timestamp) newItem.isNsfw else (if (newItem.isNsfw) true else oldItem.isNsfw))
    } else {
        updatedList.add(newItem)
    }
    val sortedList = applySort(updatedList, sortOption)
    saveComicToPrefs(context, sortedList)
    return sortedList
}

fun loadComicHistory(context: Context): List<ComicHistory> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
    return try { Gson().fromJson(json, object : TypeToken<List<ComicHistory>>() {}.type) } catch (e: Exception) { emptyList() }
}

fun deleteComicHistory(context: Context, currentList: List<ComicHistory>, id: String): List<ComicHistory> {
    val updatedList = currentList.filter { it.id != id }
    saveComicToPrefs(context, updatedList)
    return updatedList
}

fun saveComicReaderProgress(context: Context, currentList: List<ComicHistory>, comicId: String?, chapterIndex: Int, scrollIndex: Int): List<ComicHistory>? {
    if (comicId == null) return null
    val item = currentList.find { it.id == comicId } ?: return null
    
    // 计算当前阅读页数（基于缓存的总页数和章节信息）
    val cachedCurrentPage = if (item.chapters.isNotEmpty() && item.cachedTotalPages > 0) {
        // 简化计算：按比例估算当前页
        val chaptersCount = item.chapters.size
        val pagesPerChapter = item.cachedTotalPages / chaptersCount
        val pagesBeforeChapter = chapterIndex * pagesPerChapter
        (pagesBeforeChapter + scrollIndex + 1).coerceAtMost(item.cachedTotalPages)
    } else {
        scrollIndex + 1
    }
    
    val newItem = item.copy(
        lastReadChapterIndex = chapterIndex, 
        lastReadIndex = scrollIndex, 
        timestamp = System.currentTimeMillis(),
        cachedCurrentPage = cachedCurrentPage
    )
    val updatedList = currentList.toMutableList()
    val index = updatedList.indexOfFirst { it.id == comicId }
    if (index != -1) updatedList[index] = newItem
    saveComicToPrefs(context, updatedList)
    return updatedList
}

fun saveComicToPrefs(context: Context, list: List<ComicHistory>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_HISTORY, Gson().toJson(list)) }
}

fun clearComicHistory(context: Context): List<ComicHistory> {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { remove(KEY_HISTORY) }
    return emptyList()
}

// --- 小说历史管理 ---
fun loadNovelHistory(context: Context): List<NovelHistory> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_NOVEL_HISTORY, null) ?: return emptyList()
    return try { Gson().fromJson(json, object : TypeToken<List<NovelHistory>>() {}.type) } catch (e: Exception) { emptyList() }
}

fun saveNovelToPrefs(context: Context, list: List<NovelHistory>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_NOVEL_HISTORY, Gson().toJson(list)) }
}

// --- 音频历史管理 ---
fun loadAudioHistory(context: Context): List<AudioHistory> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_AUDIO_HISTORY, null) ?: return emptyList()
    return try { Gson().fromJson(json, object : TypeToken<List<AudioHistory>>() {}.type) } catch (e: Exception) { emptyList() }
}

fun saveAudioToPrefs(context: Context, list: List<AudioHistory>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_AUDIO_HISTORY, Gson().toJson(list)) }
}

// --- 显示模式 ---
fun loadDisplayMode(context: Context): DisplayMode {
    val modeInt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DISPLAY_MODE, 3)
    return DisplayMode.fromInt(modeInt)
}

fun saveDisplayMode(context: Context, mode: DisplayMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_DISPLAY_MODE, mode.columnCount) }
}

// --- 按模块类型的显示模式 ---
fun loadDisplayModeForType(context: Context, mediaType: MediaType): DisplayMode {
    val key = when (mediaType) {
        MediaType.COMIC -> KEY_COMIC_DISPLAY_MODE
        MediaType.NOVEL -> KEY_NOVEL_DISPLAY_MODE
        MediaType.AUDIO -> KEY_DISPLAY_MODE // 音频使用原有的 key
    }
    // 默认值：漫画根据屏幕大小自动选择，小说和音频为列表视图
    val defaultMode = when (mediaType) {
        MediaType.COMIC -> getDefaultComicDisplayMode(context)
        MediaType.NOVEL -> 1  // ListView
        MediaType.AUDIO -> 1  // ListView
    }
    val modeInt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(key, defaultMode)
    return DisplayMode.fromInt(modeInt)
}

/**
 * 根据屏幕尺寸获取漫画的默认显示模式
 * - 平板 (最小宽度 ≥ 600dp): 密集视图 (5列)
 * - 手机 (最小宽度 < 600dp): 三列视图 (3列)
 */
private fun getDefaultComicDisplayMode(context: Context): Int {
    val configuration = context.resources.configuration
    val smallestWidthDp = configuration.smallestScreenWidthDp
    return if (smallestWidthDp >= 600) {
        5  // Grid5 - 密集视图 for tablets
    } else {
        3  // Grid3 - 三列视图 for phones
    }
}

fun saveDisplayModeForType(context: Context, mediaType: MediaType, mode: DisplayMode) {
    val key = when (mediaType) {
        MediaType.COMIC -> KEY_COMIC_DISPLAY_MODE
        MediaType.NOVEL -> KEY_NOVEL_DISPLAY_MODE
        MediaType.AUDIO -> KEY_DISPLAY_MODE
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(key, mode.columnCount) }
}

// --- 排序选项 ---
fun loadSortOption(context: Context): SortOption {
    val sortInt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SORT_OPTION, 0)
    return SortOption.fromInt(sortInt)
}

fun saveSortOption(context: Context, option: SortOption) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_SORT_OPTION, option.ordinal) }
}

// --- 音频显示模式 ---
private const val KEY_AUDIO_DISPLAY_MODE = "audio_display_mode"

fun loadAudioDisplayMode(context: Context): AudioDisplayMode {
    val modeInt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_AUDIO_DISPLAY_MODE, 1) // 默认 ALBUMS
    return AudioDisplayMode.fromInt(modeInt)
}

fun saveAudioDisplayMode(context: Context, mode: AudioDisplayMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_AUDIO_DISPLAY_MODE, mode.ordinal) }
}

// --- 主题 ---
fun loadTheme(context: Context): AppTheme {
    val themeName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, AppTheme.InkStyle.name)
    return try { AppTheme.valueOf(themeName ?: AppTheme.InkStyle.name) } catch (_: Exception) { AppTheme.InkStyle }
}

fun saveTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_THEME, theme.name) }
}

// --- 上次使用的媒体类型 ---
fun loadLastMediaType(context: Context): MediaType {
    val typeName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_MEDIA_TYPE, MediaType.COMIC.name)
    return try { MediaType.valueOf(typeName ?: MediaType.COMIC.name) } catch (_: Exception) { MediaType.COMIC }
}

fun saveLastMediaType(context: Context, mediaType: MediaType) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_LAST_MEDIA_TYPE, mediaType.name) }
}
fun loadDetailOverlayAlpha(context: Context): Float {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getFloat(KEY_DETAIL_OVERLAY_ALPHA, 0.15f)
}

fun saveDetailOverlayAlpha(context: Context, alpha: Float) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit { putFloat(KEY_DETAIL_OVERLAY_ALPHA, alpha) }
}

// --- 主页背景 ---
fun loadHomeBackgroundUri(context: Context): String? {
    val uri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_HOME_BACKGROUND, null)
    return if (uri.isNullOrEmpty()) null else uri
}

fun saveHomeBackgroundUri(context: Context, uri: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_HOME_BACKGROUND, uri) }
}

fun loadHomeBackgroundAlpha(context: Context): Float {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_HOME_BACKGROUND_ALPHA, 0.4f)
}

fun saveHomeBackgroundAlpha(context: Context, alpha: Float) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_HOME_BACKGROUND_ALPHA, alpha) }
}

// --- 密码管理 ---
fun hasPassword(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_PASSWORD)
}

fun savePassword(context: Context, password: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_PASSWORD, password) }
}

fun checkPassword(context: Context, input: String): Boolean {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PASSWORD, "")
    return saved == input
}

// --- 应用语言 ---
/**
 * 加载应用语言
 * 首次启动时会根据系统语言自动设置
 */
fun loadAppLanguage(context: Context): AppLanguage {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val isInitialized = prefs.getBoolean(KEY_LANGUAGE_INITIALIZED, false)
    
    return if (!isInitialized) {
        // 首次启动，根据系统语言设置
        val systemLanguage = AppLanguage.fromSystemLocale()
        saveAppLanguage(context, systemLanguage)
        systemLanguage
    } else {
        // 已初始化，读取保存的语言设置
        val languageCode = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.CHINESE.code)
        AppLanguage.fromCode(languageCode)
    }
}

fun saveAppLanguage(context: Context, language: AppLanguage) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(KEY_APP_LANGUAGE, language.code)
        putBoolean(KEY_LANGUAGE_INITIALIZED, true)
    }
}

// --- 解锁方式管理 ---
enum class UnlockMethod {
    PASSWORD,
    FINGERPRINT;
    
    companion object {
        fun fromString(value: String?): UnlockMethod {
            return when (value) {
                "FINGERPRINT" -> FINGERPRINT
                else -> PASSWORD
            }
        }
    }
}

/**
 * 检查用户是否已选择过默认解锁方式
 */
fun hasChosenUnlockMethod(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .contains(KEY_UNLOCK_METHOD)
}

/**
 * 保存用户选择的解锁方式
 */
fun saveUnlockMethod(context: Context, method: UnlockMethod) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(KEY_UNLOCK_METHOD, method.name)
    }
}

/**
 * 加载用户选择的解锁方式
 * 默认返回 PASSWORD
 */
fun loadUnlockMethod(context: Context): UnlockMethod {
    val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_UNLOCK_METHOD, null)
    return UnlockMethod.fromString(value)
}
