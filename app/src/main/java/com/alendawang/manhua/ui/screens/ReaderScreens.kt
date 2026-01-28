package com.alendawang.manhua.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.alendawang.manhua.R
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.AudioPlayerConfig
import com.alendawang.manhua.ui.components.AudioPlayerSettingsDialog
import com.alendawang.manhua.ui.components.VerticalFastScroller
import com.alendawang.manhua.utils.loadAudioPlayerConfig
import com.alendawang.manhua.utils.saveAudioPlayerConfig
import com.alendawang.manhua.utils.formatTime
import com.alendawang.manhua.model.AppLanguage
import com.alendawang.manhua.utils.AppStrings
import com.alendawang.manhua.utils.isLandscape
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

// --- 漫画阅读器 ---
@Composable
fun ReaderScreen(
    paddingValues: PaddingValues,
    images: List<Uri>,
    lazyListState: LazyListState,
    initialIndex: Int,
    isBarsVisible: Boolean,
    onToggleBars: () -> Unit,
    // 新增参数
    chapterName: String = "",
    chapterIndex: Int = 0,
    totalChapters: Int = 1,
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    onPrevChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    
    // 时间状态
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            kotlinx.coroutines.delay(30000) // 每30秒更新一次
        }
    }
    
    // 电量状态
    val batteryLevel = remember {
        val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
        batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
    }

    val pageIndicatorText by remember(images.size) {
        derivedStateOf {
            "${lazyListState.firstVisibleItemIndex + 1} / ${images.size}"
        }
    }
    
    // 检测是否到达最后一页
    val isAtLastPage by remember {
        derivedStateOf {
            images.isNotEmpty() && lazyListState.firstVisibleItemIndex >= images.size - 1
        }
    }
    
    // 章节结尾提示状态
    var showEndOfChapterHint by remember { mutableStateOf(false) }
    var canTriggerNextChapter by remember { mutableStateOf(false) }
    
    // 检测是否可以切换到下一章
    val hasNextChapter = chapterIndex < totalChapters - 1 && onNextChapter != null
    val hasPrevChapter = chapterIndex > 0 && onPrevChapter != null
    
    // 当到达最后一页时显示提示
    LaunchedEffect(isAtLastPage) {
        if (isAtLastPage && hasNextChapter) {
            showEndOfChapterHint = true
            kotlinx.coroutines.delay(500)
            canTriggerNextChapter = true
        } else {
            showEndOfChapterHint = false
            canTriggerNextChapter = false
        }
    }

    // 预加载相邻图片
    val imageLoader = remember { ImageLoader.Builder(context).build() }
    LaunchedEffect(Unit) {
        val visibleStart = lazyListState.firstVisibleItemIndex
        val preloadRange = (visibleStart - 2).coerceAtLeast(0)..(visibleStart + 5).coerceAtMost(images.size - 1)
        preloadRange.forEach { index ->
            images.getOrNull(index)?.let { uri ->
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(screenWidth.toInt())
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val minImageHeight = screenWidthDp

    // 缩放状态
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color(0xFF121212))
            // 缩放手势检测
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 2.5f)
                    if (scale == 1f) {
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    } else {
                        // 只处理水平移动，垂直移动交给 LazyColumn 滚动
                        val newX = offset.x + pan.x
                        // 简单的边界限制
                        val maxPanX = (screenWidth * scale - screenWidth) / 2
                        offset = androidx.compose.ui.geometry.Offset(
                            newX.coerceIn(-maxPanX, maxPanX),
                            0f
                        )
                        
                        // 手动分发垂直滚动事件给 LazyListState
                        if (pan.y != 0f) {
                             scope.launch {
                                 lazyListState.scrollBy(-pan.y)
                             }
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = 0f 
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(images, key = { it.toString() }) { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, 
                            indication = null
                        ) { onToggleBars() }
                ) {
                    val model = ImageRequest.Builder(context)
                        .data(uri)
                        .size(screenWidth.toInt()) // 限制请求宽度，高度自适应
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .build()

                    coil.compose.SubcomposeAsyncImage(
                        model = model,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                        loading = {
                            // 加载时显示占位高度，防止跳动
                            Box(modifier = Modifier.fillMaxWidth().height(minImageHeight).background(Color(0xFF1E1E1E)))
                        },
                        success = { state ->
                            // 加载成功后直接显示图片，不强制最小高度，解决横图留白问题
                            Image(
                                painter = state.painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    )
                }
            }
            
            // 章节结尾提示
            if (hasNextChapter) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable {
                                if (canTriggerNextChapter) {
                                    onNextChapter?.invoke()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (appLanguage == AppLanguage.CHINESE) "— 本章结束 —" else "— End of Chapter —",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (appLanguage == AppLanguage.CHINESE) "点击或继续滑动阅读下一章" else "Tap or swipe for next chapter",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { onNextChapter?.invoke() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (appLanguage == AppLanguage.CHINESE) "下一章" else "Next Chapter")
                            }
                        }
                    }
                }
            }
        }
        
        // 顶部栏（菜单呼出时显示）
        AnimatedVisibility(
            visible = isBarsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 章节名称
                Text(
                    text = if (appLanguage == AppLanguage.CHINESE) "阅读中 - ${if (chapterName.isNotEmpty()) chapterName else "第${chapterIndex + 1}章"}" else "Reading - ${if (chapterName.isNotEmpty()) chapterName else "Ch.${chapterIndex + 1}"}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(Modifier.width(12.dp))
                
                // 时间
                Text(
                    text = currentTime,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(Modifier.width(8.dp))
                
                // 电量
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            batteryLevel <= 20 -> Icons.Rounded.Battery0Bar
                            batteryLevel <= 50 -> Icons.Rounded.Battery3Bar
                            batteryLevel <= 80 -> Icons.Rounded.Battery5Bar
                            else -> Icons.Rounded.BatteryFull
                        },
                        contentDescription = if (appLanguage == AppLanguage.CHINESE) "电量" else "Battery",
                        tint = if (batteryLevel <= 20) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // 底部栏：页码始终显示，菜单呼出时显示章节切换按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 上一章按钮（菜单呼出时显示）
                AnimatedVisibility(
                    visible = isBarsVisible && hasPrevChapter,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .clickable { onPrevChapter?.invoke() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = if (appLanguage == AppLanguage.CHINESE) "上一章" else "Previous Chapter",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                
                // 页码指示器（始终显示）
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pageIndicatorText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 下一章按钮（菜单呼出时显示）
                AnimatedVisibility(
                    visible = isBarsVisible && hasNextChapter,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .clickable { onNextChapter?.invoke() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = if (appLanguage == AppLanguage.CHINESE) "下一章" else "Next Chapter",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 快速滚动条
        if (images.isNotEmpty() && scale == 1f) {
            VerticalFastScroller(
                listState = lazyListState,
                totalItems = images.size,
                isVisible = isBarsVisible,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // 返回顶部按钮
        AnimatedVisibility(
            visible = isBarsVisible && lazyListState.firstVisibleItemIndex > 3, 
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 80.dp, end = 24.dp), 
            enter = fadeIn() + scaleIn(), 
            exit = fadeOut() + scaleOut()
        ) {
            FloatingActionButton(
                onClick = { scope.launch { lazyListState.scrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Rounded.ArrowUpward, null) }
        }
    }
}

// --- 音频播放器 ---

@Composable
fun AudioPlayerScreen(
    paddingValues: PaddingValues,
    audio: AudioHistory,
    playlist: List<AudioHistory>,
    trackIndex: Int,
    initialPosition: Long,
    onToggleBars: () -> Unit,
    isBarsVisible: Boolean,
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    onTrackChange: (Int) -> Unit,
    onAudioChange: (String, Int) -> Unit,
    onUpdateBackground: (String?) -> Unit = {},
    showLyricsInitially: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(loadAudioPlayerConfig(context)) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 监听播放进度
    // AudioPlaybackBus.state 是一个 StateFlow，我们通过 collectAsState 订阅
    val playbackState by com.alendawang.manhua.AudioPlaybackBus.state.collectAsState()
    
    // 使用 playbackState.trackIndex 而不是传入的 trackIndex，确保后台切歌时歌词能正确同步
    val effectiveTrackIndex = if (playbackState.audioId == audio.id && playbackState.trackIndex >= 0) {
        playbackState.trackIndex
    } else {
        trackIndex
    }
    
    // --- 元数据与歌词状态 ---
    // 使用 remember 缓存 metadata，key 为当前的 track URI
    val currentTrackUriString = audio.tracks.getOrNull(effectiveTrackIndex)?.uriString 
    var metadata by remember(currentTrackUriString) { mutableStateOf<com.alendawang.manhua.utils.AudioMetadata?>(null) }
    var lyrics by remember(currentTrackUriString) { mutableStateOf<List<com.alendawang.manhua.utils.LyricLine>>(emptyList()) }
    
    // 歌词显示状态：如果 showLyricsInitially 为 true（从唱片按钮跳转），强制显示歌词
    // 否则使用保存的状态（从通知栏跳转时恢复上次状态）
    var showLyrics by remember { 
        mutableStateOf(if (showLyricsInitially) true else playbackState.showLyrics) 
    }
    
    // 当 showLyrics 状态变化时，同步到 AudioPlaybackBus
    LaunchedEffect(showLyrics) {
        com.alendawang.manhua.AudioPlaybackBus.state.update { it.copy(showLyrics = showLyrics) }
    }
    
    // 当进入播放页面或切换曲目时，启动播放服务
    LaunchedEffect(audio.id, trackIndex) {
        com.alendawang.manhua.AudioPlaybackService.startPlayback(
            context = context,
            audio = audio,
            startIndex = trackIndex,
            startPosition = initialPosition,
            autoPlay = true, // 进入播放器页面默认自动播放
            playlist = playlist
        )
    }
    
    // 进入播放界面时隐藏悬浮歌词，离开时恢复显示
    // 使用 LaunchedEffect 在进入时立即隐藏
    LaunchedEffect(config.floatingLyricsEnabled) {
        if (config.floatingLyricsEnabled) {
            com.alendawang.manhua.FloatingLyricsService.hide(context)
        }
    }
    
    // 使用生命周期观察者检测 app 进入后台/前台
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, config.floatingLyricsEnabled) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // 读取当前 SharedPreferences 中的设置（而非内存中的 config）
            val prefs = context.getSharedPreferences("audio_player_config", android.content.Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("floating_lyrics_enabled", false)
            
            if (isEnabled) {
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                        // App 进入后台：显示悬浮歌词
                        com.alendawang.manhua.FloatingLyricsService.show(context)
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        // App 回到前台：隐藏悬浮歌词
                        com.alendawang.manhua.FloatingLyricsService.hide(context)
                    }
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 离开播放界面（导航到其他页面）：检查 SharedPreferences 设置
            val prefs = context.getSharedPreferences("audio_player_config", android.content.Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("floating_lyrics_enabled", false)
            if (isEnabled) {
                com.alendawang.manhua.FloatingLyricsService.show(context)
            }
        }
    }
    
    // 加载元数据和歌词
    LaunchedEffect(currentTrackUriString) {
        if (currentTrackUriString != null) {
            val uri = Uri.parse(currentTrackUriString)
            launch(Dispatchers.IO) {
                metadata = com.alendawang.manhua.utils.AudioMetadataUtils.getAudioMetadata(context, uri)
                lyrics = com.alendawang.manhua.utils.AudioMetadataUtils.findAndParseLyrics(context, uri)
            }
        }
    }

    // Cover/background priority: per-song custom -> embedded cover -> folder cover -> default cover image
    val effectiveCoverData: Any = audio.customBackgroundUriString
        ?: metadata?.coverBitmap
        ?: audio.coverUriString
        ?: R.drawable.default_audio_cover

    val bgImageRequest = remember(audio.id, audio.customBackgroundUriString, metadata, effectiveCoverData) {
        ImageRequest.Builder(context)
            .data(effectiveCoverData)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val currentTrack = audio.tracks.getOrNull(effectiveTrackIndex)

    if (showSettingsDialog) {
        AudioPlayerSettingsDialog(
            config = config,
            hasCustomBackground = audio.customBackgroundUriString != null,
            onConfigChange = { newConfig ->
                config = newConfig
                saveAudioPlayerConfig(context, newConfig)
            },
            onBackgroundChange = onUpdateBackground,
            onDismiss = { showSettingsDialog = false }
        )
    }

    val isLandscapeMode = isLandscape()
    
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = bgImageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(30.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = config.overlayAlpha.coerceAtLeast(0.3f)))
        )

        if (isLandscapeMode) {
            // ===== 横屏布局：左边控制区 + 右边歌词 =====
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 左边：封面 + 信息 (顶部) + 进度条 + 控制按钮 (底部)
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 黑胶唱片封面 - 使用 weight 填充可用空间
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.alendawang.manhua.ui.components.VinylRecordView(
                            coverData = effectiveCoverData,
                            isPlaying = playbackState.isPlaying,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // 歌曲信息 - 固定高度
                    Text(
                        text = metadata?.title?.takeIf { it.isNotBlank() } ?: currentTrack?.name ?: if (appLanguage == AppLanguage.CHINESE) "未知曲目" else "Unknown Track",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = metadata?.artist?.takeIf { it.isNotBlank() } ?: audio.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 下半部分：进度条 + 控制按钮
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 进度条
                        Column(modifier = Modifier.fillMaxWidth()) {
                            com.alendawang.manhua.ui.components.MeteorSlider(
                                value = playbackState.positionMs.toFloat(),
                                onValueChange = { newPos ->
                                    com.alendawang.manhua.AudioPlaybackService.seekTo(context, newPos.toLong())
                                },
                                valueRange = 0f..(playbackState.durationMs.coerceAtLeast(1)).toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(playbackState.positionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatTime(playbackState.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // 播放控制按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (effectiveTrackIndex > 0) {
                                        onTrackChange(effectiveTrackIndex - 1)
                                    } else {
                                        val prevAudioIndex = playlist.indexOfFirst { it.id == audio.id } - 1
                                        if (prevAudioIndex >= 0) {
                                            val prevAudio = playlist[prevAudioIndex]
                                            onAudioChange(prevAudio.id, prevAudio.tracks.lastIndex.coerceAtLeast(0))
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            IconButton(
                                onClick = { 
                                    val newPos = (playbackState.positionMs - 10000).coerceAtLeast(0)
                                    com.alendawang.manhua.AudioPlaybackService.seekTo(context, newPos)
                                }
                            ) {
                                Icon(Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            FloatingActionButton(
                                onClick = { com.alendawang.manhua.AudioPlaybackService.toggle(context) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    null, 
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { 
                                    val newPos = (playbackState.positionMs + 10000).coerceAtMost(playbackState.durationMs)
                                    com.alendawang.manhua.AudioPlaybackService.seekTo(context, newPos)
                                }
                            ) {
                                Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            IconButton(
                                onClick = {
                                    if (effectiveTrackIndex < audio.tracks.lastIndex) {
                                        onTrackChange(effectiveTrackIndex + 1)
                                    } else {
                                        val nextAudioIndex = playlist.indexOfFirst { it.id == audio.id } + 1
                                        if (nextAudioIndex < playlist.size) {
                                            val nextAudio = playlist[nextAudioIndex]
                                            onAudioChange(nextAudio.id, 0)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
                
                // 右边：歌词显示区域
                Box(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val currentLineIndex = remember(playbackState.positionMs, lyrics) {
                        lyrics.indexOfLast { it.startTime <= playbackState.positionMs }.coerceAtLeast(0)
                    }
                    com.alendawang.manhua.ui.components.LyricView(
                        lyrics = lyrics,
                        currentTime = playbackState.positionMs,
                        isPlaying = playbackState.isPlaying,
                        onSeek = { time ->
                            com.alendawang.manhua.AudioPlaybackService.seekTo(context, time)
                        },
                        onDismiss = { /* 横屏模式下歌词常显，不需要dismiss */ },
                        initialLineIndex = currentLineIndex,
                        fontSize = config.lyricsFontSize,
                        textColor = Color(config.lyricsColor)
                    )
                }
            }
        } else {
            // ===== 竖屏布局：原有设计 =====
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))

                // 封面 / 歌词区域 (可点击切换)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = showLyrics,
                        transitionSpec = {
                            fadeIn(tween(300)) + scaleIn(initialScale = 0.9f) togetherWith
                                fadeOut(tween(300)) + scaleOut(targetScale = 0.9f)
                        },
                        label = "CoverLyricsSwitch"
                    ) { isLyricsMode ->
                        if (isLyricsMode) {
                            val currentLineIndex = remember(playbackState.positionMs, lyrics) {
                                lyrics.indexOfLast { it.startTime <= playbackState.positionMs }.coerceAtLeast(0)
                            }
                            com.alendawang.manhua.ui.components.LyricView(
                                lyrics = lyrics,
                                currentTime = playbackState.positionMs,
                                isPlaying = playbackState.isPlaying,
                                onSeek = { time ->
                                    com.alendawang.manhua.AudioPlaybackService.seekTo(context, time)
                                },
                                onDismiss = { showLyrics = false },
                                initialLineIndex = currentLineIndex,
                                fontSize = config.lyricsFontSize,
                                textColor = Color(config.lyricsColor)
                            )
                        } else {
                            // 黑胶唱片封面
                            com.alendawang.manhua.ui.components.VinylRecordView(
                                coverData = effectiveCoverData,
                                isPlaying = playbackState.isPlaying,
                                modifier = Modifier.fillMaxSize(),
                                onClick = { showLyrics = true }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (showLyrics) 16.dp else 32.dp))
                
                // 歌曲信息 - 仅在封面模式显示
                AnimatedVisibility(
                    visible = !showLyrics,
                    enter = fadeIn(tween(300)) + expandVertically(),
                    exit = fadeOut(tween(300)) + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = metadata?.title?.takeIf { it.isNotBlank() } ?: currentTrack?.name ?: if (appLanguage == AppLanguage.CHINESE) "未知曲目" else "Unknown Track",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = metadata?.artist?.takeIf { it.isNotBlank() } ?: audio.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // 进度条
                Column(modifier = Modifier.fillMaxWidth()) {
                    com.alendawang.manhua.ui.components.MeteorSlider(
                        value = playbackState.positionMs.toFloat(),
                        onValueChange = { newPos ->
                            com.alendawang.manhua.AudioPlaybackService.seekTo(context, newPos.toLong())
                        },
                        valueRange = 0f..(playbackState.durationMs.coerceAtLeast(1)).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(playbackState.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatTime(playbackState.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))

                // 播放控制栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (effectiveTrackIndex > 0) {
                                onTrackChange(effectiveTrackIndex - 1)
                            } else {
                                val prevAudioIndex = playlist.indexOfFirst { it.id == audio.id } - 1
                                if (prevAudioIndex >= 0) {
                                    val prevAudio = playlist[prevAudioIndex]
                                    onAudioChange(prevAudio.id, prevAudio.tracks.lastIndex.coerceAtLeast(0))
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, if (appLanguage == AppLanguage.CHINESE) "上一曲" else "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = { 
                            val newPos = (playbackState.positionMs - 10000).coerceAtLeast(0)
                            com.alendawang.manhua.AudioPlaybackService.seekTo(context, newPos)
                        }
                    ) {
                        Icon(Icons.Rounded.FastRewind, if (appLanguage == AppLanguage.CHINESE) "快退10秒" else "-10s", tint = Color.White, modifier = Modifier.size(40.dp))
                    }

                    FloatingActionButton(
                        onClick = { com.alendawang.manhua.AudioPlaybackService.toggle(context) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (appLanguage == AppLanguage.CHINESE) "播放/暂停" else "Play/Pause", 
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { 
                            val newPos = (playbackState.positionMs + 10000).coerceAtMost(playbackState.durationMs)
                            com.alendawang.manhua.AudioPlaybackService.seekTo(context, newPos)
                        }
                    ) {
                        Icon(Icons.Rounded.FastForward, if (appLanguage == AppLanguage.CHINESE) "快进10秒" else "+10s", tint = Color.White, modifier = Modifier.size(40.dp))
                    }

                    IconButton(
                        onClick = {
                            if (effectiveTrackIndex < audio.tracks.lastIndex) {
                                onTrackChange(effectiveTrackIndex + 1)
                            } else {
                                val nextAudioIndex = playlist.indexOfFirst { it.id == audio.id } + 1
                                if (nextAudioIndex < playlist.size) {
                                    val nextAudio = playlist[nextAudioIndex]
                                    onAudioChange(nextAudio.id, 0)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.SkipNext, if (appLanguage == AppLanguage.CHINESE) "下一曲" else "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        
        // 右上角设置按钮
        IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = paddingValues.calculateTopPadding() + 8.dp, end = 16.dp)
        ) {
            Icon(Icons.Rounded.Settings, if (appLanguage == AppLanguage.CHINESE) "设置" else "Settings", tint = Color.White)
        }
    }
}
