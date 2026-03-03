package com.alendawang.manhua.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.alendawang.manhua.model.FontType
import com.alendawang.manhua.model.PageContent
import com.alendawang.manhua.model.ReaderConfig
import com.alendawang.manhua.utils.loadReaderConfig
import com.alendawang.manhua.utils.saveReaderConfig

// --- 小说阅读器 ViewModel (MVVM) ---
class NovelReaderViewModel(
    private val context: Context,
    private val onProgressSave: (Int, Int) -> Unit
) {
    var config by mutableStateOf(loadReaderConfig(context))
    var fullText by mutableStateOf("")
    var pages by mutableStateOf<List<PageContent>>(emptyList())
    var nextChapterFirstPage by mutableStateOf<PageContent?>(null)
    var prevChapterLastPage by mutableStateOf<PageContent?>(null)
    var currentPageIndex by mutableIntStateOf(0)
    var chapterTitle by mutableStateOf("")
    var showMenu by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var isJumpingToPreviousChapter by mutableStateOf(false)

    private val paint = android.graphics.Paint()
    private val textBounds = android.graphics.Rect()
    
    // 存储与屏幕画布绑定的真实宽高（规避系统状态栏、刘海遮挡导致的屏幕像素错觉）
    var viewWidth: Int = 0
    var viewHeight: Int = 0

    // 初始化章节内容
    fun loadChapter(text: String, title: String, startFromLastPage: Boolean = false) {
        fullText = text
        chapterTitle = title
        pages = calculatePagesFor(fullText)
        currentPageIndex = if (startFromLastPage) maxOf(0, pages.size - 1) else 0
    }

    // 预加载相邻章节的首尾页（用于无缝翻页）
    fun prefetchAdjacentChapters(prevText: String?, nextText: String?) {
        prevChapterLastPage = prevText?.let { calculatePagesFor(it).lastOrNull() }
        nextChapterFirstPage = nextText?.let { calculatePagesFor(it).firstOrNull() }
    }

    // 计算分页（更新当前状态）
    fun calculatePages() {
        pages = calculatePagesFor(fullText)
        if (currentPageIndex >= pages.size) {
            currentPageIndex = maxOf(0, pages.size - 1)
        }
    }

    // 纯计算：为给定文本计算分页列表
    fun calculatePagesFor(text: String): List<PageContent> {
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        paint.textSize = config.fontSize * scaledDensity
        paint.color = config.backgroundColor.textColor.toArgb()
        paint.typeface = when (config.fontType) {
            FontType.Serif -> android.graphics.Typeface.SERIF
            FontType.SansSerif -> android.graphics.Typeface.SANS_SERIF
            FontType.Monospace -> android.graphics.Typeface.MONOSPACE
            FontType.System -> android.graphics.Typeface.DEFAULT
        }

        val width = if (viewWidth > 0) viewWidth else context.resources.displayMetrics.widthPixels
        val height = if (viewHeight > 0) viewHeight else context.resources.displayMetrics.heightPixels
        val availableWidth = (width - config.horizontalPadding * 2 * density).toInt()
        val lineHeight = (config.fontSize * config.lineHeightRatio * scaledDensity).toInt()
        val paragraphSpacing = (config.paragraphSpacing * scaledDensity).toInt()
        val maxPageHeight = height - (64 * density).toInt() // 减去底部和顶部栏空间（32dp * 2）

        val resultPages = mutableListOf<PageContent>()
        if (text.isEmpty()) return resultPages
        
        // 全局一次性测量整章文本
        val textPaint = android.text.TextPaint(paint)
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, availableWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.text.StaticLayout(text, textPaint, availableWidth, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }

        var currentHeight = 0
        var pageStartOffset = 0

        for (i in 0 until staticLayout.lineCount) {
            val lineStart = staticLayout.getLineStart(i)
            val lineEnd = staticLayout.getLineEnd(i)
            val lineText = text.substring(lineStart, lineEnd)

            var addedHeight = lineHeight
            // 原生 StaticLayout 会保留字串里的换行符，以此判断是否为真实段落末尾
            val isParagraphEnd = lineText.endsWith("\n")
            if (isParagraphEnd) {
                addedHeight += paragraphSpacing
            }

            // 如果本行加上后超过了一页能容纳的最大高度，进行强制分页
            if (currentHeight + addedHeight > maxPageHeight && currentHeight > 0) {
                resultPages.add(PageContent(
                    startIndex = pageStartOffset,
                    endIndex = lineStart,
                    text = text.substring(pageStartOffset, lineStart),
                    pageIndex = resultPages.size
                ))
                pageStartOffset = lineStart
                currentHeight = 0
            }
            
            currentHeight += addedHeight
        }

        // 收集最后一页内容
        if (pageStartOffset < text.length) {
            resultPages.add(PageContent(
                startIndex = pageStartOffset,
                endIndex = text.length,
                text = text.substring(pageStartOffset, text.length),
                pageIndex = resultPages.size
            ))
        }

        return resultPages
    }

    // 找到安全的断点，避免在多字节字符中间断开
    private fun findSafeBreakPoint(text: String, breakIndex: Int): Int {
        if (breakIndex >= text.length) return breakIndex

        var safePoint = breakIndex
        while (safePoint > 0 && !Character.isLowSurrogate(text[safePoint]) && !Character.isHighSurrogate(text[safePoint])) {
            safePoint--
        }
        if (safePoint > 0) {
            return safePoint
        }
        return breakIndex
    }

    fun nextPage(): Boolean {
        if (currentPageIndex < pages.size - 1) {
            currentPageIndex++
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        if (currentPageIndex > 0) {
            currentPageIndex--
            return true
        }
        return false
    }

    fun jumpToPage(index: Int) {
        currentPageIndex = index.coerceIn(0, pages.size - 1)
    }

    fun updateConfig(newConfig: ReaderConfig) {
        config = newConfig
        saveReaderConfig(context, newConfig)
    }

    fun getCurrentProgress(): Float {
        return if (pages.isEmpty()) 0f else (currentPageIndex + 1).toFloat() / pages.size
    }

    fun getProgressText(): String {
        return "${currentPageIndex + 1} / ${pages.size}"
    }
}
