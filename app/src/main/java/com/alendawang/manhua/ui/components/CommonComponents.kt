package com.alendawang.manhua.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alendawang.manhua.R
import com.alendawang.manhua.model.ReaderBackgroundColor
import com.alendawang.manhua.model.ReaderConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// --- 垂直快速滚动条 ---
@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    totalItems: Int,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barHeight by remember { mutableIntStateOf(0) }

    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val progress = if (isDragging) dragProgress else {
        if (totalItems > 1) firstVisibleIndex.toFloat() / (totalItems - 1) else 0f
    }.coerceIn(0f, 1f)


    AnimatedVisibility(
        visible = isVisible || isDragging,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(500)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .onGloballyPositioned { barHeight = it.size.height }
                .pointerInput(totalItems) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            // 防止 barHeight 为 0 时除零
                            dragProgress = if (barHeight > 0) (offset.y / barHeight).coerceIn(0f, 1f) else 0f
                            val targetIndex = (dragProgress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                            scope.launch { listState.scrollToItem(targetIndex) }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            val newY = change.position.y
                            // 防止 barHeight 为 0 时除零
                            val newProgress = if (barHeight > 0) (newY / barHeight).coerceIn(0f, 1f) else dragProgress
                            dragProgress = newProgress
                            val targetIndex = (newProgress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                            scope.launch { listState.scrollToItem(targetIndex) }
                        }
                    )
                }
        ) {
            val density = LocalDensity.current
            val thumbHeight = 48.dp
            val thumbHeightPx = with(density) { thumbHeight.toPx() }

            val trackHeightPx = barHeight.toFloat() - thumbHeightPx
            val thumbY = if (trackHeightPx > 0) trackHeightPx * progress else 0f

            // Page number bubble
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = (-32).dp.roundToPx(), y = thumbY.toInt()) }
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(progress * (totalItems - 1)).toInt() + 1}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(x = 0, y = thumbY.toInt()) }
                    .width(8.dp)
                    .height(thumbHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDragging) 0.9f else 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

// --- 小说阅读器背景 ---
@Composable
fun ReaderBackground(config: ReaderConfig, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val customBackgroundUri = config.customBackgroundUriString
        val backgroundImageRes = if (customBackgroundUri == null) {
            when (config.backgroundColor) {
                ReaderBackgroundColor.Parchment -> R.drawable.reader_bg_1
                ReaderBackgroundColor.EyeCare -> R.drawable.reader_bg_2
                else -> null
            }
        } else {
            null
        }
        when {
            customBackgroundUri != null -> {
                AsyncImage(
                    model = customBackgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = config.customBackgroundOverlayAlpha.coerceIn(0f, 1f)))
                )
            }
            backgroundImageRes != null -> {
                Image(
                    painter = painterResource(backgroundImageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(config.backgroundColor.color)
                )
            }
        }
    }
}
