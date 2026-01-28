package com.alendawang.manhua.utils

import android.content.Context
import android.content.res.Configuration
import android.os.BatteryManager
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- 横屏检测 (Composable) ---
/**
 * 检测当前屏幕是否处于横屏模式
 * 在 Composable 函数中调用，会在方向改变时自动重组
 * @return true 如果是横屏，false 如果是竖屏
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

/**
 * 检测当前屏幕是否处于横屏模式（非 Composable 版本）
 * 在非 Composable 上下文中使用，例如 ViewModel 或普通函数
 * @param context Android Context
 * @return true 如果是横屏，false 如果是竖屏
 */
fun isLandscape(context: Context): Boolean {
    return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

// --- 滚动到居中项 ---
suspend fun scrollToItemCentered(listState: LazyListState, index: Int) {
    if (index < 0) return
    val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
    val offset = (viewportHeight / 2).coerceAtLeast(0)
    listState.animateScrollToItem(index.coerceAtLeast(0), -offset)
}

// --- 自然排序比较器 ---
fun compareNatural(s1: String, s2: String): Int {
    val regex = "([0-9]+)|([^0-9]+)".toRegex()
    val p1 = regex.findAll(s1).map { it.value }.toList()
    val p2 = regex.findAll(s2).map { it.value }.toList()
    for (i in 0 until maxOf(p1.size, p2.size)) {
        val s1Part = p1.getOrNull(i) ?: return -1
        val s2Part = p2.getOrNull(i) ?: return 1
        val n1 = s1Part.toIntOrNull()
        val n2 = s2Part.toIntOrNull()
        if (n1 != null && n2 != null) {
            if (n1 != n2) return n1.compareTo(n2)
        } else {
            val cmp = s1Part.compareTo(s2Part, true)
            if (cmp != 0) return cmp
        }
    }
    return s1.length.compareTo(s2.length)
}

// --- 电池电量获取 ---
fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
}

// --- 当前时间获取 ---
fun getCurrentTime(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date())
}

// --- 时间格式化 ---
fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(
            Locale.getDefault(),
            "%d:%02d:%02d",
            hours,
            minutes % 60,
            seconds % 60
        )
    } else {
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes,
            seconds % 60
        )
    }
}

// --- 滚动进度打包/解包 ---
fun packScrollProgress(index: Int, offset: Int): Int {
    val safeIndex = index.coerceAtLeast(0)
    val safeOffset = offset.coerceIn(0, 0xFFFF)
    val packed = (safeIndex shl 16) or safeOffset
    return -packed - 1
}

fun isPackedProgress(value: Int): Boolean = value < 0

fun unpackScrollProgress(value: Int): Pair<Int, Int> {
    val packed = -value - 1
    val index = packed ushr 16
    val offset = packed and 0xFFFF
    return index to offset
}

// --- Spanned 内容分割 ---
fun splitSpanned(text: CharSequence, limit: Int = 3000): List<CharSequence> {
    val list = mutableListOf<CharSequence>()
    var start = 0
    val len = text.length
    while (start < len) {
        var end = (start + limit).coerceAtMost(len)
        if (end < len) {
            val nextNewline = text.indexOf('\n', end)
            if (nextNewline != -1 && nextNewline - start < limit * 1.2) {
                end = nextNewline + 1
            } else {
                val nextSpace = text.indexOf(' ', end)
                if (nextSpace != -1) end = nextSpace + 1
            }
        }
        list.add(text.subSequence(start, end))
        start = end
    }
    return list
}

// 在 CharSequence 中查找字符的扩展函数
private fun CharSequence.indexOf(char: Char, startIndex: Int): Int {
    for (i in startIndex until length) {
        if (this[i] == char) return i
    }
    return -1
}

// --- 旧版滚动位置转换 ---
fun estimateIndexFromLegacyPx(
    legacyPx: Int,
    paragraphs: List<String>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: androidx.compose.ui.text.TextStyle,
    widthPx: Int,
    paragraphSpacingPx: Int
): Pair<Int, Int> {
    if (legacyPx <= 0 || paragraphs.isEmpty()) return 0 to 0

    val sample = paragraphs.asSequence()
        .filter { it.isNotBlank() }
        .take(20)
        .toList()
    if (sample.isEmpty()) return 0 to legacyPx

    var totalHeight = 0
    val constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx)
    for (paragraph in sample) {
        val layout = textMeasurer.measure(
            text = paragraph,
            style = textStyle,
            constraints = constraints
        )
        totalHeight += layout.size.height + paragraphSpacingPx
    }

    val avgHeight = (totalHeight / sample.size).coerceAtLeast(1)
    val targetIndex = (legacyPx / avgHeight).coerceIn(0, paragraphs.lastIndex)
    val targetOffset = (legacyPx - (targetIndex * avgHeight)).coerceAtLeast(0)
    return targetIndex to targetOffset
}
