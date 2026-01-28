package com.alendawang.manhua.model

import androidx.compose.ui.graphics.Color

// --- 阅读器配置 ---
data class ReaderConfig(
    val fontSize: Float = 18f,
    val lineHeightRatio: Float = 1.6f,
    val paragraphSpacing: Float = 16f,
    val horizontalPadding: Float = 16f,
    val backgroundColor: ReaderBackgroundColor = ReaderBackgroundColor.EyeCare,
    val fontType: FontType = FontType.System,
    val customBackgroundUriString: String? = null,
    val customBackgroundOverlayAlpha: Float = 0.35f,
    val customTextColor: Int? = null
)

enum class ReaderBackgroundColor(val label: String, val color: Color, val textColor: Color) {
    EyeCare("羊皮纸", Color(0xFFC7EDCC), Color(0xFF2E4E3F)),
    Night("夜间模式", Color(0xFF1A1A1A), Color(0xFFB0B0B0)),
    PureWhite("纯白", Color(0xFFFFFFFF), Color(0xFF000000)),
    Gray("灰度", Color(0xFFE6E6E6), Color(0xFF333333)),
    Parchment("少女", Color(0xFFF5E6D3), Color(0xFF3E2723))
}

enum class FontType(val label: String) {
    System("系统字体"),
    Serif("宋体"),
    SansSerif("黑体"),
    Monospace("等宽")
}

// --- 分页数据 ---
data class PageContent(
    val startIndex: Int,
    val endIndex: Int,
    val text: String,
    val pageIndex: Int
)

// --- 音频播放器配置 ---
data class AudioPlayerConfig(
    val customBackgroundUriString: String? = null,
    val lyricsFontSize: Float = 18f,  // 播放页面歌词字体大小
    val lyricsColor: Int = 0xFFFFFFFF.toInt(),  // 播放页面歌词颜色，默认白色
    val overlayAlpha: Float = 0.6f,
    val playbackSpeed: Float = 1.0f,
    val floatingLyricsEnabled: Boolean = false,
    val floatingLyricColor: Int = 0xFF87CEEB.toInt(), // 默认天蓝色
    val floatingLyricTextSize: Float = 18f
)

// 悬浮歌词预设颜色
enum class FloatingLyricColor(val label: String, val colorInt: Int) {
    SkyBlue("天蓝", 0xFF87CEEB.toInt()),
    FluorescentGreen("荧光绿", 0xFF7FFF00.toInt()),
    LightYellow("淡黄", 0xFFFFFFE0.toInt()),
    LightPurple("淡紫", 0xFFDDA0DD.toInt()),
    Black("黑色", 0xFF000000.toInt())
}

// 播放页面歌词颜色（包含默认白色）
enum class LyricsColor(val label: String, val colorInt: Int) {
    White("白色", 0xFFFFFFFF.toInt()),
    SkyBlue("天蓝", 0xFF87CEEB.toInt()),
    FluorescentGreen("荧光绿", 0xFF7FFF00.toInt()),
    LightYellow("淡黄", 0xFFFFFFE0.toInt()),
    LightPurple("淡紫", 0xFFDDA0DD.toInt()),
    Black("黑色", 0xFF000000.toInt())
}
