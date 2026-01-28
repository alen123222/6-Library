package com.alendawang.manhua.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// --- 媒体类型枚举 ---
enum class MediaType(val label: String, val icon: ImageVector) {
    COMIC("漫画", Icons.Rounded.MenuBook),
    NOVEL("小说", Icons.Rounded.Menu),
    AUDIO("音频", Icons.Rounded.Headphones);

    companion object {
        fun fromIndex(index: Int): MediaType = values().getOrNull(index) ?: COMIC
    }
}

// --- 显示模式枚举 ---
enum class DisplayMode(val columnCount: Int, val label: String, val icon: ImageVector) {
    ListView(1, "列表视图", Icons.Rounded.ViewList),
    Grid3(3, "三列视图", Icons.Rounded.GridView),
    Grid4(4, "四列视图", Icons.Rounded.Grid4x4),
    Grid5(5, "密集视图", Icons.Rounded.Apps);

    companion object {
        fun fromInt(value: Int): DisplayMode = values().firstOrNull { it.columnCount == value } ?: Grid3
    }
}

// --- 排序模式枚举 ---
enum class SortOption(val label: String) {
    TimeDesc("最近阅读"),
    TimeAsc("最早阅读"),
    NameAsc("名称 (A-Z)"),
    NameDesc("名称 (Z-A)");

    companion object {
        fun fromInt(value: Int): SortOption = values().getOrNull(value) ?: TimeDesc
    }
}

// --- 音频显示模式枚举 ---
enum class AudioDisplayMode(val label: String, val icon: ImageVector) {
    SINGLES("纯单曲", Icons.Rounded.MusicNote),    // 所有歌曲平铺显示
    ALBUMS("专辑模式", Icons.Rounded.Album);       // 专辑显示为卡片

    companion object {
        fun fromInt(value: Int): AudioDisplayMode = values().getOrNull(value) ?: ALBUMS
    }
}

// --- 主题配置 ---
enum class AppTheme(val label: String, val primaryColor: Color) {
    Sakura("落樱", Color(0xFFFF80AB)),
    Cyberpunk("暗黑", Color(0xFF00E5FF)),
    InkStyle("水墨", Color(0xFF263238)),
    Matcha("抹茶", Color(0xFF66BB6A));
}

fun AppTheme.next(): AppTheme = when (this) {
    AppTheme.Sakura -> AppTheme.Cyberpunk
    AppTheme.Cyberpunk -> AppTheme.InkStyle
    AppTheme.InkStyle -> AppTheme.Matcha
    AppTheme.Matcha -> AppTheme.Sakura
}

// --- 应用语言 ---
enum class AppLanguage(val code: String) {
    CHINESE("zh"),
    ENGLISH("en");
    
    fun getLabel(currentLanguage: AppLanguage): String = when (currentLanguage) {
        CHINESE -> when (this) {
            CHINESE -> "中文"
            ENGLISH -> "English"
        }
        ENGLISH -> when (this) {
            CHINESE -> "中文"
            ENGLISH -> "English"
        }
    }
    
    companion object {
        fun fromCode(code: String?): AppLanguage = when (code) {
            "zh" -> CHINESE
            "en" -> ENGLISH
            else -> CHINESE // 默认中文
        }
        
        /**
         * 根据系统语言获取默认应用语言
         */
        fun fromSystemLocale(): AppLanguage {
            val systemLanguage = java.util.Locale.getDefault().language
            return when {
                systemLanguage.startsWith("zh") -> CHINESE
                systemLanguage.startsWith("en") -> ENGLISH
                else -> ENGLISH // 非中英文系统默认英文
            }
        }
    }
}

// --- 扫描状态 ---
data class ScanState(
    val isScanning: Boolean = false,
    val currentFolder: String = "",
    val totalFound: Int = 0
)
