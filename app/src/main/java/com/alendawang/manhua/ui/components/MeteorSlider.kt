package com.alendawang.manhua.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 流星样式进度条
 * 
 * 特点：
 * 1. 流动渐变色 - 颜色在多种颜色间缓慢流动
 * 2. 发光头部 - 进度头部有光晕效果
 * 3. 粒子尾迹 - 覆盖已播放区域
 */
@Composable
fun MeteorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    trackHeight: Dp = 6.dp,
    particleCount: Int = 30
) {
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    
    // 流动颜色列表
    val colorPalette = listOf(
        Color(0xFF00D4FF), // 青蓝
        Color(0xFF00FFD4), // 青绿
        Color(0xFFFF00D4), // 粉紫
        Color(0xFFD400FF), // 紫色
        Color(0xFF00D4FF)  // 回到青蓝形成循环
    )
    
    // 颜色流动动画 - 慢速循环
    val infiniteTransition = rememberInfiniteTransition(label = "meteorEffects")
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),  // 8秒完成一个颜色循环
            repeatMode = RepeatMode.Restart
        ),
        label = "colorPhase"
    )
    
    // 粒子动画循环
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )
    
    // 光晕呼吸效果
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    // 生成粒子
    val particles = remember { 
        List(particleCount) { index ->
            MeteorParticle(
                positionRatio = index.toFloat() / particleCount,
                size = Random.nextFloat() * 2.5f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.3f,
                speed = Random.nextFloat() * 0.3f + 0.2f,
                offsetY = Random.nextFloat() * 2f - 1f
            )
        }
    }
    
    var sliderWidth by remember { mutableFloatStateOf(0f) }
    
    // 拖动时使用本地状态，避免频繁调用外部回调
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    var pendingSeek by remember { mutableStateOf(false) }  // 标记是否有待处理的 seek
    
    // 同步外部值变化
    LaunchedEffect(value) {
        // 当外部值更新且接近 dragValue 时，说明 seek 完成
        if (pendingSeek && kotlin.math.abs(value - dragValue) < (valueRange.endInclusive - valueRange.start) * 0.05f) {
            pendingSeek = false
        }
        if (!isDragging && !pendingSeek) {
            dragValue = value
        }
    }
    
    // 显示值：拖动中或等待 seek 完成时用本地值
    val displayValue = if (isDragging || pendingSeek) dragValue else value
    val displayNormalized = ((displayValue - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight * 3.5f)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / sliderWidth)
                        .coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start) + valueRange.start
                    onValueChange(newValue)
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        pendingSeek = true  // 标记等待 seek 完成
                        onValueChange(dragValue)  // 拖动结束时才真正 seek
                    },
                    onDragCancel = {
                        isDragging = false
                        dragValue = value
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newValue = (change.position.x / sliderWidth)
                            .coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start) + valueRange.start
                        dragValue = newValue  // 只更新本地状态
                    }
                )
            }
    ) {
        sliderWidth = size.width
        val trackHeightPx = trackHeight.toPx()
        val centerY = size.height / 2
        val progressWidth = displayNormalized * size.width
        
        // 计算当前流动的颜色
        val flowingColors = getFlowingColors(colorPalette, colorPhase)
        
        // 1. 背景轨道
        drawRoundRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(0f, centerY - trackHeightPx / 2),
            size = Size(size.width, trackHeightPx),
            cornerRadius = CornerRadius(trackHeightPx / 2)
        )
        
        if (progressWidth > 0) {
            // 2. 流动渐变拖尾
            val tailGradient = Brush.horizontalGradient(
                colors = listOf(
                    flowingColors[0].copy(alpha = 0.1f),
                    flowingColors[1].copy(alpha = 0.3f),
                    flowingColors[2].copy(alpha = 0.5f),
                    flowingColors[3].copy(alpha = 0.7f),
                    flowingColors[4]
                ),
                startX = 0f,
                endX = progressWidth
            )
            drawRoundRect(
                brush = tailGradient,
                topLeft = Offset(0f, centerY - trackHeightPx / 2),
                size = Size(progressWidth, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2)
            )
            
            // 3. 粒子尾迹
            drawParticles(
                particles = particles,
                phase = particlePhase,
                progressWidth = progressWidth,
                centerY = centerY,
                trackHeight = trackHeightPx,
                flowingColors = flowingColors
            )
            
            // 4. 发光头部 - 外层光晕 (范围缩小到2/3)
            val headX = progressWidth
            val currentColor = flowingColors[4]  // 使用最亮的颜色
            val glowRadius = trackHeightPx * 2f * glowPulse  // 从3f改为2f
            drawCircle(
                color = Color.White.copy(alpha = 0.25f * glowPulse),
                radius = glowRadius,
                center = Offset(headX, centerY)
            )
            
            // 5. 发光头部 - 中层
            drawCircle(
                color = currentColor.copy(alpha = 0.6f),
                radius = trackHeightPx * 1.3f,
                center = Offset(headX, centerY)
            )
            
            // 6. 发光头部 - 核心亮点
            drawCircle(
                color = Color.White,
                radius = trackHeightPx * 0.65f,
                center = Offset(headX, centerY)
            )
        }
    }
}

