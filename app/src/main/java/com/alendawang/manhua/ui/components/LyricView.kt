package com.alendawang.manhua.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alendawang.manhua.utils.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentTime: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit = {},
    initialLineIndex: Int = 0,  // 新增：初始显示的歌词行索引
    fontSize: Float = 18f,      // 歌词字体大小
    textColor: Color = Color.White, // 歌词颜色
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismiss() },
                    onLongClick = { }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        return
    }

    // 使用 initialLineIndex 初始化列表位置，直接从当前播放的歌词行开始显示
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialLineIndex.coerceIn(0, (lyrics.size - 1).coerceAtLeast(0))
    )
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    // 长按选中的歌词行索引
    var longPressedIndex by remember { mutableStateOf(-1) }
    
    // 用户是否正在拖拽（参考MusicFree: dragShownRef）
    var isDragging by remember { mutableStateOf(false) }
    // 上次拖拽结束的时间
    var lastDragEndTime by remember { mutableStateOf(0L) }
    
    // 容器高度
    var containerHeight by remember { mutableIntStateOf(0) }
    
    // 估计的每行歌词高度（包含间距）
    val itemHeight = with(density) { 56.dp.toPx().toInt() } // 大约36sp字体 + 20dp间距
    
    // 找到当前播放的歌词行索引
    val currentLineIndex by remember(currentTime, lyrics) {
        derivedStateOf {
            val index = lyrics.indexOfLast { it.startTime <= currentTime }
            if (index >= 0) index else 0
        }
    }

    // 检测用户拖拽/滚动
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isDragging = true
        } else if (isDragging) {
            lastDragEndTime = System.currentTimeMillis()
        }
    }
    
    // 3秒后恢复自动滚动（参考MusicFree: setDraggingIndex延迟2000ms）
    LaunchedEffect(lastDragEndTime) {
        if (isDragging && lastDragEndTime > 0) {
            delay(3000)
            if (System.currentTimeMillis() - lastDragEndTime >= 2900) {
                isDragging = false
            }
        }
    }

    // 顶部和底部padding - 参考MusicFree: empty style的paddingTop: "70%"
    // 使用50%让歌词可以滚动到屏幕中央
    val verticalPaddingDp = with(density) { (containerHeight / 2).toDp() }
    
    // 自动滚动到当前歌词 - 参考MusicFree的scrollToIndex逻辑
    // 使用scrollToItem而非animateScrollToItem，让滚动立即完成
    // 这样歌词切换时不会因为动画太慢而跟不上播放速度
    LaunchedEffect(currentLineIndex, isDragging, isPlaying) {
        if (!isDragging && isPlaying && containerHeight > 0) {
            // 立即滚动到当前歌词，无动画延迟
            listState.scrollToItem(
                index = currentLineIndex,
                scrollOffset = 0
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // 获取容器高度
        LaunchedEffect(maxHeight) {
            containerHeight = with(density) { maxHeight.toPx().toInt() }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 参考MusicFree: ListHeaderComponent和ListFooterComponent都是空白组件
            // 使用对称padding让任何歌词都可以滚动到中央
            contentPadding = PaddingValues(vertical = verticalPaddingDp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrentLine = index == currentLineIndex
                val isLongPressed = index == longPressedIndex
                
                // 平滑的透明度动画
                val alpha by animateFloatAsState(
                    targetValue = if (isCurrentLine) 1f else 0.5f,
                    animationSpec = tween(durationMillis = 300),
                    label = "lyricAlpha"
                )
                
                // 固定字体大小，使用传入的参数
                // val fontSize is now a parameter

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (longPressedIndex >= 0) {
                                    longPressedIndex = -1
                                } else {
                                    onDismiss()
                                }
                            },
                            onLongClick = {
                                longPressedIndex = index
                            }
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fontSize.sp,
                            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alpha(alpha)
                            .padding(horizontal = 8.dp)
                    )
                    
                    AnimatedVisibility(
                        visible = isLongPressed,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = {
                                onSeek(line.startTime)
                                longPressedIndex = -1
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "跳转到此处",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

