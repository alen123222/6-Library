package com.alendawang.manhua.ui.screens

import android.widget.Toast

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.*
import com.alendawang.manhua.R
import com.alendawang.manhua.model.AppLanguage
import com.alendawang.manhua.utils.isLandscape
import com.alendawang.manhua.utils.loadLabEnabled
import com.alendawang.manhua.utils.saveLabEnabled
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.Calendar
import java.util.concurrent.TimeUnit

import androidx.compose.ui.composed

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput

// --- 高级交互：带弹性阻尼的零延迟点击动画 ---
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bouncyScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                val up = waitForUpOrCancellation()
                isPressed = false
                if (up != null) {
                    currentOnClick()
                }
            }
        }
}

@Composable
fun DynamicGreetingHeader(
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    onHeaderClick: () -> Unit = {}
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    data class GreetingData(val zh: String, val en: String, val lottieUrl: String, val icon: ImageVector)
    val data = when (hour) {
        in 6..11 -> GreetingData("早。开启新的一天", "Good morning, start a new day", "https://lottie.host/8e3a241e-b855-4e76-80db-3305a41be749/X8y1g6QJkV.json", Icons.Rounded.WbSunny)
        in 12..17 -> GreetingData("下午好。睡个午觉吗？", "Good afternoon, maybe take a nap?", "https://lottie.host/8e3a241e-b855-4e76-80db-3305a41be749/X8y1g6QJkV.json", Icons.Rounded.WbCloudy)
        in 18..22 -> GreetingData("晚上好。有好好吃饭吗？", "Good evening, had a nice dinner?", "https://lottie.host/81a8b13d-5197-4b71-9f26-06830737190f/B85mR31y1D.json", Icons.Rounded.NightsStay)
        else -> GreetingData("夜深了，注意休息哦", "It's late, get some rest", "https://lottie.host/81a8b13d-5197-4b71-9f26-06830737190f/B85mR31y1D.json", Icons.Rounded.NightsStay)
    }
    val greeting = if (appLanguage == AppLanguage.CHINESE) data.zh else data.en
    val subtitle = if (appLanguage == AppLanguage.CHINESE) "点点撒花 🎉" else "Tap for a confetti surprise 🎉"

    val composition by rememberLottieComposition(LottieCompositionSpec.Url(data.lottieUrl))
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onHeaderClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(60.dp)
                )
            } else {
                Icon(
                    data.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }
}

