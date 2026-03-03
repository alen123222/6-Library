package com.alendawang.manhua.ui.reader

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.Scroller

/**
 * 翻页方向
 */
enum class PageDirection {
    NONE, PREV, NEXT
}

/**
 * 翻页策略基类
 * 参考 Legado 的 PageDelegate 设计
 */
abstract class PageDelegate(protected val pageView: PageView) {

    protected val startX: Float get() = pageView.startX
    protected val startY: Float get() = pageView.startY
    protected val lastX: Float get() = pageView.lastX
    protected val lastY: Float get() = pageView.lastY
    protected val touchX: Float get() = pageView.touchX
    protected val touchY: Float get() = pageView.touchY

    protected var viewWidth: Int = 0
    protected var viewHeight: Int = 0

    protected val scroller: Scroller by lazy {
        Scroller(pageView.getViewContext(), LinearInterpolator())
    }

    var isMoved = false
    var noNext = true
    var mDirection = PageDirection.NONE
    var isCancel = false
    var isRunning = false
    var isStarted = false

    protected fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, animationSpeed: Int) {
        val duration = if (dx != 0) {
            (animationSpeed * kotlin.math.abs(dx)) / viewWidth.coerceAtLeast(1)
        } else {
            (animationSpeed * kotlin.math.abs(dy)) / viewHeight.coerceAtLeast(1)
        }
        scroller.startScroll(startX, startY, dx, dy, duration)
        isRunning = true
        isStarted = true
        pageView.invalidate()
    }

    protected fun stopScroll() {
        isStarted = false
        pageView.post {
            isMoved = false
            isRunning = false
            pageView.invalidate()
        }
    }

    open fun setViewSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    open fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            pageView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    open fun onScroll() = Unit

    abstract fun abortAnim()
    abstract fun onAnimStart(animationSpeed: Int)
    abstract fun onDraw(canvas: Canvas)
    abstract fun onAnimStop()
    abstract fun nextPageByAnim(animationSpeed: Int)
    abstract fun prevPageByAnim(animationSpeed: Int)

    open fun setDirection(direction: PageDirection) {
        mDirection = direction
    }

    abstract fun onTouch(event: MotionEvent)

    fun onDown() {
        isMoved = false
        noNext = false
        isRunning = false
        isCancel = false
        setDirection(PageDirection.NONE)
    }

    fun hasPrev(): Boolean = pageView.hasPrevPage()
    fun hasNext(): Boolean = pageView.hasNextPage()

    open fun onDestroy() {}
}

