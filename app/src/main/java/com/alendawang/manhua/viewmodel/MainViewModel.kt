package com.alendawang.manhua.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alendawang.manhua.data.AppDatabase
import com.alendawang.manhua.data.MediaRepository
import com.alendawang.manhua.model.*
import com.alendawang.manhua.navigation.Screen
import com.alendawang.manhua.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MainViewModel - 管理应用的核心状态和业务逻辑

 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context: Context get() = getApplication()
    
    // Room 数据库仓库
    private val repository = MediaRepository(AppDatabase.getInstance(application))
    
    // 启动时清理所有漫画图片缓存 + 迁移数据 + 加载数据
    init {
        viewModelScope.launch(Dispatchers.IO) {
            clearAllComicImageCaches(context)
        }
        viewModelScope.launch {
            // 先执行 SP → Room 一次性迁移
            repository.migrateFromSharedPreferencesIfNeeded(context)
            // 然后从 Room 加载数据
            comicHistoryList = applySort(repository.loadAllComics(), sortOption)
            novelHistoryList = applyNovelSort(repository.loadAllNovels(), sortOption)
            audioHistoryList = applyAudioSort(repository.loadAllAudio(), sortOption)
        }
    }
    
    // --- 全局状态 ---
    var currentTheme by mutableStateOf(loadTheme(context))
        private set
    var currentScreen by mutableStateOf<Screen>(Screen.Home)
    var currentMediaType by mutableStateOf(MediaType.COMIC)
    
    // --- 漫画状态 ---
    var sortOption by mutableStateOf(loadSortOption(context))
        private set
    var comicHistoryList by mutableStateOf<List<ComicHistory>>(emptyList())
        private set
    var displayMode by mutableStateOf(loadDisplayMode(context))
        private set
    
    // --- 小说状态 ---
    var novelHistoryList by mutableStateOf<List<NovelHistory>>(emptyList())
        private set
    
    // --- 音频状态 ---
    var audioHistoryList by mutableStateOf<List<AudioHistory>>(emptyList())
        private set
    var audioDisplayMode by mutableStateOf(loadAudioDisplayMode(context))
        private set
    
    // --- 搜索状态 ---
    var isSearchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    
    // --- 隐藏模式状态 ---
    var isHiddenModeUnlocked by mutableStateOf(false)
    var showPasswordDialog by mutableStateOf(false)
    var showSetPasswordDialog by mutableStateOf(false)
    var showHelpDialog by mutableStateOf(false)
    var showThemeLongPressMenu by mutableStateOf(false)
    
    // --- 自定义背景状态 ---
    var customHomeBackgroundUri by mutableStateOf(loadHomeBackgroundUri(context))
        private set
    var customHomeBackgroundAlpha by mutableFloatStateOf(loadHomeBackgroundAlpha(context))
        private set
    
    // --- 扫描状态 ---
    var scanState by mutableStateOf(ScanState())
        private set
    private var scanJob: Job? = null
    
    // --- 对话框状态 ---
    var showHomeLongPressDialog by mutableStateOf(false)
    var showDeleteConfirmDialog by mutableStateOf(false)
    var itemToDelete by mutableStateOf<MediaHistory?>(null)
    var longPressedItem by mutableStateOf<MediaHistory?>(null)
    var showDisplayModeMenu by mutableStateOf(false)
    var showSortMenu by mutableStateOf(false)
    var showDetailOverlayDialog by mutableStateOf(false)
    var detailOverlayAlpha by mutableFloatStateOf(loadDetailOverlayAlpha(context))
        private set
    
    // --- 多选模式状态 ---
    var isMultiSelectMode by mutableStateOf(false)
    var selectedItems by mutableStateOf(setOf<String>())
    
    // --- UI 状态 ---
    var isBarsVisible by mutableStateOf(true)
    
    // === 计算属性 ===
    
    val displayedHistoryList: List<MediaHistory>
        get() = when (currentMediaType) {
            MediaType.COMIC -> applySort(
                filterHistory(comicHistoryList, isHiddenModeUnlocked, searchQuery),
                sortOption
            )
            MediaType.NOVEL -> filterHistory(novelHistoryList, isHiddenModeUnlocked, searchQuery)
            MediaType.AUDIO -> filterHistory(audioHistoryList, isHiddenModeUnlocked, searchQuery)
        }
    
    // === 更新函数 ===
    
    fun updateNovelHistory(updated: List<NovelHistory>) {
        val sorted = applyNovelSort(updated, sortOption)
        novelHistoryList = sorted
        viewModelScope.launch { repository.saveAllNovels(sorted) }
    }
    
    fun updateAudioHistory(updated: List<AudioHistory>) {
        val sorted = applyAudioSort(updated, sortOption)
        audioHistoryList = sorted
        viewModelScope.launch { repository.saveAllAudio(sorted) }
    }
    
    fun updateNovelItem(id: String, transform: (NovelHistory) -> NovelHistory) {
        updateNovelHistory(novelHistoryList.map { if (it.id == id) transform(it) else it })
    }
    
    fun updateAudioItem(id: String, transform: (AudioHistory) -> AudioHistory) {
        updateAudioHistory(audioHistoryList.map { if (it.id == id) transform(it) else it })
    }
    
    fun updateComicHistory(updated: ComicHistory) {
        // 更新内存列表
        val updatedList = comicHistoryList.toMutableList()
        val index = updatedList.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            val oldItem = updatedList[index]
            updatedList[index] = updated.copy(
                isNsfw = if (updated.timestamp == oldItem.timestamp) updated.isNsfw 
                         else (if (updated.isNsfw) true else oldItem.isNsfw)
            )
        } else {
            updatedList.add(updated)
        }
        comicHistoryList = applySort(updatedList, sortOption)
        viewModelScope.launch { repository.saveAllComics(comicHistoryList) }
    }
    
    fun deleteComic(id: String) {
        comicHistoryList = comicHistoryList.filter { it.id != id }
        viewModelScope.launch { repository.deleteComic(id) }
    }
    
    // 退出漫画阅读器时清理当前章节的缓存 (所有类型)
    fun cleanupComicCache(chapterUri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            clearComicImageCache(context, chapterUri)
        }
    }
    
    // === 主题和显示模式 ===
    
    fun setTheme(theme: AppTheme) {
        currentTheme = theme
        saveTheme(context, theme)
    }
    
    fun nextTheme() {
        setTheme(currentTheme.next())
    }
    
    fun changeSortOption(option: SortOption) {
        sortOption = option
        saveSortOption(context, option)
        // 立即应用排序
        when (currentMediaType) {
            MediaType.COMIC -> comicHistoryList = applySort(comicHistoryList, option)
            MediaType.NOVEL -> novelHistoryList = applyNovelSort(novelHistoryList, option)
            MediaType.AUDIO -> audioHistoryList = applyAudioSort(audioHistoryList, option)
        }
    }
    
    fun changeDisplayMode(mode: DisplayMode) {
        displayMode = mode
        saveDisplayMode(context, mode)
    }
    
    fun changeAudioDisplayMode(mode: AudioDisplayMode) {
        audioDisplayMode = mode
        saveAudioDisplayMode(context, mode)
    }
    
    // === 背景设置 ===
    
    fun setHomeBackground(uriString: String) {
        saveHomeBackgroundUri(context, uriString)
        customHomeBackgroundUri = uriString
    }
    
    fun setHomeBackgroundAlpha(alpha: Float) {
        val newValue = alpha.coerceIn(0f, 1f)
        customHomeBackgroundAlpha = newValue
        saveHomeBackgroundAlpha(context, newValue)
    }
    
    fun clearHomeBackground() {
        saveHomeBackgroundUri(context, "")
        customHomeBackgroundUri = null
    }
    
    fun updateDetailOverlayAlpha(alpha: Float) {
        val newValue = alpha.coerceIn(0f, 1f)
        detailOverlayAlpha = newValue
        saveDetailOverlayAlpha(context, newValue)
    }
    
    // === 密码相关 ===
    
    fun checkPassword(input: String): Boolean {
        return com.alendawang.manhua.utils.checkPassword(context, input)
    }
    
    fun savePassword(input: String) {
        com.alendawang.manhua.utils.savePassword(context, input)
    }
    
    fun hasPassword(): Boolean {
        return com.alendawang.manhua.utils.hasPassword(context)
    }
    
    // === 扫描操作 ===
    
    fun startScan(treeUri: Uri) {
        viewModelScope.launch {
            scanState = ScanState(isScanning = true, currentFolder = "准备开始...", totalFound = 0)
            try {
                when (currentMediaType) {
                    MediaType.COMIC -> {
                        // 使用 Map 接口支持增量扫描
                        val existingComicsMap = comicHistoryList.associateBy { it.uriString }
                        scanComicsFlow(context, treeUri, existingComicsMap) { name ->
                            if (scanState.isScanning) scanState = scanState.copy(currentFolder = name)
                        }.collect { comic ->
                            // 无论是新增还是更新，都添加/替换到列表
                            updateComicHistory(comic)
                            if (scanState.isScanning) scanState = scanState.copy(totalFound = scanState.totalFound + 1)
                        }
                    }
                    MediaType.NOVEL -> {
                        val existingUris = novelHistoryList.map { it.uriString }.toSet()
                        scanNovelsFlow(context, treeUri, existingUris) { name ->
                            if (scanState.isScanning) scanState = scanState.copy(currentFolder = name)
                        }.collect { newNovel ->
                            if (novelHistoryList.none { it.uriString == newNovel.uriString }) {
                                updateNovelHistory(novelHistoryList + newNovel)
                                if (scanState.isScanning) scanState = scanState.copy(totalFound = scanState.totalFound + 1)
                            }
                        }
                    }
                    MediaType.AUDIO -> {
                        val existingUris = audioHistoryList.map { it.uriString }.toSet()
                        scanAudiosFlow(context, treeUri, existingUris) { name ->
                            if (scanState.isScanning) scanState = scanState.copy(currentFolder = name)
                        }.collect { newAudio ->
                            if (audioHistoryList.none { it.uriString == newAudio.uriString }) {
                                updateAudioHistory(audioHistoryList + newAudio)
                                if (scanState.isScanning) scanState = scanState.copy(totalFound = scanState.totalFound + 1)
                            }
                        }
                    }
                }
                Toast.makeText(context, "扫描完成", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Handle exception
            } finally {
                scanState = ScanState(isScanning = false)
            }
        }
    }
    
    fun cancelScan() {
        scanState = scanState.copy(isScanning = false)
        scanJob?.cancel()
    }
    
    // === 封面更新 ===
    
    fun updateCover(uri: Uri) {
        val screen = currentScreen
        if (screen !is Screen.Details) return
        
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {}
        
        when (screen.mediaType) {
            MediaType.COMIC -> {
                val item = comicHistoryList.find { it.id == screen.mediaId }
                if (item != null) {
                    updateComicHistory(item.copy(coverUriString = uri.toString()))
                }
            }
            MediaType.NOVEL -> {
                val item = novelHistoryList.find { it.id == screen.mediaId }
                if (item != null) {
                    updateNovelItem(item.id) { it.copy(coverUriString = uri.toString()) }
                }
            }
            MediaType.AUDIO -> {
                val item = audioHistoryList.find { it.id == screen.mediaId }
                if (item != null) {
                    updateAudioItem(item.id) { it.copy(coverUriString = uri.toString()) }
                }
            }
        }
    }
    
    // === 删除操作 ===
    
    fun deleteItem(item: MediaHistory) {
        when (item) {
            is ComicHistory -> deleteComic(item.id)
            is NovelHistory -> updateNovelHistory(novelHistoryList.filter { it.id != item.id })
            is AudioHistory -> updateAudioHistory(audioHistoryList.filter { it.id != item.id })
        }
        // 如果在详情页删除，退回到首页
        if (currentScreen is Screen.Details) {
            currentScreen = Screen.Home
        }
    }
    
    // === 清空历史 ===
    
    fun clearCurrentHistory() {
        when (currentMediaType) {
            MediaType.COMIC -> {
                comicHistoryList = emptyList()
                viewModelScope.launch { repository.clearAllComics() }
            }
            MediaType.NOVEL -> {
                updateNovelHistory(emptyList())
            }
            MediaType.AUDIO -> {
                updateAudioHistory(emptyList())
            }
        }
    }
    
    // === 多选操作 ===
    
    fun toggleSelection(id: String) {
        selectedItems = if (selectedItems.contains(id)) {
            selectedItems - id
        } else {
            selectedItems + id
        }
    }
    
    fun clearSelection() {
        selectedItems = emptySet()
        isMultiSelectMode = false
    }
    
    fun selectAll() {
        selectedItems = displayedHistoryList.map { it.id }.toSet()
    }
    
    fun deleteSelectedItems() {
        when (currentMediaType) {
            MediaType.COMIC -> {
                val idsToDelete = selectedItems.toList()
                comicHistoryList = comicHistoryList.filter { it.id !in selectedItems }
                viewModelScope.launch { repository.deleteComicsByIds(idsToDelete) }
            }
            MediaType.NOVEL -> {
                updateNovelHistory(novelHistoryList.filter { it.id !in selectedItems })
            }
            MediaType.AUDIO -> {
                updateAudioHistory(audioHistoryList.filter { it.id !in selectedItems })
            }
        }
        clearSelection()
    }
    
    fun favoriteSelectedItems() {
        when (currentMediaType) {
            MediaType.COMIC -> {
                comicHistoryList = comicHistoryList.map { comic ->
                    if (comic.id in selectedItems) comic.copy(isFavorite = true) else comic
                }
                viewModelScope.launch { repository.saveAllComics(comicHistoryList) }
            }
            MediaType.NOVEL -> {
                updateNovelHistory(novelHistoryList.map { novel ->
                    if (novel.id in selectedItems) novel.copy(isFavorite = true) else novel
                })
            }
            MediaType.AUDIO -> {
                updateAudioHistory(audioHistoryList.map { audio ->
                    if (audio.id in selectedItems) audio.copy(isFavorite = true) else audio
                })
            }
        }
        clearSelection()
    }
    
    fun hideSelectedItems() {
        when (currentMediaType) {
            MediaType.COMIC -> {
                comicHistoryList = comicHistoryList.map { comic ->
                    if (comic.id in selectedItems) comic.copy(isNsfw = true) else comic
                }
                viewModelScope.launch { repository.saveAllComics(comicHistoryList) }
            }
            MediaType.NOVEL -> {
                updateNovelHistory(novelHistoryList.map { novel ->
                    if (novel.id in selectedItems) novel.copy(isNsfw = true) else novel
                })
            }
            MediaType.AUDIO -> {
                updateAudioHistory(audioHistoryList.map { audio ->
                    if (audio.id in selectedItems) audio.copy(isNsfw = true) else audio
                })
            }
        }
        clearSelection()
    }
}
