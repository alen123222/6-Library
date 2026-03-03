package com.alendawang.manhua.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.alendawang.manhua.model.ComicReadMode
import kotlin.math.abs

/**
 * 漫画翻页视图
 * 复用 PageDelegate 体系（SlidePageDelegate / SimulationPageDelegate）
 * 外部直接提供三页 Bitmap，无需 PageRenderer
 */
class ComicReadView(context: Context) : View(context), PageView {

    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = null
            field = value
        }

    override fun getViewContext(): android.content.Context = context
    override val defaultAnimationSpeed = 150
    override val pageSlopSquare: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    // 触摸点
    override var startX = 0f
    override var startY = 0f
    override var lastX = 0f
    override var lastY = 0f
    override var touchX = 0f
    override var touchY = 0f

    // 状态
    private var pressDown = false
    private var isMove = false
    private val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    override var isAbortAnim = false

    // 缩放状态
    private var zoomScale = 1f
    private var zoomOffsetX = 0f
    private var zoomOffsetY = 0f
    private var isZooming = false
    private var lastPanX = 0f
    private var lastPanY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isZooming = true
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1f, 3f)
            if (zoomScale == 1f) {
                zoomOffsetX = 0f
                zoomOffsetY = 0f
            }
            invalidate()
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (zoomScale < 1.05f) {
                zoomScale = 1f
                zoomOffsetX = 0f
                zoomOffsetY = 0f
            }
            isZooming = false
            invalidate()
        }
    })

    // 三页 Bitmap 缓冲（由外部直接提供原始图片）
    private var prevBitmap: Bitmap? = null
    private var curBitmap: Bitmap? = null
    private var nextBitmap: Bitmap? = null

    // 将原始图片缩放为全屏尺寸的 Bitmap（黑色背景 + 图片居中）
    private var scaledPrevBitmap: Bitmap? = null
    private var scaledCurBitmap: Bitmap? = null
    private var scaledNextBitmap: Bitmap? = null

    private var readMode: ComicReadMode = ComicReadMode.SCROLL

    // 黑色背景画笔
    private val bgPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.FILL
    }

    // 高质量缩放画笔（双线性过滤 + 抗锯齿）
    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

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

    fun setReadMode(mode: ComicReadMode) {
        if (readMode != mode) {
            readMode = mode
            updatePageDelegate()
        }
    }

    /**
     * 外部提供三页的图片 Bitmap（原始尺寸）
     */
    fun setBitmaps(prev: Bitmap?, cur: Bitmap?, next: Bitmap?) {
        prevBitmap = prev
        curBitmap = cur
        nextBitmap = next
        rebuildScaledBitmaps()
        invalidate()
    }

    /**
     * 将原始图片缩放到适配视图尺寸的 Bitmap
     * 黑色背景，图片按宽度等比缩放后垂直居中
     */
    private fun scaleImageToPage(source: Bitmap?): Bitmap? {
        if (source == null || width <= 0 || height <= 0) return null
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val scale = width.toFloat() / source.width.toFloat()
        val scaledHeight = source.height * scale
        val dy = (height - scaledHeight) / 2f

        val matrix = android.graphics.Matrix()
        matrix.setScale(scale, scale)
        // 如果图片比屏幕高，从顶部开始显示
        matrix.postTranslate(0f, dy.coerceAtLeast(0f))

        canvas.drawBitmap(source, matrix, scalePaint)
        return page
    }

    private fun rebuildScaledBitmaps() {
        if (width <= 0 || height <= 0) return
        scaledPrevBitmap = scaleImageToPage(prevBitmap)
        scaledCurBitmap = scaleImageToPage(curBitmap)
        scaledNextBitmap = scaleImageToPage(nextBitmap)
    }

    // --- PageView 接口实现 ---
    override fun getPrevBitmap(): Bitmap? = scaledPrevBitmap
    override fun getCurBitmap(): Bitmap? = scaledCurBitmap
    override fun getNextBitmap(): Bitmap? = scaledNextBitmap

    override fun hasNextPage(): Boolean = onPageChangeListener?.hasNextPage() ?: false
    override fun hasPrevPage(): Boolean = onPageChangeListener?.hasPrevPage() ?: false

    override fun getViewWidth(): Int = width
    override fun getViewHeight(): Int = height

    override fun fillPage(direction: PageDirection) {
        pageDelegate?.let {
            it.isRunning = false
            it.isStarted = false
            it.isMoved = false
        }

        when (direction) {
            PageDirection.NEXT -> {
                scaledPrevBitmap = scaledCurBitmap
                scaledCurBitmap = scaledNextBitmap
                scaledNextBitmap = null
            }
            PageDirection.PREV -> {
                scaledNextBitmap = scaledCurBitmap
                scaledCurBitmap = scaledPrevBitmap
                scaledPrevBitmap = null
            }
            PageDirection.NONE -> {}
        }
        invalidate()

        onPageChangeListener?.onPageChanged(direction)
    }

    override fun setStartPoint(x: Float, y: Float, invalidateView: Boolean) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y
        if (invalidateView) invalidate()
    }

    override fun setTouchPoint(x: Float, y: Float, invalidateView: Boolean) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidateView) invalidate()
        pageDelegate?.onScroll()
    }

    private fun updatePageDelegate() {
        val newDelegate: PageDelegate? = when (readMode) {
            ComicReadMode.SLIDE -> SlidePageDelegate(this)
            ComicReadMode.SIMULATION -> SimulationPageDelegate(this)
            ComicReadMode.SCROLL -> null
        }
        pageDelegate = newDelegate
        newDelegate?.setViewSize(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildScaledBitmaps()
        pageDelegate?.setViewSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 应用缩放变换
        if (zoomScale != 1f) {
            canvas.save()
            canvas.translate(zoomOffsetX, zoomOffsetY)
            canvas.scale(zoomScale, zoomScale, width / 2f, height / 2f)
        }

        if (pageDelegate?.isRunning == true) {
            pageDelegate?.onDraw(canvas)
        } else {
            scaledCurBitmap?.let {
                if (!it.isRecycled) canvas.drawBitmap(it, 0f, 0f, null)
            } ?: run {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
        }

        if (zoomScale != 1f) {
            canvas.restore()
        }
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageDelegate == null) return super.onTouchEvent(event)

        // 先让 ScaleGestureDetector 处理
        scaleGestureDetector.onTouchEvent(event)

        // 双指缩放或已缩放时的平移：不触发翻页
        if (event.pointerCount >= 2) {
            isZooming = true
            return true
        }

        // 缩放状态下单指拖动：平移而不是翻页
        if (zoomScale > 1.05f) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastPanX = event.x
                    lastPanY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastPanX
                    val dy = event.y - lastPanY
                    val maxOffsetX = width * (zoomScale - 1) / 2f
                    val maxOffsetY = height * (zoomScale - 1) / 2f
                    zoomOffsetX = (zoomOffsetX + dx).coerceIn(-maxOffsetX, maxOffsetX)
                    zoomOffsetY = (zoomOffsetY + dy).coerceIn(-maxOffsetY, maxOffsetY)
                    lastPanX = event.x
                    lastPanY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // 双击复位可以在此添加
                    return true
                }
            }
            return true
        }

        // 缩放手势刚结束，忽略此次触摸序列的剩余事件
        if (isZooming) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isZooming = false
            }
            return true
        }

        // 正常翻页逻辑
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
                    if (isAbortAnim) {
                        isAbortAnim = false
                        return true
                    }
                    val screenWidth = width
                    when {
                        startX < screenWidth / 3f -> {
                            pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
                        }
                        startX > screenWidth * 2f / 3f -> {
                            pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
                        }
                        else -> {
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
        scaledPrevBitmap?.recycle()
        scaledCurBitmap?.recycle()
        scaledNextBitmap?.recycle()
        scaledPrevBitmap = null
        scaledCurBitmap = null
        scaledNextBitmap = null
    }
}
