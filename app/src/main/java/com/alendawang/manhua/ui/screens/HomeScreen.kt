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
                    val comicList = historyList.filterIsInstance<ComicHistory>()
                    // 优化 1: 使用 stable key 并确保缓存稳定
                    val cachedComicList = remember(comicList.hashCode()) { comicList }
                    val cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f
                    
                    // 优化 2: 检测滚动状态
                    val lazyGridState = rememberLazyGridState()
                    val isScrolling = lazyGridState.isScrollInProgress
                    
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cachedComicList, key = { it.id }) { history ->
                            val isSelected = selectedItems.contains(history.id)
                            Box {
                                if (displayMode == DisplayMode.ListView)
                                    ComicHistoryItemListCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha, isScrolling)
                                else
                                    ComicHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha, isScrolling)
                                // 多选模式下的选中指示器
                                if (isMultiSelectMode) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
                MediaType.NOVEL -> {
                    val novelList = historyList.filterIsInstance<NovelHistory>()
                    // 优化 1: 使用 stable key 并确保缓存稳定
                    val cachedNovelList = remember(novelList.hashCode()) { novelList }
                    val cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f
                    
                    val lazyGridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cachedNovelList, key = { it.id }) { history ->
                            val isSelected = selectedItems.contains(history.id)
                            Box {
                                if (displayMode == DisplayMode.ListView)
                                    NovelHistoryItemCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                else
                                    NovelHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                // 多选模式下的选中指示器
                                if (isMultiSelectMode) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
                MediaType.AUDIO -> {
                    val audioList = historyList.filterIsInstance<AudioHistory>()
                    val cachedAudioList = remember(audioList.hashCode()) { audioList }
                    val cardAlpha = if (customBackgroundUri != null) customBackgroundAlpha.coerceAtMost(0.7f) else 1f
                    
                    val audioGridState = rememberLazyGridState()
                    
                    // 判断是否在搜索模式：搜索时显示匹配的单曲
                    val isSearching = searchQuery.isNotBlank() && audioSearchMatchingTracks.isNotEmpty()
                    
                    // 搜索模式或单曲模式：显示单曲列表
                    if (isSearching || audioDisplayMode == AudioDisplayMode.SINGLES) {
                        // 创建显示的曲目列表
                        val tracksToShow = remember(cachedAudioList.hashCode(), searchQuery, audioSearchMatchingTracks) {
                            if (isSearching) {
                                // 搜索时：只显示匹配的歌曲
                                cachedAudioList.flatMap { audio ->
                                    val matchingIndices = audioSearchMatchingTracks[audio.id] ?: emptyList()
                                    matchingIndices.mapNotNull { index ->
                                        audio.tracks.getOrNull(index)?.let { track ->
                                            Triple(audio, index, track)
                                        }
                                    }
                                }
                            } else {
                                // 单曲模式：显示所有歌曲
                                cachedAudioList.flatMap { audio ->
                                    audio.tracks.mapIndexed { index, track -> Triple(audio, index, track) }
                                }
                            }
                        }
                        
                        LazyVerticalGrid(
                            state = audioGridState,
                            columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                            contentPadding = PaddingValues(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(tracksToShow, key = { "${it.first.id}_${it.second}" }) { (audio, trackIndex, track) ->
                                val singleTrackAudio = AudioHistory(
                                    id = track.uriString,
                                    name = track.name,
                                    uriString = audio.uriString,
                                    coverUriString = audio.coverUriString,
                                    timestamp = audio.timestamp,
                                    tracks = listOf(track),
                                    lastPlayedIndex = 0,
                                    lastPlayedPosition = 0,
                                    isFavorite = audio.isFavorite,
                                    isNsfw = audio.isNsfw
                                )
                                val isSelected = selectedItems.contains(audio.id)
                                Box {
                                    if (displayMode == DisplayMode.ListView)
                                        AudioHistoryItemCard(
                                            singleTrackAudio,
                                            currentTheme,
                                            onClick = {
                                                // 点击行为根据显示模式决定
                                                onAudioTrackClick(audio, trackIndex)
                                            },
                                            onLongClick = { onHistoryItemLongClick(audio) },
                                            cardAlpha
                                        )
                                    else
                                        AudioHistoryItemGridCard(
                                            singleTrackAudio,
                                            currentTheme,
                                            onClick = {
                                                // 点击行为根据显示模式决定
                                                onAudioTrackClick(audio, trackIndex)
                                            },
                                            onLongClick = { onHistoryItemLongClick(audio) },
                                            cardAlpha
                                        )
                                    if (isMultiSelectMode) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                                .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    // Playing indicator
                                    val isCurrentlyPlaying = isAudioPlaying && 
                                        currentPlayingAudioId == audio.id && 
                                        currentPlayingTrackIndex == trackIndex
                                    if (isCurrentlyPlaying && !isMultiSelectMode) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                                .size(28.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BouncingMusicNote(modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    } else {
                        // 专辑模式（非搜索）：显示专辑列表
                        LazyVerticalGrid(
                            state = audioGridState,
                            columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                            contentPadding = PaddingValues(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(cachedAudioList, key = { it.id }) { history ->
                                val isSelected = selectedItems.contains(history.id)
                                Box {
                                    if (displayMode == DisplayMode.ListView)
                                        AudioHistoryItemCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                    else
                                        AudioHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                    if (isMultiSelectMode) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                                .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
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
                val lazyGridState = rememberLazyGridState()
                
                when (currentMediaType) {
                    MediaType.COMIC -> {
                        val comicList = historyList.filterIsInstance<ComicHistory>()
                        val cachedComicList = remember(comicList.hashCode()) { comicList }
                        val isScrolling = lazyGridState.isScrollInProgress
                        
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                            contentPadding = PaddingValues(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(cachedComicList, key = { it.id }) { history ->
                                val isSelected = selectedItems.contains(history.id)
                                Box {
                                    if (displayMode == DisplayMode.ListView)
                                        ComicHistoryItemListCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha, isScrolling)
                                    else
                                        ComicHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha, isScrolling)
                                    if (isMultiSelectMode) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                                .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                    MediaType.NOVEL -> {
                        val novelList = historyList.filterIsInstance<NovelHistory>()
                        val cachedNovelList = remember(novelList.hashCode()) { novelList }
                        
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                            contentPadding = PaddingValues(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(cachedNovelList, key = { it.id }) { history ->
                                val isSelected = selectedItems.contains(history.id)
                                Box {
                                    if (displayMode == DisplayMode.ListView)
                                        NovelHistoryItemCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                    else
                                        NovelHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                    if (isMultiSelectMode) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                                .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                    MediaType.AUDIO -> {
                        val audioList = historyList.filterIsInstance<AudioHistory>()
                        val cachedAudioList = remember(audioList.hashCode()) { audioList }
                        val isSearching = searchQuery.isNotBlank() && audioSearchMatchingTracks.isNotEmpty()
                        
                        if (isSearching || audioDisplayMode == AudioDisplayMode.SINGLES) {
                            val tracksToShow = remember(cachedAudioList.hashCode(), searchQuery, audioSearchMatchingTracks) {
                                if (isSearching) {
                                    cachedAudioList.flatMap { audio ->
                                        val matchingIndices = audioSearchMatchingTracks[audio.id] ?: emptyList()
                                        matchingIndices.mapNotNull { index ->
                                            audio.tracks.getOrNull(index)?.let { track ->
                                                Triple(audio, index, track)
                                            }
                                        }
                                    }
                                } else {
                                    cachedAudioList.flatMap { audio ->
                                        audio.tracks.mapIndexed { index, track -> Triple(audio, index, track) }
                                    }
                                }
                            }
                            
                            LazyVerticalGrid(
                                state = lazyGridState,
                                columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                                contentPadding = PaddingValues(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(tracksToShow, key = { "${it.first.id}_${it.second}" }) { (audio, trackIndex, track) ->
                                    val singleTrackAudio = AudioHistory(
                                        id = track.uriString,
                                        name = track.name,
                                        uriString = audio.uriString,
                                        coverUriString = audio.coverUriString,
                                        timestamp = audio.timestamp,
                                        tracks = listOf(track),
                                        lastPlayedIndex = 0,
                                        lastPlayedPosition = 0,
                                        isFavorite = audio.isFavorite,
                                        isNsfw = audio.isNsfw
                                    )
                                    val isSelected = selectedItems.contains(audio.id)
                                    Box {
                                        if (displayMode == DisplayMode.ListView)
                                            AudioHistoryItemCard(singleTrackAudio, currentTheme, { onAudioTrackClick(audio, trackIndex) }, { onHistoryItemLongClick(audio) }, cardAlpha)
                                        else
                                            AudioHistoryItemGridCard(singleTrackAudio, currentTheme, { onAudioTrackClick(audio, trackIndex) }, { onHistoryItemLongClick(audio) }, cardAlpha)
                                        if (isMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                    .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(24.dp)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                                    .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        val isCurrentlyPlaying = isAudioPlaying && currentPlayingAudioId == audio.id && currentPlayingTrackIndex == trackIndex
                                        if (isCurrentlyPlaying && !isMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(8.dp)
                                                    .size(28.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                BouncingMusicNote(modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                                    Spacer(Modifier.height(32.dp))
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                state = lazyGridState,
                                columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1) else GridCells.Fixed(displayMode.columnCount),
                                contentPadding = PaddingValues(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(cachedAudioList, key = { it.id }) { history ->
                                    val isSelected = selectedItems.contains(history.id)
                                    Box {
                                        if (displayMode == DisplayMode.ListView)
                                            AudioHistoryItemCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                        else
                                            AudioHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha)
                                        if (isMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                    .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(24.dp)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
                                                    .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                                    Spacer(Modifier.height(32.dp))
                                }
                            }
                        }
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
