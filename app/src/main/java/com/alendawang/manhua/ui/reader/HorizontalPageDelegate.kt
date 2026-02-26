package com.alendawang.manhua.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent

/**
 * 水平翻页基类
 * 被 SlidePageDelegate 和 SimulationPageDelegate 继承
 */
abstract class HorizontalPageDelegate(readView: ReadView) : PageDelegate(readView) {

    protected var curBitmap: Bitmap? = null
    protected var prevBitmap: Bitmap? = null
    protected var nextBitmap: Bitmap? = null

    private val slopSquare: Int get() = readView.pageSlopSquare

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        setBitmap()
    }

    open fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> {
                prevBitmap = readView.getPrevBitmap()
                curBitmap = readView.getCurBitmap()
            }
            PageDirection.NEXT -> {
                nextBitmap = readView.getNextBitmap()
                curBitmap = readView.getCurBitmap()
            }
            else -> Unit
        }
    }

    override fun onTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
            }
            MotionEvent.ACTION_MOVE -> {
                onScrollEvent(event)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                onAnimStart(readView.defaultAnimationSpeed)
            }
        }
    }

    private fun onScrollEvent(event: MotionEvent) {
        val action = event.action
        val pointerUp = action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) event.actionIndex else -1
        var sumX = 0f
        var sumY = 0f
        val count = event.pointerCount
        for (i in 0 until count) {
            if (skipIndex == i) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        val div = if (pointerUp) count - 1 else count
        val focusX = sumX / div
        val focusY = sumY / div

        if (!isMoved) {
            val deltaX = (focusX - startX).toInt()
            val deltaY = (focusY - startY).toInt()
            val distance = deltaX * deltaX + deltaY * deltaY
            isMoved = distance > slopSquare * slopSquare
            if (isMoved) {
                if (sumX - startX > 0) {
                    if (!hasPrev()) {
                        noNext = true
                        return
                    }
                    setDirection(PageDirection.PREV)
                } else {
                    if (!hasNext()) {
                        noNext = true
                        return
                    }
                    setDirection(PageDirection.NEXT)
                }
                readView.setStartPoint(event.x, event.y, false)
            }
        }
        if (isMoved) {
            isCancel = if (mDirection == PageDirection.NEXT) sumX > lastX else sumX < lastX
            isRunning = true
            readView.setTouchPoint(sumX, sumY)
        }
    }

    override fun abortAnim() {
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            scroller.abortAnimation()
            if (!isCancel) {
                readView.fillPage(mDirection)
                readView.invalidate()
            }
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        abortAnim()
        if (!hasNext()) return
        setDirection(PageDirection.NEXT)
        val y = if (startY > viewHeight / 2) viewHeight.toFloat() * 0.9f else 1f
        readView.setStartPoint(viewWidth.toFloat() * 0.9f, y, false)
        onAnimStart(animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        abortAnim()
        if (!hasPrev()) return
        setDirection(PageDirection.PREV)
        readView.setStartPoint(0f, viewHeight.toFloat(), false)
        onAnimStart(animationSpeed)
    }

    override fun onDestroy() {
        super.onDestroy()
        curBitmap = null
        prevBitmap = null
        nextBitmap = null
    }
}
