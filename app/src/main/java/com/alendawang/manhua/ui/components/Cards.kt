package com.alendawang.manhua.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.alendawang.manhua.R
import com.alendawang.manhua.model.AppTheme
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.ComicHistory
import com.alendawang.manhua.model.NovelHistory
import com.alendawang.manhua.utils.computeComicProgress

// --- 漫画网格卡片 ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicHistoryItemGridCard(
    history: ComicHistory, 
    theme: AppTheme, 
    onClick: () -> Unit, 
    onLongClick: () -> Unit, 
    containerAlpha: Float = 1f,
    isScrolling: Boolean = false  // 新增：滚动状态
) {
    val context = LocalContext.current
    // 智能图片加载：滚动时优先使用缓存，停止后加载新图片
    val imageRequest = remember(history.id, isScrolling) {
        ImageRequest.Builder(context)
            .data(history.coverUriString)
            .crossfade(!isScrolling)  // 滚动时禁用淡入动画
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(if (isScrolling) CachePolicy.READ_ONLY else CachePolicy.ENABLED)  // 滚动时只读缓存
            .memoryCacheKey("${history.id}_cover")
            .diskCacheKey("${history.id}_cover")
            .build()
    }
    // 直接使用缓存的进度数据，避免 IO 操作
    val totalPages = history.cachedTotalPages
    val currentPage = history.cachedCurrentPage

    Column(modifier = Modifier.padding(6.dp).fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f/3f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Rounded.BrokenImage)
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))

                if (history.isFavorite) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = 0.9f), CircleShape).padding(4.dp)) {
                        Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                    }
                }

                if (history.isNsfw) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("HIDDEN", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = history.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        val progressPercent = if (totalPages > 0 && currentPage >= 0) {
            ((currentPage.toFloat() / totalPages) * 100).toInt()
        } else {
            0
        }
        Text(
            text = "阅读进度 ${progressPercent}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- 漫画列表卡片 ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicHistoryItemListCard(
    history: ComicHistory, 
    theme: AppTheme, 
    onClick: () -> Unit, 
    onLongClick: () -> Unit, 
    containerAlpha: Float = 1f,
    isScrolling: Boolean = false  // 新增：滚动状态
) {
    val context = LocalContext.current
    // 智能图片加载：滚动时优先使用缓存
    val imageRequest = remember(history.id, isScrolling) {
        ImageRequest.Builder(context)
            .data(history.coverUriString)
            .crossfade(!isScrolling)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(if (isScrolling) CachePolicy.READ_ONLY else CachePolicy.ENABLED)
            .memoryCacheKey("${history.id}_cover")
            .diskCacheKey("${history.id}_cover")
            .build()
    }
    val totalPages = history.cachedTotalPages
    val currentPage = history.cachedCurrentPage
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)),
        modifier = Modifier.padding(6.dp).fillMaxWidth().height(90.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.aspectRatio(2f/3f).fillMaxHeight()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(Icons.Rounded.BrokenImage)
                    )
                }
                if (history.isNsfw) {
                    Box(modifier = Modifier.align(Alignment.TopStart).background(MaterialTheme.colorScheme.error).padding(2.dp)) {
                        Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = history.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ImportContacts, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    val progressPercent = if (totalPages > 0 && currentPage >= 0) {
                        ((currentPage.toFloat() / totalPages) * 100).toInt()
                    } else {
                        0
                    }
                    Text(
                        text = "${maxOf(0, totalPages)} 页 · 进度 ${progressPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (history.isFavorite) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

// --- 小说历史卡片 (列表视图) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelHistoryItemCard(history: NovelHistory, theme: AppTheme, onClick: () -> Unit, onLongClick: () -> Unit, containerAlpha: Float = 1f) {
    val context = LocalContext.current
    val imageRequest = remember(history.id) {
        ImageRequest.Builder(context)
            .data(history.coverUriString)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${history.id}_cover")
            .diskCacheKey("${history.id}_cover")
            .build()
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)),
        modifier = Modifier.padding(6.dp).fillMaxWidth().height(90.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.aspectRatio(2f/3f).fillMaxHeight()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(Icons.Rounded.BrokenImage)
                    )
                }
                if (history.isNsfw) {
                    Box(modifier = Modifier.align(Alignment.TopStart).background(MaterialTheme.colorScheme.error).padding(2.dp)) {
                        Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = history.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Menu, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${history.chapters.size} 章 · 进度 ${(history.lastReadChapterIndex.toFloat() / maxOf(1, history.chapters.size) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (history.isFavorite) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

// --- 小说网格卡片 ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelHistoryItemGridCard(
    history: NovelHistory, 
    theme: AppTheme, 
    onClick: () -> Unit, 
    onLongClick: () -> Unit, 
    containerAlpha: Float = 1f
) {
    val context = LocalContext.current
    val imageRequest = remember(history.id) {
        ImageRequest.Builder(context)
            .data(history.coverUriString)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${history.id}_cover")
            .diskCacheKey("${history.id}_cover")
            .build()
    }
    val progressPercent = (history.lastReadChapterIndex.toFloat() / maxOf(1, history.chapters.size) * 100).toInt()

    Column(modifier = Modifier.padding(6.dp).fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f/3f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Rounded.MenuBook)
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))

                if (history.isFavorite) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = 0.9f), CircleShape).padding(4.dp)) {
                        Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                    }
                }

                if (history.isNsfw) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("HIDDEN", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = history.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "阅读进度 ${progressPercent}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- 音频历史卡片 (列表视图) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioHistoryItemCard(history: AudioHistory, theme: AppTheme, onClick: () -> Unit, onLongClick: () -> Unit, containerAlpha: Float = 1f) {
    val context = LocalContext.current
    val imageRequest = remember(history.id) {
        ImageRequest.Builder(context)
            .data(history.coverUriString ?: R.drawable.default_audio_cover)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${history.id}_cover")
            .diskCacheKey("${history.id}_cover")
            .build()
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)),
        modifier = Modifier.padding(6.dp).fillMaxWidth().height(90.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.aspectRatio(1f).fillMaxHeight()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(Icons.Rounded.BrokenImage)
                    )
                }
                if (history.isNsfw) {
                    Box(modifier = Modifier.align(Alignment.TopStart).background(MaterialTheme.colorScheme.error).padding(2.dp)) {
                        Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = history.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Headphones, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${history.tracks.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (history.isFavorite) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

// --- 音频网格卡片 ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioHistoryItemGridCard(
    history: AudioHistory, 
    theme: AppTheme, 
    onClick: () -> Unit, 
    onLongClick: () -> Unit, 
    containerAlpha: Float = 1f
) {
    val context = LocalContext.current
    val imageRequest = remember(history.id) {
        ImageRequest.Builder(context)
            .data(history.coverUriString ?: R.drawable.default_audio_cover)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("${history.id}_cover")
            .diskCacheKey("${history.id}_cover")
            .build()
    }

    Column(modifier = Modifier.padding(6.dp).fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Rounded.Headphones)
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))

                if (history.isFavorite) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = 0.9f), CircleShape).padding(4.dp)) {
                        Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                    }
                }

                if (history.isNsfw) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("HIDDEN", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = history.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${history.tracks.size} 首",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
