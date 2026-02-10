package com.alendawang.manhua.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alendawang.manhua.model.AppTheme
import com.alendawang.manhua.model.AudioDisplayMode
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.AudioTrack
import com.alendawang.manhua.model.ComicHistory
import com.alendawang.manhua.model.DisplayMode
import com.alendawang.manhua.model.NovelHistory
import com.alendawang.manhua.ui.components.*

// ============================================================
// 共享多选覆盖层 — 替代原先复制6次的多选指示器代码
// ============================================================
@Composable
fun BoxScope.MultiSelectOverlay(
    isMultiSelectMode: Boolean,
    isSelected: Boolean
) {
    if (!isMultiSelectMode) return
    // 背景高亮 + 边框
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .border(
                if (isSelected) 3.dp else 0.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
    )
    // 勾选圆圈
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(24.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.7f),
                CircleShape
            )
            .border(
                2.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ============================================================
// 漫画 Tab 内容
// ============================================================
@Composable
fun ComicTabContent(
    comicList: List<ComicHistory>,
    currentTheme: AppTheme,
    displayMode: DisplayMode,
    cardAlpha: Float,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    onHistoryItemClick: (ComicHistory) -> Unit,
    onHistoryItemLongClick: (ComicHistory) -> Unit,
    lazyGridState: LazyGridState = rememberLazyGridState()
) {
    val cachedComicList = remember(comicList.hashCode()) { comicList }
    val isScrolling = lazyGridState.isScrollInProgress

    LazyVerticalGrid(
        state = lazyGridState,
        columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1)
                  else GridCells.Fixed(displayMode.columnCount),
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
                MultiSelectOverlay(isMultiSelectMode, isSelected)
            }
        }
        item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================
// 小说 Tab 内容
// ============================================================
@Composable
fun NovelTabContent(
    novelList: List<NovelHistory>,
    currentTheme: AppTheme,
    displayMode: DisplayMode,
    cardAlpha: Float,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    onHistoryItemClick: (NovelHistory) -> Unit,
    onHistoryItemLongClick: (NovelHistory) -> Unit,
    lazyGridState: LazyGridState = rememberLazyGridState()
) {
    val cachedNovelList = remember(novelList.hashCode()) { novelList }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1)
                  else GridCells.Fixed(displayMode.columnCount),
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
                MultiSelectOverlay(isMultiSelectMode, isSelected)
            }
        }
        item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================
// 音频 Tab 内容
// ============================================================
@Composable
fun AudioTabContent(
    audioList: List<AudioHistory>,
    currentTheme: AppTheme,
    displayMode: DisplayMode,
    audioDisplayMode: AudioDisplayMode,
    cardAlpha: Float,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    isAudioPlaying: Boolean,
    currentPlayingAudioId: String?,
    currentPlayingTrackIndex: Int,
    searchQuery: String,
    audioSearchMatchingTracks: Map<String, List<Int>>,
    onHistoryItemClick: (AudioHistory) -> Unit,
    onHistoryItemLongClick: (AudioHistory) -> Unit,
    onAudioTrackClick: (AudioHistory, Int) -> Unit,
    onAudioTrackLongClick: (AudioHistory, Int) -> Unit,
    lazyGridState: LazyGridState = rememberLazyGridState()
) {
    val cachedAudioList = remember(audioList.hashCode()) { audioList }
    val isSearching = searchQuery.isNotBlank() && audioSearchMatchingTracks.isNotEmpty()

    if (isSearching || audioDisplayMode == AudioDisplayMode.SINGLES) {
        // 单曲模式 / 搜索模式
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
            columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1)
                      else GridCells.Fixed(displayMode.columnCount),
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
                    isFavorite = track.isFavorite,
                    isNsfw = audio.isNsfw
                )
                val isSelected = selectedItems.contains(audio.id)
                Box {
                    if (displayMode == DisplayMode.ListView)
                        AudioHistoryItemCard(
                            singleTrackAudio, currentTheme,
                            onClick = { onAudioTrackClick(audio, trackIndex) },
                            onLongClick = { onAudioTrackLongClick(audio, trackIndex) },
                            cardAlpha, showFavorite = true
                        )
                    else
                        AudioHistoryItemGridCard(
                            singleTrackAudio, currentTheme,
                            onClick = { onAudioTrackClick(audio, trackIndex) },
                            onLongClick = { onAudioTrackLongClick(audio, trackIndex) },
                            cardAlpha, showFavorite = true
                        )
                    MultiSelectOverlay(isMultiSelectMode, isSelected)
                    // 播放指示器
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
        // 专辑模式
        LazyVerticalGrid(
            state = lazyGridState,
            columns = if (displayMode == DisplayMode.ListView) GridCells.Fixed(1)
                      else GridCells.Fixed(displayMode.columnCount),
            contentPadding = PaddingValues(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cachedAudioList, key = { it.id }) { history ->
                val isSelected = selectedItems.contains(history.id)
                Box {
                    if (displayMode == DisplayMode.ListView)
                        AudioHistoryItemCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha, showFavorite = false)
                    else
                        AudioHistoryItemGridCard(history, currentTheme, { onHistoryItemClick(history) }, { onHistoryItemLongClick(history) }, cardAlpha, showFavorite = false)
                    MultiSelectOverlay(isMultiSelectMode, isSelected)
                }
            }
            item(span = { GridItemSpan(if (displayMode == DisplayMode.ListView) 1 else displayMode.columnCount) }) {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
