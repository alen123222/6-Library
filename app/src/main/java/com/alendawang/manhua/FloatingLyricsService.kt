package com.alendawang.manhua

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.alendawang.manhua.utils.AudioMetadataUtils
import com.alendawang.manhua.utils.LyricLine

/**
 * 悬浮歌词服务
 * 在桌面显示当前播放的歌词，支持拖拽移动、双击返回播放界面、长按关闭
 */
class FloatingLyricsService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var floatingContainer: LinearLayout? = null
    private var lyricTextView: TextView? = null
    private var closeButton: ImageView? = null
    
    private var lyrics: List<LyricLine> = emptyList()
    private var currentTrackUri: String? = null
    private var lyricsLoadJob: Job? = null
    
    // 悬浮窗是否可见
    private var isVisible = true
    // 长按模式（显示关闭按钮）
    private var isLongPressMode = false
    // 配置
    private var textColor: Int = 0xFF87CEEB.toInt()
    private var textSize: Float = 18f
    
    // 双击检测
    private var lastTapTime = 0L
    private val doubleTapThreshold = 300L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadConfig()
        createFloatingView()
        startLyricUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingView()
            ACTION_HIDE -> hideFloatingView()
            ACTION_STOP -> stopSelf()
            ACTION_START_HIDDEN -> {
                // 服务启动时即隐藏（用于在播放界面内启动服务的情况）
                isVisible = false
                floatingContainer?.visibility = View.GONE
            }
            ACTION_UPDATE_CONFIG -> {
                loadConfig()
                updateTextStyle()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingView()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun loadConfig() {
        val prefs = getSharedPreferences("audio_player_config", Context.MODE_PRIVATE)
        textColor = prefs.getInt("floating_lyric_color", 0xFF87CEEB.toInt())
        textSize = prefs.getFloat("floating_lyric_text_size", 18f)
    }
    
    private fun updateTextStyle() {
        lyricTextView?.apply {
            setTextColor(textColor)
            this.textSize = this@FloatingLyricsService.textSize
            // 添加描边效果
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }
    }

    private fun createFloatingView() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        // 创建容器
        floatingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
        }

        // 创建歌词文本视图 - 无背景，只有文字和阴影
        lyricTextView = TextView(this).apply {
            text = ""
            textSize = this@FloatingLyricsService.textSize
            setTextColor(textColor)
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            maxLines = 2
            typeface = Typeface.DEFAULT_BOLD
        }

        // 创建关闭按钮（初始隐藏）
        closeButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            visibility = View.GONE
            setPadding(16, 0, 0, 0)
            setOnClickListener {
                // 保存设置为关闭状态
                getSharedPreferences("audio_player_config", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("floating_lyrics_enabled", false)
                    .apply()
                // 发送广播通知 UI 更新
                sendBroadcast(Intent(ACTION_FLOATING_LYRICS_DISABLED).apply {
                    setPackage(packageName)
                })
                // 关闭悬浮歌词
                stopSelf()
            }
        }

        floatingContainer?.addView(lyricTextView)
        floatingContainer?.addView(closeButton)

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        // 添加触摸监听
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressJob: Job? = null

        floatingContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    
                    // 开始长按检测
                    longPressJob = serviceScope.launch {
                        delay(500)
                        if (!isDragging) {
                            isLongPressMode = true
                            closeButton?.visibility = View.VISIBLE
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (deltaX * deltaX + deltaY * deltaY > 100) {
                        isDragging = true
                        longPressJob?.cancel()
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        windowManager?.updateViewLayout(floatingContainer, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    if (!isDragging && !isLongPressMode) {
                        // 检测双击
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < doubleTapThreshold) {
                            // 双击 - 返回播放界面
                            navigateToPlayer()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                        }
                    }
                    if (isLongPressMode) {
                        // 延迟隐藏关闭按钮
                        serviceScope.launch {
                            delay(3000)
                            isLongPressMode = false
                            closeButton?.visibility = View.GONE
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingContainer, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun navigateToPlayer() {
        val playbackState = AudioPlaybackBus.state.value
        if (playbackState.audioId != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AudioPlaybackService.EXTRA_NAVIGATE_TO_AUDIO_PLAYER, true)
                putExtra("extra_audio_id", playbackState.audioId)
                putExtra("extra_start_index", playbackState.trackIndex)
                putExtra("show_lyrics_initially", true)
            }
            startActivity(intent)
        }
    }

    private fun startLyricUpdates() {
        serviceScope.launch {
            AudioPlaybackBus.state.collectLatest { state ->
                if (!isVisible) return@collectLatest
                
                val trackUri = state.currentTrackUri
                
                // 如果曲目变化，重新加载歌词
                if (trackUri != currentTrackUri && trackUri != null) {
                    currentTrackUri = trackUri
                    loadLyrics(trackUri)
                }
                
                // 更新当前歌词行
                if (lyrics.isNotEmpty() && state.isPlaying) {
                    val currentLine = lyrics.indexOfLast { it.startTime <= state.positionMs }
                    if (currentLine >= 0) {
                        val lyricText = lyrics[currentLine].text
                        lyricTextView?.text = lyricText
                    }
                } else if (!state.isPlaying && lyrics.isEmpty()) {
                    lyricTextView?.text = ""
                }
            }
        }
    }

    private fun loadLyrics(trackUri: String) {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(trackUri)
                val loadedLyrics = AudioMetadataUtils.findAndParseLyrics(this@FloatingLyricsService, uri)
                lyrics = loadedLyrics
                if (lyrics.isEmpty()) {
                    launch(Dispatchers.Main) {
                        lyricTextView?.text = "暂无歌词"
                    }
                }
            } catch (e: Exception) {
                lyrics = emptyList()
            }
        }
    }

    private fun showFloatingView() {
        isVisible = true
        floatingContainer?.visibility = View.VISIBLE
    }

    private fun hideFloatingView() {
        isVisible = false
        floatingContainer?.visibility = View.GONE
    }

    private fun removeFloatingView() {
        try {
            floatingContainer?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        floatingContainer = null
        lyricTextView = null
        closeButton = null
    }

    companion object {
        private const val ACTION_SHOW = "com.alendawang.manhua.action.FLOATING_SHOW"
        private const val ACTION_HIDE = "com.alendawang.manhua.action.FLOATING_HIDE"
        private const val ACTION_STOP = "com.alendawang.manhua.action.FLOATING_STOP"
        private const val ACTION_UPDATE_CONFIG = "com.alendawang.manhua.action.FLOATING_UPDATE_CONFIG"
        private const val ACTION_START_HIDDEN = "com.alendawang.manhua.action.FLOATING_START_HIDDEN"
        // 公开常量，供 UI 监听
        const val ACTION_FLOATING_LYRICS_DISABLED = "com.alendawang.manhua.action.FLOATING_LYRICS_DISABLED"

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.startService(intent)
        }
        
        // 在播放界面启动服务时用，启动后立即隐藏
        fun startHidden(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.startService(intent)
            // 立即发送隐藏命令
            val hideIntent = Intent(context, FloatingLyricsService::class.java).apply {
                action = ACTION_START_HIDDEN
            }
            context.startService(hideIntent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun show(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
        
        fun updateConfig(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
            }
            context.startService(intent)
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun openOverlayPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