@Composable
fun WelcomeBanner(appLanguage: AppLanguage) {
    data class BannerItem(val imageRes: Int, val titleZh: String, val titleEn: String, val subZh: String, val subEn: String)
    val banners = listOf(
        BannerItem(R.drawable.welcome_banner_bg, "欢迎回来", "Welcome Back", "你的漫画、小说与音频，一站式沉浸体验", "Your comics, novels & audio – all in one place"),
        BannerItem(R.drawable.welcome_banner_bg2, "发现新世界", "Discover New Worlds", "每天都有新故事等你开启", "New stories await you every day"),
        BannerItem(R.drawable.welcome_banner_bg3, "沉浸阅读", "Immersive Reading", "开启无干扰的极致阅读体验", "Enjoy a distraction-free reading experience")
    )
    val pagerState = rememberPagerState(pageCount = { banners.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().height(160.dp).bouncyClickable { },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val item = banners[page]
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = item.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 50f)
                    ))
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                        Icon(Icons.Rounded.AutoStories, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(if (appLanguage == AppLanguage.CHINESE) item.titleZh else item.titleEn, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(Modifier.height(2.dp))
                        Text(if (appLanguage == AppLanguage.CHINESE) item.subZh else item.subEn, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 页面指示器小圆点
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(banners.size) { i ->
                Box(modifier = Modifier.size(if (pagerState.currentPage == i) 8.dp else 6.dp).background(
                    if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape
                ))
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable { /* 仅弹性反馈，无功能 */ },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, null, tint = contentColor.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                Box(modifier = Modifier.size(8.dp).background(contentColor, CircleShape))
            }
            Spacer(Modifier.height(20.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = contentColor.copy(alpha = 0.7f))
        }
    }
}

fun formatTimeMs(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun formatTimeShort(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun FeatureCardsRow(
    appLanguage: AppLanguage,
    totalComicTimeMs: Long,
    novelList: List<com.alendawang.manhua.model.NovelHistory>,
    totalAudioTimeMs: Long
) {
    val totalNovelTimeMs = novelList.sumOf { it.totalReadTimeMs }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = if (appLanguage == AppLanguage.CHINESE) "漫画时长" else "Comics",
            value = formatTimeShort(totalComicTimeMs),
            icon = Icons.Rounded.AutoStories,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = if (appLanguage == AppLanguage.CHINESE) "阅读时长" else "Reading",
            value = formatTimeShort(totalNovelTimeMs),
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = if (appLanguage == AppLanguage.CHINESE) "听歌时长" else "Listening",
            value = formatTimeShort(totalAudioTimeMs),
            icon = Icons.Rounded.LibraryMusic,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

// --- 存储空间使用详情 ---
@Composable
fun StorageBar(label: String, usedMB: Float, totalMB: Float, barColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${usedMB.toInt()} / ${totalMB.toInt()} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (usedMB / totalMB).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun StorageSection(
    appLanguage: AppLanguage,
    comicSizeMB: Float,
    novelSizeMB: Float,
    audioSizeMB: Float
) {
    val totalMB = comicSizeMB + novelSizeMB + audioSizeMB
    val maxBar = totalMB.coerceAtLeast(1f) // 避免除以 0

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (appLanguage == AppLanguage.CHINESE) "存储空间" else "Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StorageBar(if (appLanguage == AppLanguage.CHINESE) "漫画缓存" else "Comics", comicSizeMB, maxBar, MaterialTheme.colorScheme.primary)
                StorageBar(if (appLanguage == AppLanguage.CHINESE) "小说数据" else "Novels", novelSizeMB, maxBar, MaterialTheme.colorScheme.secondary)
                StorageBar(if (appLanguage == AppLanguage.CHINESE) "音频文件" else "Audio", audioSizeMB, maxBar, MaterialTheme.colorScheme.tertiary)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (appLanguage == AppLanguage.CHINESE) "总计占用" else "Total Used", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${"%.1f".format(totalMB)} MB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    iconTint: Color,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆角图标底座
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}


@Composable
fun SettingsSection(
    appLanguage: AppLanguage,
    currentTheme: com.alendawang.manhua.model.AppTheme,
    cacheSizeMB: Float,
    showContinueReading: Boolean,
    onThemeChange: () -> Unit,
    onLanguageChange: () -> Unit,
    onClearCache: () -> Unit,
    onToggleContinueReading: (Boolean) -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (appLanguage == AppLanguage.CHINESE) "系统仪表盘" else "System Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Rounded.Palette,
                    title = if (appLanguage == AppLanguage.CHINESE) "切换主题色彩" else "Theme Color",
                    value = currentTheme.label,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onThemeChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    title = if (appLanguage == AppLanguage.CHINESE) "语言首选项" else "Language",
                    value = if (appLanguage == AppLanguage.CHINESE) "中文" else "English",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onLanguageChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                SettingsRow(
                    icon = Icons.Rounded.MenuBook,
                    title = if (appLanguage == AppLanguage.CHINESE) "主页继续阅读模块" else "Home Continue Reading",
                    value = if (showContinueReading) (if (appLanguage == AppLanguage.CHINESE) "开启" else "On") else (if (appLanguage == AppLanguage.CHINESE) "关闭" else "Off"),
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = { onToggleContinueReading(!showContinueReading) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                SettingsRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = if (appLanguage == AppLanguage.CHINESE) "清理图片缓存" else "Clear Cache",
                    value = "${"%.1f".format(cacheSizeMB)} MB",
                    iconTint = MaterialTheme.colorScheme.error,
                    valueColor = MaterialTheme.colorScheme.error,
                    onClick = { showClearDialog = true }
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if (appLanguage == AppLanguage.CHINESE) "清理缓存" else "Clear Cache") },
            text = { Text(if (appLanguage == AppLanguage.CHINESE) "确定要清理所有图片缓存吗？" else "Are you sure you want to clear all image cache?") },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearCache() }) {
                    Text(if (appLanguage == AppLanguage.CHINESE) "确定" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(if (appLanguage == AppLanguage.CHINESE) "取消" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun UserGuideSection(appLanguage: AppLanguage) {
    val guides = listOf(
        Triple(
            if (appLanguage == AppLanguage.CHINESE) "如何导入本地漫画/小说？" else "How to import local media?",
            if (appLanguage == AppLanguage.CHINESE) "关于各类型媒体资源（尤其是漫画的各种结构）的推荐存放逻辑，请前往 GitHub 仓库阅读详细说明。" else "For detailed instructions and recommended folder structures, please visit our GitHub repository.",
            "https://github.com/alen123222/6-Library"
        ),
        Triple(
            if (appLanguage == AppLanguage.CHINESE) "怎样进行多选和批量管理？" else "How to multi-select?",
            if (appLanguage == AppLanguage.CHINESE) "在主页点击顶部的多选图标即可进入多选模式。此时可以点击卡片进行勾选，通过顶部操作栏可以进行批量收藏、隐藏或删除等操作。" else "Click the multi-select icon at the top of the Home screen to enter multi-select mode. You can then select items and perform batch actions.",
            null
        ),
        Triple(
            if (appLanguage == AppLanguage.CHINESE) "音频可以在后台播放吗？" else "Can audio play in background?",
            if (appLanguage == AppLanguage.CHINESE) "可以。点击音频播放后，您可以直接切换应用或锁屏，音频会自动在后台继续播放，还可以通过系统通知栏控制播放进度和暂停。" else "Yes, start an audio and minimize the app.",
            null
        ),
        Triple(
            if (appLanguage == AppLanguage.CHINESE) "如何快速切换媒体类型？" else "How to switch media types?",
            if (appLanguage == AppLanguage.CHINESE) "在主页可以通过左右滑动屏幕（Swipe）来快速在漫画、小说、音频三大板块之间无缝切换。" else "Swipe left or right on the Home screen to quickly switch modules.",
            null
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (appLanguage == AppLanguage.CHINESE) "知识库 & FAQ" else "Knowledge Base & FAQ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        guides.forEach { (title, desc, url) ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable { expanded = !expanded },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                            if (url != null) {
                                Spacer(Modifier.height(8.dp))
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                Text(
                                    text = if (appLanguage == AppLanguage.CHINESE) "点此跳转" else "Click here",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    ),
                                    modifier = Modifier.clickable { uriHandler.openUri(url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExperimentalSection(appLanguage: AppLanguage, onNavigateToPluginSource: (String) -> Unit) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(loadLabEnabled(context)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (appLanguage == AppLanguage.CHINESE) "实验室" else "Laboratory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    isEnabled = it
                    saveLabEnabled(context, it)
                },
                modifier = Modifier.scale(0.8f)
            )
        }

        AnimatedVisibility(
            visible = isEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Rounded.MenuBook,
                        title = if (appLanguage == AppLanguage.CHINESE) "漫画源扩展" else "Comic Sources",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToPluginSource(if (appLanguage == AppLanguage.CHINESE) "漫画" else "Comic") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                    SettingsRow(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = if (appLanguage == AppLanguage.CHINESE) "小说源扩展" else "Novel Sources",
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { onNavigateToPluginSource(if (appLanguage == AppLanguage.CHINESE) "小说" else "Novel") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                    SettingsRow(
                        icon = Icons.Rounded.LibraryMusic,
                        title = if (appLanguage == AppLanguage.CHINESE) "音频源扩展" else "Audio Sources",
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigateToPluginSource(if (appLanguage == AppLanguage.CHINESE) "音源" else "Audio") }
                    )
                }
            }
        }
    }
}

@Composable
fun LandingScreen(
    paddingValues: PaddingValues,
    appLanguage: AppLanguage,
    customBackgroundUri: String? = null,
    customBackgroundAlpha: Float = 0.4f,
    onShowHelp: () -> Unit,
    // --- 真实数据 ---
    totalComicTimeMs: Long = 0L,
    novelList: List<com.alendawang.manhua.model.NovelHistory> = emptyList(),
    totalAudioTimeMs: Long = 0L,
    // --- 设置回调 ---
    currentTheme: com.alendawang.manhua.model.AppTheme = com.alendawang.manhua.model.AppTheme.InkStyle,
    onThemeChange: () -> Unit = {},
    onLanguageChange: () -> Unit = {},
    onClearCache: () -> Unit = {},
    cacheSizeMB: Float = 0f,
    showContinueReading: Boolean = true,
    onToggleContinueReading: (Boolean) -> Unit = {},
    // --- 存储空间 ---
    comicSizeMB: Float = 0f,
    novelSizeMB: Float = 0f,
    audioSizeMB: Float = 0f,
    onNavigateToPluginSource: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showKonfetti by remember { mutableStateOf(false) }
    val isLandscapeMode = isLandscape()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // 自定义背景
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
        
        // --- 响应式布局：横屏时左右分栏，竖屏时单列 ---
        if (isLandscapeMode) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 左侧栏：个人卡片、入口仪表盘
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
                ) {
                    item { DynamicGreetingHeader(appLanguage = appLanguage, onHeaderClick = { showKonfetti = true }) }
                    item { WelcomeBanner(appLanguage) }
                    item { FeatureCardsRow(appLanguage, totalComicTimeMs, novelList, totalAudioTimeMs) }
                }
                
                // 右侧栏：系统设置与使用指南
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
                ) {
                    item { SettingsSection(appLanguage = appLanguage, currentTheme = currentTheme, cacheSizeMB = cacheSizeMB, showContinueReading = showContinueReading, onThemeChange = onThemeChange, onLanguageChange = onLanguageChange, onClearCache = onClearCache, onToggleContinueReading = onToggleContinueReading) }
                    item { StorageSection(appLanguage = appLanguage, comicSizeMB = comicSizeMB, novelSizeMB = novelSizeMB, audioSizeMB = audioSizeMB) }
                    item { UserGuideSection(appLanguage = appLanguage) }
                    item { ExperimentalSection(appLanguage = appLanguage, onNavigateToPluginSource = onNavigateToPluginSource) }
                }
            }
        } else {
            // 竖屏栏：从上到下排列
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
            ) {
                item { DynamicGreetingHeader(appLanguage = appLanguage, onHeaderClick = { showKonfetti = true }) }
                item { WelcomeBanner(appLanguage) }
                item { FeatureCardsRow(appLanguage, totalComicTimeMs, novelList, totalAudioTimeMs) }
                item { Spacer(Modifier.height(4.dp)) }
                item { SettingsSection(appLanguage = appLanguage, currentTheme = currentTheme, cacheSizeMB = cacheSizeMB, showContinueReading = showContinueReading, onThemeChange = onThemeChange, onLanguageChange = onLanguageChange, onClearCache = onClearCache, onToggleContinueReading = onToggleContinueReading) }
                item { StorageSection(appLanguage = appLanguage, comicSizeMB = comicSizeMB, novelSizeMB = novelSizeMB, audioSizeMB = audioSizeMB) }
                item { Spacer(Modifier.height(4.dp)) }
                item { UserGuideSection(appLanguage = appLanguage) }
                item { Spacer(Modifier.height(4.dp)) }
                item { ExperimentalSection(appLanguage = appLanguage, onNavigateToPluginSource = onNavigateToPluginSource) }
            }
        }
        
        // --- 顶层覆盖 Konfetti 全屏撒花特效 ---
        if (showKonfetti) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3000) // 3 秒后自动关闭撒花状态
                showKonfetti = false
            }
            KonfettiView(
                modifier = Modifier.fillMaxSize().zIndex(100f),
                parties = listOf(
                    Party(
                        speed = 0f,
                        maxSpeed = 30f,
                        damping = 0.9f,
                        spread = 360,
                        colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                        position = Position.Relative(0.5, 0.3)
                    )
                )
            )
        }
    }
}
