package com.alendawang.manhua.ui.reader

import android.graphics.Canvas

/**
 * 左右滑动翻页
 * 通过 Canvas translate 平移当前页和目标页 Bitmap
 */
class SlidePageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {

    override fun onAnimStart(animationSpeed: Int) {
        val distanceX: Float = when (mDirection) {
            PageDirection.NEXT -> {
                if (isCancel) {
                    var dis = viewWidth - startX + touchX
                    if (dis > viewWidth) dis = viewWidth.toFloat()
                    viewWidth - dis
                } else {
                    -(touchX + (viewWidth - startX))
                }
            }
            else -> {
                if (isCancel) {
                    -(touchX - startX)
                } else {
                    viewWidth - (touchX - startX)
                }
            }
        }
        startScroll(touchX.toInt(), 0, distanceX.toInt(), 0, animationSpeed)
    }

    override fun onDraw(canvas: Canvas) {
        val offsetX = touchX - startX
        if ((mDirection == PageDirection.NEXT && offsetX > 0)
            || (mDirection == PageDirection.PREV && offsetX < 0)
        ) return
        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth
        if (!isRunning) return

        if (mDirection == PageDirection.PREV) {
            prevBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                canvas.save()
                canvas.translate(distanceX, 0f)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
                }
            }
            curBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                canvas.save()
                canvas.translate(distanceX + viewWidth, 0f)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
                }
            }
        } else if (mDirection == PageDirection.NEXT) {
            curBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                canvas.save()
                canvas.translate(distanceX - viewWidth, 0f)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
                }
            }
            nextBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                canvas.save()
                canvas.translate(distanceX, 0f)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
                }
            }
        }
    }

    override fun onAnimStop() {
        if (!isCancel) {
            readView.fillPage(mDirection)
        }
    }
}
