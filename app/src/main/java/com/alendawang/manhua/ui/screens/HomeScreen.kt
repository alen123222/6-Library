package com.alendawang.manhua.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.alendawang.manhua.model.AppTheme
import com.alendawang.manhua.model.AudioDisplayMode
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.ComicHistory
import com.alendawang.manhua.model.DisplayMode
import com.alendawang.manhua.model.MediaHistory
import com.alendawang.manhua.model.MediaType
import com.alendawang.manhua.model.NovelHistory
import com.alendawang.manhua.model.AudioTrack
import com.alendawang.manhua.model.AppLanguage
import com.alendawang.manhua.ui.components.*
import com.alendawang.manhua.utils.AppStrings
import kotlinx.coroutines.launch

// Bouncing music note animation for playing indicator
@Composable
fun BouncingMusicNote(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    Icon(
        Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.offset { IntOffset(0, offsetY.dp.roundToPx()) }.size(20.dp)
    )
}

@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    historyList: List<MediaHistory>,
    currentTheme: AppTheme,
    customBackgroundUri: String? = null,
    customBackgroundAlpha: Float = 0.4f,
    displayMode: DisplayMode,
    audioDisplayMode: AudioDisplayMode = AudioDisplayMode.ALBUMS,
    currentMediaType: MediaType,
    isHiddenMode: Boolean,
    isMultiSelectMode: Boolean = false,
    selectedItems: Set<String> = emptySet(),
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    isAudioPlaying: Boolean = false,
    currentPlayingAudioId: String? = null,
    currentPlayingTrackIndex: Int = 0,
    searchQuery: String = "",
    audioSearchMatchingTracks: Map<String, List<Int>> = emptyMap(),
    onMediaTypeChange: (MediaType) -> Unit,
    onAudioDisplayModeChange: (AudioDisplayMode) -> Unit = {},
    onBatchScanClick: () -> Unit,
    onToggleMultiSelectMode: () -> Unit,
    onHistoryItemClick: (MediaHistory) -> Unit,
    onHistoryItemLongClick: (MediaHistory) -> Unit,
    onAudioTrackClick: (AudioHistory, Int) -> Unit = { _, _ -> },
    onAudioTrackLongClick: (AudioHistory, Int) -> Unit = { _, _ -> },
    onToggleSelection: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Swipe threshold for instant module switching
    val swipeThreshold = 100f
    var totalDrag = remember { mutableStateOf(0f) }

    // 优化 5: 预加载可见区域的图片
    LaunchedEffect(historyList) {
        val imageLoader = ImageLoader.Builder(context).build()
        historyList.take(10).forEach { item ->
            item.coverUriString?.let { uri ->
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }

    // 检测是否横屏
    val isLandscapeMode = com.alendawang.manhua.utils.isLandscape()

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // 自定义背景 - 位于菜单栏下方，不会被遮挡
        if (customBackgroundUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(customBackgroundUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = customBackgroundAlpha
            )
        }

        // 横屏模式：使用 Row 布局，左侧放媒体切换按钮
        if (isLandscapeMode) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧媒体类型切换按钮（垂直排列）
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top)
                ) {
                    MediaType.values().forEach { type ->
                        val isSelected = type == currentMediaType
                        Button(
                            onClick = { onMediaTypeChange(type) },
                            modifier = Modifier
                                .width(100.dp)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isSelected) 4.dp else 0.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(type.icon, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(when (type) {
                                MediaType.COMIC -> AppStrings.comics(appLanguage)
                                MediaType.NOVEL -> AppStrings.novels(appLanguage)
                                MediaType.AUDIO -> AppStrings.audio(appLanguage)
                            }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }

                // 右侧内容区域（包含标题栏和列表）
                LandscapeContentArea(
                    historyList = historyList,
                    currentTheme = currentTheme,
                    customBackgroundUri = customBackgroundUri,
                    customBackgroundAlpha = customBackgroundAlpha,
                    displayMode = displayMode,
                    audioDisplayMode = audioDisplayMode,
                    currentMediaType = currentMediaType,
                    isHiddenMode = isHiddenMode,
                    isMultiSelectMode = isMultiSelectMode,
                    selectedItems = selectedItems,
                    appLanguage = appLanguage,
                    isAudioPlaying = isAudioPlaying,
                    currentPlayingAudioId = currentPlayingAudioId,
                    currentPlayingTrackIndex = currentPlayingTrackIndex,
                    searchQuery = searchQuery,
                    audioSearchMatchingTracks = audioSearchMatchingTracks,
                    onAudioDisplayModeChange = onAudioDisplayModeChange,
                    onBatchScanClick = onBatchScanClick,
                    onToggleMultiSelectMode = onToggleMultiSelectMode,
                    onHistoryItemClick = onHistoryItemClick,
                    onHistoryItemLongClick = onHistoryItemLongClick,
                    onAudioTrackClick = onAudioTrackClick,
                    onAudioTrackLongClick = onAudioTrackLongClick,
                    onToggleSelection = onToggleSelection,
                    onNavigateToPlayer = onNavigateToPlayer
                )
            }
        } else {
            // 竖屏模式：原有布局（媒体切换在顶部）
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(16.dp))

            // 媒体类型切换按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MediaType.values().forEach { type ->
                    val isSelected = type == currentMediaType
                    Button(
                        onClick = { onMediaTypeChange(type) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
                    ) {
                        Icon(type.icon, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(when (type) {
                            MediaType.COMIC -> AppStrings.comics(appLanguage)
                            MediaType.NOVEL -> AppStrings.novels(appLanguage)
                            MediaType.AUDIO -> AppStrings.audio(appLanguage)
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isHiddenMode) {
                        if (appLanguage == AppLanguage.CHINESE) "绝密档案" else "Secret Files"
                    } else {
                        when (currentMediaType) {
                            MediaType.COMIC -> if (appLanguage == AppLanguage.CHINESE) "漫画架" else "Shelf"
                            MediaType.NOVEL -> if (appLanguage == AppLanguage.CHINESE) "小说架" else "Shelf"
                            MediaType.AUDIO -> if (appLanguage == AppLanguage.CHINESE) "音频架" else "Shelf"
                        }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(8.dp))
                // Calculate correct count based on audio display mode
                val displayCount = if (currentMediaType == MediaType.AUDIO && audioDisplayMode == AudioDisplayMode.SINGLES) {
                    historyList.filterIsInstance<AudioHistory>().sumOf { it.tracks.size }
                } else {
                    historyList.size
                }
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("$displayCount", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 音频显示模式切换按钮 (仅在音频模式显示)
                if (currentMediaType == MediaType.AUDIO) {
                    IconButton(
                        onClick = {
                            val newMode = if (audioDisplayMode == AudioDisplayMode.ALBUMS) 
                                AudioDisplayMode.SINGLES else AudioDisplayMode.ALBUMS
                            onAudioDisplayModeChange(newMode)
                        }
                    ) {
                        Icon(
                            audioDisplayMode.icon,
                            contentDescription = audioDisplayMode.label,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 多选模式切换按钮
                IconButton(
                    onClick = onToggleMultiSelectMode
                ) {
                    Icon(
                        if (isMultiSelectMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                        contentDescription = if (isMultiSelectMode) "退出多选" else "多选",
                        tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }



        // Content area with swipe gesture for instant module switching
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(currentMediaType) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDrag.value < -swipeThreshold) {
                                // Swiped left -> next module
                                val nextIndex = (currentMediaType.ordinal + 1).coerceAtMost(MediaType.values().size - 1)
                                if (nextIndex != currentMediaType.ordinal) {
                                    onMediaTypeChange(MediaType.fromIndex(nextIndex))
                                }
                            } else if (totalDrag.value > swipeThreshold) {
                                // Swiped right -> previous module
                                val prevIndex = (currentMediaType.ordinal - 1).coerceAtLeast(0)
                                if (prevIndex != currentMediaType.ordinal) {
                                    onMediaTypeChange(MediaType.fromIndex(prevIndex))
                                }
                            }
                            totalDrag.value = 0f
                        },
                        onDragCancel = { totalDrag.value = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag.value += dragAmount
                        }
                    )
                }
        ) {
            // Show content based on currentMediaType
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(currentMediaType.icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isHiddenMode) {
                                if (appLanguage == AppLanguage.CHINESE) "这里空空如也..." else "Nothing here..."
                            } else {
                                if (appLanguage == AppLanguage.CHINESE) "暂无内容，请点击下方按钮扫描" else "No content. Tap the button below to scan."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Content for the current module
                when (currentMediaType) {
                    MediaType.COMIC -> {
                        ComicTabContent(
                            comicList = historyList.filterIsInstance<ComicHistory>(),
                            currentTheme = currentTheme,
                            displayMode = displayMode,
                            cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            onHistoryItemClick = { onHistoryItemClick(it) },
                            onHistoryItemLongClick = { onHistoryItemLongClick(it) }
                        )
                    }
                    MediaType.NOVEL -> {
                        NovelTabContent(
                            novelList = historyList.filterIsInstance<NovelHistory>(),
                            currentTheme = currentTheme,
                            displayMode = displayMode,
                            cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            onHistoryItemClick = { onHistoryItemClick(it) },
                            onHistoryItemLongClick = { onHistoryItemLongClick(it) }
                        )
                    }
                    MediaType.AUDIO -> {
                        AudioTabContent(
                            audioList = historyList.filterIsInstance<AudioHistory>(),
                            currentTheme = currentTheme,
                            displayMode = displayMode,
                            audioDisplayMode = audioDisplayMode,
                            cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            isAudioPlaying = isAudioPlaying,
                            currentPlayingAudioId = currentPlayingAudioId,
                            currentPlayingTrackIndex = currentPlayingTrackIndex,
                            searchQuery = searchQuery,
                            audioSearchMatchingTracks = audioSearchMatchingTracks,
                            onHistoryItemClick = { onHistoryItemClick(it) },
                            onHistoryItemLongClick = { onHistoryItemLongClick(it) },
                            onAudioTrackClick = onAudioTrackClick,
                            onAudioTrackLongClick = onAudioTrackLongClick
                        )
                    }
                }
            }
        }
        } // End of else block (portrait Column)
        } // End of if/else block

        // 悬浮唱片按钮 (所有模式都显示，仅在播放时显示)
        // 位于导入按钮上方，带渐变色和跳动动画
        if (isAudioPlaying && currentPlayingAudioId != null) {
            // Bounce animation
            val infiniteTransition = rememberInfiniteTransition(label = "playerButton")
            val bounceOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce"
            )
            
            // Gradient color animation
            val colorPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "colorShift"
            )
            
            // Create animated gradient colors
            val gradientColors = listOf(
                Color(0xFFFF6B6B), // Red
                Color(0xFFFFE66D), // Yellow
                Color(0xFF4ECDC4), // Teal
                Color(0xFF9B59B6), // Purple
                Color(0xFFFF6B6B)  // Red (for seamless loop)
            )
            val startIndex = (colorPhase * (gradientColors.size - 1)).toInt()
            val endIndex = (startIndex + 1).coerceAtMost(gradientColors.size - 1)
            val fraction = (colorPhase * (gradientColors.size - 1)) - startIndex
            val currentColor = lerp(gradientColors[startIndex], gradientColors[endIndex], fraction)
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 100.dp)
                    .navigationBarsPadding()
                    .offset { IntOffset(0, bounceOffset.dp.roundToPx()) }
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                currentColor,
                                lerp(currentColor, gradientColors[(endIndex + 1) % (gradientColors.size - 1)], 0.5f)
                            )
                        )
                    )
                    .clickable { onNavigateToPlayer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Album, 
                    contentDescription = if (appLanguage == AppLanguage.CHINESE) "返回播放" else "Now Playing",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 仅竖屏模式显示悬浮导入按钮
        if (!isLandscapeMode) {
            // 悬浮导入按钮
            FloatingActionButton(
                onClick = onBatchScanClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .navigationBarsPadding(), // 适配底部导航栏
                shape = RoundedCornerShape(16.dp), // 圆角带 R 角的小正方形
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.DriveFolderUpload, if (appLanguage == AppLanguage.CHINESE) "导入本地文件" else "Import Local Files")
            }
        }
    }
}