/**
 * 绘制粒子尾迹
 */
private fun DrawScope.drawParticles(
    particles: List<MeteorParticle>,
    phase: Float,
    progressWidth: Float,
    centerY: Float,
    trackHeight: Float,
    flowingColors: List<Color>
) {
    particles.forEach { particle ->
        val baseX = particle.positionRatio * progressWidth
        
        if (baseX > 0 && baseX < progressWidth) {
            val densityFactor = (baseX / progressWidth)
            
            // 添加动态波动
            val wavePhase = (phase + particle.speed) % 1f
            val waveX = sin(wavePhase * 2 * Math.PI.toFloat()) * trackHeight * 0.3f
            val waveY = particle.offsetY * trackHeight * 1.2f + 
                       cos(wavePhase * 3 * Math.PI.toFloat()) * trackHeight * 0.5f
            
            val particleX = baseX + waveX
            val particleY = centerY + waveY
            
            // 闪烁效果
            val flickerAlpha = particle.alpha * (0.4f + 0.6f * sin(wavePhase * 4 * Math.PI.toFloat()).coerceIn(0f, 1f))
            
            // 根据位置选择颜色
            val colorIndex = ((baseX / progressWidth) * (flowingColors.size - 1)).toInt()
                .coerceIn(0, flowingColors.size - 1)
            val particleColor = flowingColors[colorIndex]
            
            // 粒子大小
            val adjustedSize = particle.size * (0.7f + 0.6f * densityFactor)
            
            // 粒子核心
            drawCircle(
                color = Color.White.copy(alpha = flickerAlpha * densityFactor),
                radius = adjustedSize,
                center = Offset(particleX, particleY)
            )
            // 粒子光晕
            drawCircle(
                color = particleColor.copy(alpha = flickerAlpha * 0.4f * densityFactor),
                radius = adjustedSize * 2.5f,
                center = Offset(particleX, particleY)
            )
        }
    }
}

/**
 * 粒子数据类
 */
private data class MeteorParticle(
    val positionRatio: Float,
    val size: Float,
    val alpha: Float,
    val speed: Float,
    val offsetY: Float
)

/**
 * 根据相位计算当前流动的颜色组
 */
private fun getFlowingColors(palette: List<Color>, phase: Float): List<Color> {
    val shiftedColors = mutableListOf<Color>()
    for (i in 0 until 5) {
        val colorIndex = (phase * palette.size + i) % palette.size
        val lowerIndex = colorIndex.toInt() % palette.size
        val upperIndex = (lowerIndex + 1) % palette.size
        val fraction = colorIndex - colorIndex.toInt()
        
        // 在两个颜色间插值
        val color = lerpColor(palette[lowerIndex], palette[upperIndex], fraction)
        shiftedColors.add(color)
    }
    return shiftedColors
}

/**
 * 颜色插值
 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

