package com.alendawang.manhua.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alendawang.manhua.R
import kotlinx.coroutines.isActive

/**
 * 黑胶唱片封面组件
 * 
 * 特点：
 * 1. 黑胶唱片外观 - 黑色圆盘 + 中央封面图 (封面占2/3，黑胶圈占1/3)
 * 2. 旋转动画 - 播放时旋转，暂停时停止，无跳跃
 * 3. 自适应大小 - 可响应式缩放
 */
@Composable
fun VinylRecordView(
    coverData: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    maxSize: Dp = 1000.dp,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // 使用 Animatable 实现平滑的暂停/恢复旋转
    var currentRotation by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 从当前角度继续旋转
            while (isActive) {
                val startTime = withFrameNanos { it }
                var lastFrameTime = startTime
                
                while (isActive && isPlaying) {
                    val frameTime = withFrameNanos { it }
                    val deltaTime = (frameTime - lastFrameTime) / 1_000_000_000f  // 转换为秒
                    lastFrameTime = frameTime
                    
                    // 每秒旋转 45 度 (8秒一圈)
                    currentRotation = (currentRotation + deltaTime * 45f) % 360f
                }
                break
            }
        }
        // 暂停时不做任何事，保持当前角度
    }
    
    // 使用 BoxWithConstraints 测量可用空间，取较小的维度
    BoxWithConstraints(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 计算正方形边长：取可用宽高中较小的那个，同时不超过 maxSize
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        // 按照用户要求：以最小值为基准，留出 20% 空白，剩下 80% 放唱片
        val baseSize = minOf(availableWidth, availableHeight, maxSize)
        val vinylSize = baseSize * 0.8f
        
        Box(
            modifier = Modifier.size(vinylSize),
            contentAlignment = Alignment.Center
        ) {
        // 外层黑胶唱片
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentRotation)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 黑胶唱片纹路背景
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2
                
                // 黑色背景
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = radius,
                    center = center
                )
                
                // 唱片纹路 - 同心圆 (只在外圈1/5区域)
                val grooveCount = 15
                val innerRadius = radius * 0.80f  // 封面边界
                for (i in 1..grooveCount) {
                    val grooveRadius = innerRadius + (radius - innerRadius) * (i.toFloat() / grooveCount)
                    val alpha = if (i % 3 == 0) 0.18f else 0.10f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = grooveRadius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f)
                    )
                }
                
                // 反光效果
                val highlightBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent,
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawCircle(
                    brush = highlightBrush,
                    radius = radius,
                    center = center
                )
            }
            
            // 中央封面图 (占唱片的 4/5)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.80f)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverData)
                        .fallback(R.mipmap.ic_launcher)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        }
    }
}
