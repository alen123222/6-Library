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

    fun renderPage(
        pageContent: PageContent?,
        config: ReaderConfig,
        width: Int,
        height: Int,
        density: Float,
        scaledDensity: Float,
        bgColor: Int,
        bgBitmap: Bitmap? = null,
        overlayAlpha: Float = 0f,
        pageInfo: String = ""
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 绘制背景
        if (bgBitmap != null) {
            // 填满绘制背景纹理（按中心裁剪填充等比拉伸）
            val srcRect = android.graphics.Rect(0, 0, bgBitmap.width, bgBitmap.height)
            val destRect = android.graphics.Rect(0, 0, width, height)
            
            // 计算裁剪拉伸比例，模仿 ContentScale.Crop
            val scale: Float
            var dx = 0f
            var dy = 0f
            if (bgBitmap.width * height > width * bgBitmap.height) {
                scale = height.toFloat() / bgBitmap.height.toFloat()
                dx = (width - bgBitmap.width * scale) * 0.5f
            } else {
                scale = width.toFloat() / bgBitmap.width.toFloat()
                dy = (height - bgBitmap.height * scale) * 0.5f
            }
            
            val matrix = android.graphics.Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            canvas.drawBitmap(bgBitmap, matrix, null)
            
            // 绘制黑色遮罩（自定义图片背景常用以保证文字可读性）
            if (overlayAlpha > 0f) {
                val alphaInt = (overlayAlpha * 255).toInt().coerceIn(0, 255)
                canvas.drawColor(android.graphics.Color.argb(alphaInt, 0, 0, 0))
            }
        } else {
            canvas.drawColor(bgColor)
        }

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
        
        // 直接使用与 ViewModel 中一致的 StaticLayout 进行屏幕绘制级别的排版
        val textPaint = android.text.TextPaint(paint)
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.text.StaticLayout.Builder.obtain(pageContent.text, 0, pageContent.text.length, textPaint, availableWidth.toInt())
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.text.StaticLayout(pageContent.text, textPaint, availableWidth.toInt(), android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }

        // 通过计算 ViewModel 给出的确切行信息来绘制
        for (i in 0 until staticLayout.lineCount) {
            val lineStart = staticLayout.getLineStart(i)
            val lineEnd = staticLayout.getLineEnd(i)
            val lineText = pageContent.text.substring(lineStart, lineEnd)
            
            val isParagraphEnd = lineText.endsWith("\n")
            val pureText = if (isParagraphEnd) lineText.dropLast(1) else lineText

            if (pureText.isNotEmpty()) {
                canvas.drawText(pureText, paddingX, y, paint)
            }
            
            y += lineHeight
            if (isParagraphEnd) {
                y += paragraphSpacing
            }
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
}
