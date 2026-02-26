package com.alendawang.manhua.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.alendawang.manhua.model.FontType
import com.alendawang.manhua.model.PageContent
import com.alendawang.manhua.model.ReaderConfig

/**
 * 页面渲染器
 * 将 PageContent 渲染为 Bitmap，用于翻页动画
 */
object PageRenderer {

    /**
     * 将一页文本内容渲染为 Bitmap
     */
    fun renderPage(
        pageContent: PageContent?,
        config: ReaderConfig,
        width: Int,
        height: Int,
        density: Float,
        scaledDensity: Float,
        bgColor: Int,
        pageInfo: String = ""
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 绘制背景
        canvas.drawColor(bgColor)

        if (pageContent == null) return bitmap

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = config.fontSize * scaledDensity
            color = config.customTextColor
                ?: config.backgroundColor.textColor.let {
                    android.graphics.Color.argb(
                        (it.alpha * 255).toInt(),
                        (it.red * 255).toInt(),
                        (it.green * 255).toInt(),
                        (it.blue * 255).toInt()
                    )
                }
            typeface = when (config.fontType) {
                FontType.Serif -> Typeface.SERIF
                FontType.SansSerif -> Typeface.SANS_SERIF
                FontType.Monospace -> Typeface.MONOSPACE
                FontType.System -> Typeface.DEFAULT
            }
        }

        val paddingX = config.horizontalPadding * density
        val paddingY = 32 * density
        val availableWidth = width - paddingX * 2
        val lineHeight = config.fontSize * config.lineHeightRatio * scaledDensity
        val paragraphSpacing = config.paragraphSpacing * scaledDensity

        val fontMetrics = paint.fontMetrics
        var y = paddingY - fontMetrics.ascent
        val lines = pageContent.text.split("\n")

        for (line in lines) {
            if (line.isEmpty()) {
                y += paragraphSpacing
                continue
            }
            val words = splitTextToLines(line, paint, availableWidth)
            for (word in words) {
                if (y + fontMetrics.descent > height - paddingY) break
                canvas.drawText(word, paddingX, y, paint)
                y += lineHeight
            }
            y += paragraphSpacing
        }

        // 绘制页码信息
        if (pageInfo.isNotEmpty()) {
            val infoPaint = Paint().apply {
                isAntiAlias = true
                textSize = 12 * scaledDensity
                color = paint.color
                alpha = 128
            }
            val infoWidth = infoPaint.measureText(pageInfo)
            canvas.drawText(
                pageInfo,
                (width - infoWidth) / 2,
                height - 12 * density,
                infoPaint
            )
        }

        return bitmap
    }

    private fun splitTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val breakIndex = paint.breakText(remaining, true, maxWidth, null)
            if (breakIndex > 0) {
                lines.add(remaining.substring(0, breakIndex))
                remaining = remaining.substring(breakIndex)
            } else {
                lines.add(remaining)
                break
            }
        }
        return lines
    }
}
