package com.alendawang.manhua.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.AudioTrack
import com.alendawang.manhua.model.ComicChapter
import com.alendawang.manhua.model.ComicHistory
import com.alendawang.manhua.model.NovelChapter
import com.alendawang.manhua.model.NovelHistory
import com.alendawang.manhua.ui.components.VerticalFastScroller
import com.alendawang.manhua.utils.computeComicProgress
import com.alendawang.manhua.utils.scrollToItemCentered
import com.alendawang.manhua.R
import com.alendawang.manhua.model.AppLanguage
import com.alendawang.manhua.utils.AppStrings

// --- 漫画详情页 ---
@Composable
fun ComicDetailScreen(
    paddingValues: PaddingValues,
    comic: ComicHistory,
    overlayAlpha: Float,
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    onChapterClick: (ComicChapter, Int) -> Unit
) {
    val context = LocalContext.current
    val bgImageRequest = remember(comic.id) {
        ImageRequest.Builder(context)
            .data(comic.coverUriString)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val coverImageRequest = remember(comic.id) {
        ImageRequest.Builder(context)
            .data(comic.coverUriString)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    var totalPages by rememberSaveable(comic.id) { mutableIntStateOf(-1) }
    var currentPage by rememberSaveable(comic.id) { mutableIntStateOf(-1) }
    LaunchedEffect(comic.id, comic.lastReadChapterIndex, comic.lastReadIndex) {
        val (total, current) = computeComicProgress(context, comic)
        totalPages = total
        currentPage = current
    }

    val maskStartAlpha = overlayAlpha.coerceIn(0f, 1f)
    val maskEndAlpha = (maskStartAlpha + 0.35f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = bgImageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = maskStartAlpha),
                            Color.Black.copy(alpha = maskEndAlpha)
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Card(
                modifier = Modifier.weight(3f).aspectRatio(2f/3f).align(Alignment.Top).padding(top = 16.dp),
                elevation = CardDefaults.cardElevation(16.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                AsyncImage(model = coverImageRequest, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(7f).fillMaxHeight()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comic.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (comic.isFavorite) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.size(32.dp).padding(start = 12.dp))
                    if (comic.isNsfw) Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp).padding(start = 12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (appLanguage == AppLanguage.CHINESE) "共 ${totalPages} 页  |  阅读进度：${if (totalPages > 0) ((currentPage.toFloat() / totalPages) * 100).toInt() else 0}%" else "${totalPages} pages  |  Progress: ${if (totalPages > 0) ((currentPage.toFloat() / totalPages) * 100).toInt() else 0}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(comic.chapters.size) { index ->
                            val chapter = comic.chapters[index]
                            val isLastRead = index == comic.lastReadChapterIndex
                            Card(
                                onClick = { onChapterClick(chapter, index) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isLastRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        chapter.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isLastRead) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLastRead) Color.White else Color.White.copy(alpha = 0.9f)
                                    )
                                    if (isLastRead) {
                                        Icon(Icons.Rounded.History, if (appLanguage == AppLanguage.CHINESE) "上次阅读" else "Last Read", tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (appLanguage == AppLanguage.CHINESE) "上次阅读" else "Last Read", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 小说详情页 ---
@Composable
fun NovelDetailScreen(
    paddingValues: PaddingValues,
    novel: NovelHistory,
    overlayAlpha: Float,
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    onChapterClick: (NovelChapter, Int) -> Unit
) {
    val context = LocalContext.current
    val chapterListState = rememberLazyListState()
    val bgImageRequest = remember(novel.id) {
        ImageRequest.Builder(context)
            .data(novel.coverUriString)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val coverImageRequest = remember(novel.id) {
        ImageRequest.Builder(context)
            .data(novel.coverUriString)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val maskStartAlpha = overlayAlpha.coerceIn(0f, 1f)
    val maskEndAlpha = (maskStartAlpha + 0.35f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = bgImageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = maskStartAlpha),
                            Color.Black.copy(alpha = maskEndAlpha)
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Card(
                modifier = Modifier.weight(3f).aspectRatio(2f/3f).align(Alignment.Top).padding(top = 16.dp),
                elevation = CardDefaults.cardElevation(0.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                AsyncImage(model = coverImageRequest, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(7f).fillMaxHeight()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        novel.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (novel.isFavorite) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.size(32.dp).padding(start = 12.dp))
                    if (novel.isNsfw) Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp).padding(start = 12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (appLanguage == AppLanguage.CHINESE) "共 ${novel.chapters.size} 章  |  阅读进度：${((novel.lastReadChapterIndex.toFloat() / maxOf(1, novel.chapters.size)) * 100).toInt()}%" else "${novel.chapters.size} chapters  |  Progress: ${((novel.lastReadChapterIndex.toFloat() / maxOf(1, novel.chapters.size)) * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    LaunchedEffect(novel.id, novel.lastReadChapterIndex) {
                        scrollToItemCentered(chapterListState, novel.lastReadChapterIndex)
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = chapterListState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = novel.chapters,
                                key = { index, _ -> "${novel.id}_chapter_$index" }
                            ) { index, chapter ->
                                val isLastRead = index == novel.lastReadChapterIndex
                                Card(
                                    onClick = { onChapterClick(chapter, index) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isLastRead) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            chapter.name,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isLastRead) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.White
                                        )
                                        if (isLastRead) {
                                            Icon(Icons.Rounded.History, if (appLanguage == AppLanguage.CHINESE) "上次阅读" else "Last Read", tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (appLanguage == AppLanguage.CHINESE) "上次阅读" else "Last Read", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                        VerticalFastScroller(
                            listState = chapterListState,
                            totalItems = novel.chapters.size,
                            isVisible = true,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }
}

// --- 音频详情页 ---
@Composable
fun AudioDetailScreen(
    paddingValues: PaddingValues,
    audio: AudioHistory,
    overlayAlpha: Float,
    appLanguage: AppLanguage = AppLanguage.CHINESE,
    currentPlayingAudioId: String? = null,
    currentPlayingTrackIndex: Int = 0,
    isPlaying: Boolean = false,
    highlightTrackIndex: Int? = null,
    onTrackClick: (AudioTrack, Int) -> Unit
) {
    val context = LocalContext.current
    val bgImageRequest = remember(audio.id) {
        ImageRequest.Builder(context)
            .data(audio.coverUriString ?: R.drawable.default_audio_cover)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val coverImageRequest = remember(audio.id) {
        ImageRequest.Builder(context)
            .data(audio.coverUriString ?: R.drawable.default_audio_cover)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val maskStartAlpha = overlayAlpha.coerceIn(0f, 1f)
    val maskEndAlpha = (maskStartAlpha + 0.35f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = bgImageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = maskStartAlpha),
                            Color.Black.copy(alpha = maskEndAlpha)
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Card(
                modifier = Modifier.weight(3f).aspectRatio(1f).align(Alignment.Top).padding(top = 16.dp),
                elevation = CardDefaults.cardElevation(0.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                AsyncImage(model = coverImageRequest, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(7f).fillMaxHeight()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        audio.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (audio.isFavorite) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.size(32.dp).padding(start = 12.dp))
                    if (audio.isNsfw) Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp).padding(start = 12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (appLanguage == AppLanguage.CHINESE) "共 ${audio.tracks.size} 首" else "${audio.tracks.size} tracks",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    // Auto-scroll to highlighted or playing track
                    val trackListState = rememberLazyListState()
                    val targetTrackIndex = highlightTrackIndex 
                        ?: if (isPlaying && currentPlayingAudioId == audio.id) currentPlayingTrackIndex else null
                    
                    // Use scrollToItemCentered for centered scrolling
                    LaunchedEffect(targetTrackIndex) {
                        targetTrackIndex?.let { index ->
                            if (index in 0 until audio.tracks.size) {
                                scrollToItemCentered(trackListState, index)
                            }
                        }
                    }
                    
                    LazyColumn(
                        state = trackListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(audio.tracks.size) { index ->
                            val track = audio.tracks[index]
                            val isTrackPlaying = isPlaying && currentPlayingAudioId == audio.id && currentPlayingTrackIndex == index
                            val isHighlighted = highlightTrackIndex == index
                            Card(
                                onClick = { onTrackClick(track, index) },
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isTrackPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        isHighlighted -> Color(0xFFFFD700).copy(alpha = 0.3f) // Gold highlight for search
                                        else -> Color.White.copy(alpha = 0.1f)
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Bouncing music note for currently playing track
                                    if (isTrackPlaying) {
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
                                            tint = Color.White,
                                            modifier = Modifier.offset { IntOffset(0, offsetY.dp.roundToPx()) }.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        track.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isTrackPlaying) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    // 收藏红心 - 显示在曲目名称右侧
                                    if (track.isFavorite) {
                                        Icon(
                                            Icons.Rounded.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                        )
                                    }
                                    if (isHighlighted && !isTrackPlaying) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = if (appLanguage == AppLanguage.CHINESE) "搜索匹配" else "Search match",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
