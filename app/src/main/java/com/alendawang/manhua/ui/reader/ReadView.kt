package com.alendawang.manhua.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.alendawang.manhua.model.PageContent
import com.alendawang.manhua.model.PageFlipMode
import com.alendawang.manhua.model.ReaderConfig
import kotlin.math.abs

/**
 * 阅读主视图
 * 使用三页缓冲 + 策略模式翻页
 */
class ReadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = null
            field = value
        }

    val defaultAnimationSpeed = 300
    val pageSlopSquare: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    // 触摸点
    var startX = 0f
    var startY = 0f
    var lastX = 0f
    var lastY = 0f
    var touchX = 0f
    var touchY = 0f

    // 状态
    private var pressDown = false
    private var isMove = false
    private val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    // 页面数据
    private var prevPageContent: PageContent? = null
    private var curPageContent: PageContent? = null
    private var nextPageContent: PageContent? = null

    // 渲染缓存
    private var cachedPrevBitmap: Bitmap? = null
    private var cachedCurBitmap: Bitmap? = null
    private var cachedNextBitmap: Bitmap? = null
    private var bitmapsDirty = true

    // 配置
    private var config: ReaderConfig = ReaderConfig()
    var bgColor: Int = 0xFFC7EDCC.toInt()
        private set
    private var pageInfo: String = ""

    // 回调
    var onPageChangeListener: OnPageChangeListener? = null
    var onTapCenterListener: (() -> Unit)? = null

    interface OnPageChangeListener {
        fun onPageChanged(direction: PageDirection)
        fun hasNextPage(): Boolean
        fun hasPrevPage(): Boolean
    }

    init {
        setWillNotDraw(false)
    }

    fun setReaderConfig(newConfig: ReaderConfig) {
        config = newConfig
        bitmapsDirty = true
        updatePageDelegate()
        invalidate()
    }

    fun setPageData(
        prev: PageContent?,
        cur: PageContent?,
        next: PageContent?,
        info: String = ""
    ) {
        prevPageContent = prev
        curPageContent = cur
        nextPageContent = next
        pageInfo = info
        bitmapsDirty = true
        invalidate()
    }

    fun setBgColor(color: Int) {
        bgColor = color
        bitmapsDirty = true
    }

    private fun updatePageDelegate() {
        val newDelegate = when (config.pageFlipMode) {
            PageFlipMode.SLIDE -> SlidePageDelegate(this)
            PageFlipMode.SIMULATION -> SimulationPageDelegate(this)
            PageFlipMode.SCROLL -> null // scroll mode is handled by Compose LazyColumn
        }
        pageDelegate = newDelegate
        newDelegate?.setViewSize(width, height)
    }

    private fun ensureBitmaps() {
        if (!bitmapsDirty || width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity

        // 不要 recycle 旧的 Bitmap！PageDelegate 可能仍持有引用
        // 旧 Bitmap 会在失去所有引用后被 GC 自然回收

        // 强制使用 TRANSPARENT 渲染 Bitmap 背景，以便底层 Compose Background (纹理/自定义图片) 能透出来
        val transparent = android.graphics.Color.TRANSPARENT
        cachedPrevBitmap = PageRenderer.renderPage(
            prevPageContent, config, width, height, density, scaledDensity, transparent, ""
        )
        cachedCurBitmap = PageRenderer.renderPage(
            curPageContent, config, width, height, density, scaledDensity, transparent, pageInfo
        )
        cachedNextBitmap = PageRenderer.renderPage(
            nextPageContent, config, width, height, density, scaledDensity, transparent, ""
        )
        bitmapsDirty = false
    }

    fun getPrevBitmap(): Bitmap? {
        ensureBitmaps()
        return cachedPrevBitmap
    }

    fun getCurBitmap(): Bitmap? {
        ensureBitmaps()
        return cachedCurBitmap
    }

    fun getNextBitmap(): Bitmap? {
        ensureBitmaps()
        return cachedNextBitmap
    }

    fun hasNextPage(): Boolean = onPageChangeListener?.hasNextPage() ?: false
    fun hasPrevPage(): Boolean = onPageChangeListener?.hasPrevPage() ?: false

    fun fillPage(direction: PageDirection) {
        // 先停止动画状态，避免 delegate 在下一帧继续用旧 bitmap 绘制
        pageDelegate?.let {
            it.isRunning = false
            it.isStarted = false
            it.isMoved = false
        }
        onPageChangeListener?.onPageChanged(direction)
    }

    fun setStartPoint(x: Float, y: Float, invalidateView: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y
        if (invalidateView) invalidate()
    }

    fun setTouchPoint(x: Float, y: Float, invalidateView: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidateView) invalidate()
        pageDelegate?.onScroll()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmapsDirty = true
        pageDelegate?.setViewSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensureBitmaps()

        // 如果翻页动画正在运行，由 delegate 绘制
        if (pageDelegate?.isRunning == true) {
            pageDelegate?.onDraw(canvas)
        } else {
            // 否则直接显示当前页（含 isRecycled 安全检查）
            cachedCurBitmap?.let {
                if (!it.isRecycled) canvas.drawBitmap(it, 0f, 0f, null)
            }
        }
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageDelegate == null) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressDown = true
                isMove = false
                pageDelegate?.onTouch(event)
                pageDelegate?.onDown()
                setStartPoint(event.x, event.y, false)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pressDown) return true
                val absX = abs(startX - event.x)
                val absY = abs(startY - event.y)
                if (!isMove) {
                    isMove = absX > slopSquare || absY > slopSquare
                }
                if (isMove) {
                    pageDelegate?.onTouch(event)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!pressDown) return true
                pressDown = false
                if (pageDelegate?.isMoved != true && !isMove) {
                    // 单击处理
                    val screenWidth = width
                    when {
                        startX < screenWidth / 3f -> {
                            // 左侧点击 - 上一页
                            pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
                        }
                        startX > screenWidth * 2f / 3f -> {
                            // 右侧点击 - 下一页
                            pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
                        }
                        else -> {
                            // 中间点击 - 显示/隐藏菜单
                            onTapCenterListener?.invoke()
                        }
                    }
                    return true
                }
                if (pageDelegate?.isMoved == true) {
                    pageDelegate?.onTouch(event)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!pressDown) return true
                pressDown = false
                if (pageDelegate?.isMoved == true) {
                    pageDelegate?.onTouch(event)
                }
            }
        }
        return true
    }

    fun onDestroy() {
        pageDelegate?.onDestroy()
        cachedPrevBitmap?.recycle()
        cachedCurBitmap?.recycle()
        cachedNextBitmap?.recycle()
        cachedPrevBitmap = null
        cachedCurBitmap = null
        cachedNextBitmap = null
    }
}
