package com.alendawang.manhua.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.alendawang.manhua.R
import com.alendawang.manhua.model.*

/**
 * 继续阅读/收听的项目数据（跨媒体类型统一包装）
 */
data class ContinueReadingItem(
    val id: String,
    val name: String,
    val coverUri: String?,
    val mediaType: MediaType,
    val progressPercent: Int,
    val progressLabel: String,
    val timestamp: Long,
    val trackIndex: Int = 0, // 音频用：曲目索引
    // 用于导航的原始数据
    val comicHistory: ComicHistory? = null,
    val novelHistory: NovelHistory? = null,
    val audioHistory: AudioHistory? = null
)

/**
 * 最近播放的音频曲目记录
 */
data class RecentAudioPlay(
    val audioId: String,
    val trackIndex: Int,
    val timestamp: Long
)

/**
 * 从三种媒体列表中收集有阅读进度的项目，按最近阅读时间排序
 */
fun collectContinueReadingItems(
    comics: List<ComicHistory>,
    novels: List<NovelHistory>,
    audios: List<AudioHistory>,
    recentAudioPlays: List<RecentAudioPlay>,
    isHiddenMode: Boolean,
    maxPerType: Int = 3
): List<ContinueReadingItem> {
    val items = mutableListOf<ContinueReadingItem>()

    // 漫画：有进度且未读完，每类最多 maxPerType 个（按最近阅读排序）
    comics.filter { comic ->
        (isHiddenMode || !comic.isNsfw) &&
        comic.cachedTotalPages > 0 &&
        comic.cachedCurrentPage > 0 &&
        comic.cachedCurrentPage < comic.cachedTotalPages
    }.sortedByDescending { it.timestamp }
     .take(maxPerType)
     .forEach { comic ->
        val percent = ((comic.cachedCurrentPage.toFloat() / comic.cachedTotalPages) * 100).toInt()
        items.add(ContinueReadingItem(
            id = comic.id,
            name = comic.name,
            coverUri = comic.coverUriString,
            mediaType = MediaType.COMIC,
            progressPercent = percent,
            progressLabel = "${comic.cachedCurrentPage}/${comic.cachedTotalPages}",
            timestamp = comic.timestamp,
            comicHistory = comic
        ))
    }

    // 小说：有进度且未读完，每类最多 maxPerType 个
    novels.filter { novel ->
        (isHiddenMode || !novel.isNsfw) &&
        novel.chapters.isNotEmpty() &&
        novel.lastReadChapterIndex > 0 &&
        novel.lastReadChapterIndex < novel.chapters.size
    }.sortedByDescending { it.timestamp }
     .take(maxPerType)
     .forEach { novel ->
        val percent = ((novel.lastReadChapterIndex.toFloat() / novel.chapters.size) * 100).toInt()
        items.add(ContinueReadingItem(
            id = novel.id,
            name = novel.name,
            coverUri = novel.coverUriString,
            mediaType = MediaType.NOVEL,
            progressPercent = percent,
            progressLabel = "${novel.lastReadChapterIndex}/${novel.chapters.size}章",
            timestamp = novel.timestamp,
            novelHistory = novel
        ))
    }

    // 音频：基于最近播放记录，支持同专辑多首歌各自显示
    // 每条记录对应一首具体曲目，按播放时间倒序取前 maxPerType 个
    val audioMap = audios.associateBy { it.id }
    recentAudioPlays
        .sortedByDescending { it.timestamp }
        .distinctBy { "${it.audioId}_${it.trackIndex}" } // 去重：同一曲目只保留最新的
        .take(maxPerType)
        .forEach { play ->
            val audio = audioMap[play.audioId] ?: return@forEach
            if (!isHiddenMode && audio.isNsfw) return@forEach
            val track = audio.tracks.getOrNull(play.trackIndex) ?: return@forEach
            items.add(ContinueReadingItem(
                id = "${audio.id}_${play.trackIndex}", // 唯一 ID：专辑ID + 曲目索引
                name = track.name,
                coverUri = audio.coverUriString,
                mediaType = MediaType.AUDIO,
                progressPercent = -1,
                progressLabel = "",
                timestamp = play.timestamp,
                trackIndex = play.trackIndex,
                audioHistory = audio
            ))
        }

    // 按最近时间排序
    return items.sortedByDescending { it.timestamp }
}

/**
 * 「继续阅读」可折叠横向卡片栏
 * - 默认折叠，点击标题栏展开/收起
 * - 常驻于模块切换区域之外，不随 AnimatedContent 滑动
 */
@Composable
fun ContinueReadingSection(
    items: List<ContinueReadingItem>,
    appLanguage: AppLanguage,
    onItemClick: (ContinueReadingItem) -> Unit
) {
    if (items.isEmpty()) return

    // 使用 rememberSaveable 保留折叠状态（旋转/配置变更后保持）
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    // 箭头旋转动画
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "arrowRotation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题栏（可点击展开/收起）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (appLanguage == AppLanguage.CHINESE) "继续阅读" else "Continue",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(6.dp))
                // 数量标签
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${items.size}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                }
            }
            // 展开/收起箭头
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(arrowRotation)
            )
        }

        // 可折叠的内容区域
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(tween(150))
        ) {
            Column {
                Spacer(Modifier.height(4.dp))
                // 横向卡片列表
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        ContinueReadingCard(item = item, onClick = { onItemClick(item) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 单张「继续阅读」卡片
 */
@Composable
private fun ContinueReadingCard(
    item: ContinueReadingItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // 按下时弹性缩放
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )

    // 音频使用默认封面 fallback，和 Cards.kt 保持一致
    val coverData: Any? = if (item.mediaType == MediaType.AUDIO) {
        item.coverUri ?: R.drawable.default_audio_cover
    } else {
        item.coverUri
    }
    val imageRequest = remember(item.id, coverData) {
        ImageRequest.Builder(context)
            .data(coverData)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${item.id}_continue")
            .build()
    }

    // 进度条颜色：根据媒体类型
    val progressColor = when (item.mediaType) {
        MediaType.COMIC -> Color(0xFFFF6B6B)  // 红色
        MediaType.NOVEL -> Color(0xFF4ECDC4)  // 青色
        MediaType.AUDIO -> Color(0xFFFFE66D)  // 黄色
    }

    val mediaIcon = when (item.mediaType) {
        MediaType.COMIC -> Icons.AutoMirrored.Rounded.MenuBook
        MediaType.NOVEL -> Icons.Rounded.Menu
        MediaType.AUDIO -> Icons.Rounded.Headphones
    }

    Card(
        modifier = Modifier
            .width(130.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                onClick = onClick,
                onClickLabel = item.name
            ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // 封面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(mediaIcon)
                )

                // 底部渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // 媒体类型标签
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            progressColor.copy(alpha = 0.9f),
                            RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Icon(
                        mediaIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }

                // 进度百分比（音频不显示）
                if (item.progressPercent >= 0) {
                    Text(
                        text = "${item.progressPercent}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    )
                }
            }

            // 底部信息区
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )

                // 进度条（音频不显示）
                if (item.progressPercent >= 0) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (item.progressLabel.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

