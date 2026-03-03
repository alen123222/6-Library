package com.alendawang.manhua.ui.reader

import android.content.Context
import android.graphics.Bitmap

/**
 * 翻页视图接口
 * ReadView（小说） 和 ComicReadView（漫画） 都实现此接口，
 * 使 PageDelegate 体系可以统一操作
 */
interface PageView {
    fun getViewContext(): Context
    val pageSlopSquare: Int
    val defaultAnimationSpeed: Int
    var isAbortAnim: Boolean

    var startX: Float
    var startY: Float
    var lastX: Float
    var lastY: Float
    var touchX: Float
    var touchY: Float

    fun getPrevBitmap(): Bitmap?
    fun getCurBitmap(): Bitmap?
    fun getNextBitmap(): Bitmap?

    fun hasNextPage(): Boolean
    fun hasPrevPage(): Boolean
    fun fillPage(direction: PageDirection)

    fun setStartPoint(x: Float, y: Float, invalidateView: Boolean = true)
    fun setTouchPoint(x: Float, y: Float, invalidateView: Boolean = true)

    fun invalidate()
    fun post(action: Runnable): Boolean

    fun getViewWidth(): Int
    fun getViewHeight(): Int
}
