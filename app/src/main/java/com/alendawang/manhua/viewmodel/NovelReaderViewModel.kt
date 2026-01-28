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
    var currentPageIndex by mutableIntStateOf(0)
    var chapterTitle by mutableStateOf("")
    var showMenu by mutableStateOf(false)
    var showSettings by mutableStateOf(false)

    private val paint = android.graphics.Paint()
    private val textBounds = android.graphics.Rect()

    // 初始化章节内容
    fun loadChapter(text: String, title: String) {
        fullText = text
        chapterTitle = title
        calculatePages()
    }

    // 计算分页（核心算法）
    fun calculatePages() {
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

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val availableWidth = screenWidth - (config.horizontalPadding * 2 * density).toInt()
        val lineHeight = (config.fontSize * config.lineHeightRatio * scaledDensity).toInt()
        val paragraphSpacing = (config.paragraphSpacing * scaledDensity).toInt()
        val maxPageHeight = screenHeight - (64 * density).toInt() // 减去底部和顶部栏空间

        val resultPages = mutableListOf<PageContent>()
        val paragraphs = fullText.split("\n")
        var currentPage = StringBuilder()
        var currentHeight = 0
        var startIndex = 0

        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                // 空段落，只添加段落间距
                if (currentHeight + paragraphSpacing > maxPageHeight) {
                    // 保存当前页
                    if (currentPage.isNotEmpty()) {
                        resultPages.add(PageContent(
                            startIndex = startIndex,
                            endIndex = startIndex + currentPage.length,
                            text = currentPage.toString().trimEnd(),
                            pageIndex = resultPages.size
                        ))
                        startIndex += currentPage.length
                        currentPage = StringBuilder()
                    }
                    currentHeight = 0
                }
                currentPage.append("\n")
                currentHeight += paragraphSpacing
                continue
            }

            // 处理段落分行
            val lines = mutableListOf<String>()
            var remainingText = paragraph

            while (remainingText.isNotEmpty()) {
                val breakIndex = paint.breakText(remainingText, true, availableWidth.toFloat(), null)
                if (breakIndex > 0) {
                    // 确保不会在字符中间断开（对于多字节字符）
                    var actualBreakIndex = breakIndex
                    if (remainingText.length > breakIndex) {
                        val charAfterBreak = remainingText.codePointAt(breakIndex)
                        if (Character.isSupplementaryCodePoint(charAfterBreak) || charAfterBreak > 0x7F) {
                            // 可能是多字节字符，向后查找安全断点
                            actualBreakIndex = findSafeBreakPoint(remainingText, breakIndex)
                        }
                    }
                    lines.add(remainingText.substring(0, actualBreakIndex))
                    remainingText = remainingText.substring(actualBreakIndex)
                } else {
                    lines.add(remainingText)
                    break
                }
            }

            val linesHeight = lines.size * lineHeight
            val paragraphTotalHeight = linesHeight + paragraphSpacing

            // 检查当前页是否能容纳这个段落
            if (currentHeight + paragraphTotalHeight > maxPageHeight) {
                // 保存当前页
                if (currentPage.isNotEmpty()) {
                    resultPages.add(PageContent(
                        startIndex = startIndex,
                        endIndex = startIndex + currentPage.length,
                        text = currentPage.toString().trimEnd(),
                        pageIndex = resultPages.size
                    ))
                    startIndex += currentPage.length
                    currentPage = StringBuilder()
                    currentHeight = 0
                }

                // 如果段落本身太长，需要跨页
                if (paragraphTotalHeight > maxPageHeight) {
                    var linesAdded = 0
                    for (line in lines) {
                        if (currentHeight + lineHeight > maxPageHeight) {
                            // 当前页已满
                            if (currentPage.isNotEmpty()) {
                                resultPages.add(PageContent(
                                    startIndex = startIndex,
                                    endIndex = startIndex + currentPage.length,
                                    text = currentPage.toString().trimEnd(),
                                    pageIndex = resultPages.size
                                ))
                                startIndex += currentPage.length
                                currentPage = StringBuilder()
                                currentHeight = 0
                            }
                        }
                        currentPage.append(line).append("\n")
                        currentHeight += lineHeight
                        linesAdded++
                    }
                    // 添加段落间距（除非是段落最后一行且已到新页）
                    if (linesAdded < lines.size || currentHeight + paragraphSpacing <= maxPageHeight) {
                        currentHeight += paragraphSpacing
                    }
                } else {
                    // 新的一页，直接添加段落
                    currentPage.append(paragraph).append("\n")
                    currentHeight = paragraphTotalHeight
                }
            } else {
                // 当前页可以容纳这个段落
                currentPage.append(paragraph).append("\n")
                currentHeight += paragraphTotalHeight
            }
        }

        // 添加最后一页
        if (currentPage.isNotEmpty()) {
            resultPages.add(PageContent(
                startIndex = startIndex,
                endIndex = startIndex + currentPage.length,
                text = currentPage.toString().trimEnd(),
                pageIndex = resultPages.size
            ))
        }

        pages = resultPages
        if (currentPageIndex >= pages.size) {
            currentPageIndex = maxOf(0, pages.size - 1)
        }
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