/**
 * 横屏模式下的右侧内容区域
 * 包含标题栏和媒体列表
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.LandscapeContentArea(
    historyList: List<MediaHistory>,
    currentTheme: AppTheme,
    customBackgroundUri: String?,
    customBackgroundAlpha: Float,
    displayMode: DisplayMode,
    audioDisplayMode: AudioDisplayMode,
    currentMediaType: MediaType,
    isHiddenMode: Boolean,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    appLanguage: AppLanguage,
    isAudioPlaying: Boolean,
    currentPlayingAudioId: String?,
    currentPlayingTrackIndex: Int,
    searchQuery: String,
    audioSearchMatchingTracks: Map<String, List<Int>>,
    onAudioDisplayModeChange: (AudioDisplayMode) -> Unit,
    onBatchScanClick: () -> Unit,
    onToggleMultiSelectMode: () -> Unit,
    onHistoryItemClick: (MediaHistory) -> Unit,
    onHistoryItemLongClick: (MediaHistory) -> Unit,
    onAudioTrackClick: (AudioHistory, Int) -> Unit,
    onAudioTrackLongClick: (AudioHistory, Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isHiddenMode) {
                            if (appLanguage == AppLanguage.CHINESE) "绝密档案" else "Secret Files"
                        } else {
                            when (currentMediaType) {
                                MediaType.COMIC -> if (appLanguage == AppLanguage.CHINESE) "漫画架" else "Shelf"
                                MediaType.NOVEL -> if (appLanguage == AppLanguage.CHINESE) "小说架" else "Shelf"
                                MediaType.AUDIO -> if (appLanguage == AppLanguage.CHINESE) "音频架" else "Shelf"
                            }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    val displayCount = if (currentMediaType == MediaType.AUDIO && audioDisplayMode == AudioDisplayMode.SINGLES) {
                        historyList.filterIsInstance<AudioHistory>().sumOf { it.tracks.size }
                    } else {
                        historyList.size
                    }
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("$displayCount", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentMediaType == MediaType.AUDIO) {
                        IconButton(onClick = {
                            val newMode = if (audioDisplayMode == AudioDisplayMode.ALBUMS) 
                                AudioDisplayMode.SINGLES else AudioDisplayMode.ALBUMS
                            onAudioDisplayModeChange(newMode)
                        }) {
                            Icon(
                                audioDisplayMode.icon,
                                contentDescription = audioDisplayMode.label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onToggleMultiSelectMode) {
                        Icon(
                            if (isMultiSelectMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                            contentDescription = if (isMultiSelectMode) "退出多选" else "多选",
                            tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 内容区域
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(currentMediaType.icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isHiddenMode) {
                                if (appLanguage == AppLanguage.CHINESE) "这里空空如也..." else "Nothing here..."
                            } else {
                                if (appLanguage == AppLanguage.CHINESE) "暂无内容，请点击下方按钮扫描" else "No content. Tap the button below to scan."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f
                
                when (currentMediaType) {
                    MediaType.COMIC -> {
                        ComicTabContent(
                            comicList = historyList.filterIsInstance<ComicHistory>(),
                            currentTheme = currentTheme,
                            displayMode = displayMode,
                            cardAlpha = cardAlpha,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            onHistoryItemClick = { onHistoryItemClick(it) },
                            onHistoryItemLongClick = { onHistoryItemLongClick(it) }
                        )
                    }
                    MediaType.NOVEL -> {
                        NovelTabContent(
                            novelList = historyList.filterIsInstance<NovelHistory>(),
                            currentTheme = currentTheme,
                            displayMode = displayMode,
                            cardAlpha = cardAlpha,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            onHistoryItemClick = { onHistoryItemClick(it) },
                            onHistoryItemLongClick = { onHistoryItemLongClick(it) }
                        )
                    }
                    MediaType.AUDIO -> {
                        AudioTabContent(
                            audioList = historyList.filterIsInstance<AudioHistory>(),
                            currentTheme = currentTheme,
                            displayMode = displayMode,
                            audioDisplayMode = audioDisplayMode,
                            cardAlpha = cardAlpha,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            isAudioPlaying = isAudioPlaying,
                            currentPlayingAudioId = currentPlayingAudioId,
                            currentPlayingTrackIndex = currentPlayingTrackIndex,
                            searchQuery = searchQuery,
                            audioSearchMatchingTracks = audioSearchMatchingTracks,
                            onHistoryItemClick = { onHistoryItemClick(it) },
                            onHistoryItemLongClick = { onHistoryItemLongClick(it) },
                            onAudioTrackClick = onAudioTrackClick,
                            onAudioTrackLongClick = onAudioTrackLongClick
                        )
                    }
                }
            }
        }

        // 悬浮扫描按钮 (仅在右侧内容区域内显示)
        FloatingActionButton(
            onClick = onBatchScanClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Rounded.DriveFolderUpload, if (appLanguage == AppLanguage.CHINESE) "导入本地文件" else "Import Local Files")
        }
    }
}
