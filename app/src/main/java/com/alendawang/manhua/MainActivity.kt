package com.alendawang.manhua

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.app.Activity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// Import from new modules - using explicit imports to avoid DisplayMode conflict
import com.alendawang.manhua.model.AppTheme
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.AudioPlayerConfig
import com.alendawang.manhua.model.AudioTrack
import com.alendawang.manhua.model.ComicChapter
import com.alendawang.manhua.model.ComicHistory
import com.alendawang.manhua.model.ComicSourceType
import com.alendawang.manhua.model.DisplayMode
import com.alendawang.manhua.model.FontType
import com.alendawang.manhua.model.MediaHistory
import com.alendawang.manhua.model.MediaType
import com.alendawang.manhua.model.NovelChapter
import com.alendawang.manhua.model.NovelHistory
import com.alendawang.manhua.model.PageContent
import com.alendawang.manhua.model.ReaderBackgroundColor
import com.alendawang.manhua.model.ReaderConfig
import com.alendawang.manhua.model.ScanState
import com.alendawang.manhua.model.SortOption
import com.alendawang.manhua.model.AppLanguage
import com.alendawang.manhua.model.AudioDisplayMode
import com.alendawang.manhua.model.next
import com.alendawang.manhua.navigation.Screen
import com.alendawang.manhua.navigation.ScreenSaver
import com.alendawang.manhua.ui.theme.*
import com.alendawang.manhua.ui.components.*
import com.alendawang.manhua.ui.screens.*
import com.alendawang.manhua.utils.*
import com.alendawang.manhua.viewmodel.NovelReaderViewModel

class MainActivity : FragmentActivity() {
    private var pendingAudioNavigation: Triple<String, Int, Long>? = null
    private var pendingFileUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { 
            ComicApp(
                pendingAudioNavigation = pendingAudioNavigation,
                onAudioNavigationConsumed = { pendingAudioNavigation = null },
                pendingFileUri = pendingFileUri,
                onFileUriConsumed = { pendingFileUri = null }
            ) 
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        // 处理音频播放器通知点击
        if (intent.getBooleanExtra(AudioPlaybackService.EXTRA_NAVIGATE_TO_AUDIO_PLAYER, false)) {
            val audioId = intent.getStringExtra("extra_audio_id") ?: return
            val trackIndex = intent.getIntExtra("extra_start_index", 0)
            pendingAudioNavigation = Triple(audioId, trackIndex, 0L)
            return
        }
        
        // 处理文件打开 Intent (ACTION_VIEW)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                // 尝试获取持久化权限
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                pendingFileUri = uri
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicApp(
    pendingAudioNavigation: Triple<String, Int, Long>? = null,
    onAudioNavigationConsumed: () -> Unit = {},
    pendingFileUri: Uri? = null,
    onFileUriConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current

    // --- 全局状态 ---
    var currentTheme by remember { mutableStateOf(loadTheme(context)) }
    
    // 控制系统状态栏图标颜色
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            // Sakura(Light), Matcha(Light) 使用深色图标; Cyberpunk(Dark), InkStyle(Dark) 使用浅色图标
            val useDarkIcons = when (currentTheme) {
                AppTheme.Sakura, AppTheme.Matcha -> true 
                AppTheme.Cyberpunk, AppTheme.InkStyle -> false
            }
            
            insetsController.isAppearanceLightStatusBars = useDarkIcons
            insetsController.isAppearanceLightNavigationBars = useDarkIcons
        }
    }
    var currentScreen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    var currentMediaType by remember { mutableStateOf(loadLastMediaType(context)) }
    
    // 应用语言状态 - 首次启动自动跟随系统语言
    var appLanguage by remember { mutableStateOf(loadAppLanguage(context)) }

    // 漫画状态
    var sortOption by remember { mutableStateOf(loadSortOption(context)) }
    var comicHistoryList by remember { mutableStateOf(applySort(loadComicHistory(context), sortOption)) }
    var comicDisplayMode by remember { mutableStateOf(loadDisplayModeForType(context, MediaType.COMIC)) }

    // 小说状态
    var novelHistoryList by remember { mutableStateOf(applyNovelSort(loadNovelHistory(context), sortOption)) }
    var novelDisplayMode by remember { mutableStateOf(loadDisplayModeForType(context, MediaType.NOVEL)) }

    // 音频状态
    var audioHistoryList by remember { mutableStateOf(applyAudioSort(loadAudioHistory(context), sortOption)) }
    var audioModuleDisplayMode by remember { mutableStateOf(loadDisplayModeForType(context, MediaType.AUDIO)) }
    var audioDisplayMode by remember { mutableStateOf(loadAudioDisplayMode(context)) }
    val playbackState by AudioPlaybackBus.state.collectAsState()

    fun updateNovelHistory(updated: List<NovelHistory>) {
        val sorted = applyNovelSort(updated, sortOption)
        novelHistoryList = sorted
        saveNovelToPrefs(context, sorted)
    }

    fun updateAudioHistory(updated: List<AudioHistory>) {
        val sorted = applyAudioSort(updated, sortOption)
        audioHistoryList = sorted
        saveAudioToPrefs(context, sorted)
    }

    fun updateNovelItem(id: String, transform: (NovelHistory) -> NovelHistory) {
        updateNovelHistory(novelHistoryList.map { if (it.id == id) transform(it) else it })
    }

    fun updateAudioItem(id: String, transform: (AudioHistory) -> AudioHistory) {
        updateAudioHistory(audioHistoryList.map { if (it.id == id) transform(it) else it })
    }

    // 更新单曲收藏状态
    fun updateAudioTrackFavorite(audioId: String, trackIndex: Int) {
        updateAudioItem(audioId) { audio ->
            audio.copy(
                tracks = audio.tracks.mapIndexed { index, track ->
                    if (index == trackIndex) track.copy(isFavorite = !track.isFavorite)
                    else track
                }
            )
        }
    }


    // 搜索状态
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 隐藏模式状态
    var isHiddenModeUnlocked by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showUnlockMethodDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showThemeLongPressMenu by remember { mutableStateOf(false) }

    // 自定义背景状态
    var customHomeBackgroundUri by remember { mutableStateOf(loadHomeBackgroundUri(context)) }
    var customHomeBackgroundAlpha by remember { mutableFloatStateOf(loadHomeBackgroundAlpha(context)) }

    // 过滤列表逻辑 - 使用 remember 缓存过滤结果，避免每次 derivedStateOf 都创建新列表
    // For audio, also track which tracks match the search query for highlighting
    var audioSearchMatchingTracks by remember { mutableStateOf<Map<String, List<Int>>>(emptyMap()) }
    
    val displayedHistoryList by remember(
        comicHistoryList,
        novelHistoryList,
        audioHistoryList,
        isHiddenModeUnlocked,
        sortOption,
        searchQuery,
        currentMediaType
    ) {
        derivedStateOf {
            when (currentMediaType) {
                MediaType.COMIC -> applySort(
                    filterHistory(comicHistoryList, isHiddenModeUnlocked, searchQuery),
                    sortOption
                ) as List<MediaHistory>
                MediaType.NOVEL -> filterHistory(novelHistoryList, isHiddenModeUnlocked, searchQuery) as List<MediaHistory>
                MediaType.AUDIO -> {
                    val (filteredAudio, matchingTracks) = filterAudioHistoryWithTracks(
                        audioHistoryList, isHiddenModeUnlocked, searchQuery
                    )
                    audioSearchMatchingTracks = matchingTracks
                    filteredAudio as List<MediaHistory>
                }
            }
        }
    }

    var scanState by remember { mutableStateOf(ScanState()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    // readerListState 已移动到 ComicReader 屏幕内部，按漫画/章节独立管理

    var showHomeLongPressDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<MediaHistory?>(null) }

    var longPressedItem by remember { mutableStateOf<MediaHistory?>(null) }
    var longPressedTrackIndex by remember { mutableStateOf<Int?>(null) }  // 用于单曲收藏
    var showDisplayModeMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDetailOverlayDialog by remember { mutableStateOf(false) }
    var detailOverlayAlpha by remember { mutableFloatStateOf(loadDetailOverlayAlpha(context)) }
    
    // 多选模式状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }

    // Handle pending audio navigation from notification click
    LaunchedEffect(pendingAudioNavigation) {
        if (pendingAudioNavigation != null) {
            val (audioId, trackIndex, position) = pendingAudioNavigation
            if (audioHistoryList.any { it.id == audioId }) {
                currentScreen = Screen.AudioPlayer(audioId, trackIndex, position)
                onAudioNavigationConsumed()
            }
        }
    }
    
    // 检测系统夜间模式，自动切换到暗黑主题（保留自定义背景）
    val isSystemDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
    LaunchedEffect(isSystemDarkMode) {
        if (isSystemDarkMode && currentTheme != AppTheme.Cyberpunk) {
            currentTheme = AppTheme.Cyberpunk
            saveTheme(context, AppTheme.Cyberpunk)
        }
    }
    
    // 处理文件打开 Intent
    LaunchedEffect(pendingFileUri) {
        if (pendingFileUri == null) return@LaunchedEffect
        
        val uri = pendingFileUri
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val currentTime = System.currentTimeMillis()
        
        when {
            // 漫画文件: zip, cbz, cbr, rar, pdf
            ext in listOf("zip", "cbz", "cbr", "rar", "pdf") -> {
                val sourceType = getComicSourceType(fileName)
                val comicName = fileName.substringBeforeLast('.')
                val uriString = uri.toString()
                
                // 检查是否已存在
                val existing = comicHistoryList.find { it.uriString == uriString }
                if (existing != null) {
                    // 已存在，直接打开详情页
                    currentMediaType = MediaType.COMIC
                    currentScreen = Screen.Details(MediaType.COMIC, existing.id)
                } else {
                    // 创建新的历史记录
                    val chapters = listOf(ComicChapter("全一册", uriString, sourceType))
                    val newComic = ComicHistory(
                        id = uriString,
                        name = comicName,
                        uriString = uriString,
                        coverUriString = null,
                        timestamp = currentTime,
                        chapters = chapters,
                        lastScannedAt = currentTime
                    )
                    comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newComic, sortOption)
                    currentMediaType = MediaType.COMIC
                    currentScreen = Screen.Details(MediaType.COMIC, newComic.id)
                }
            }
            // 小说文件: txt, epub
            ext in listOf("txt", "epub") -> {
                val novelName = fileName.substringBeforeLast('.')
                val uriString = uri.toString()
                
                // 检查是否已存在
                val existing = novelHistoryList.find { it.uriString == uriString }
                if (existing != null) {
                    currentMediaType = MediaType.NOVEL
                    currentScreen = Screen.Details(MediaType.NOVEL, existing.id)
                } else {
                    // 创建新的历史记录
                    val chapters = listOf(NovelChapter(novelName, uriString))
                    val newNovel = NovelHistory(
                        id = uriString,
                        name = novelName,
                        uriString = uriString,
                        coverUriString = null,
                        timestamp = currentTime,
                        chapters = chapters
                    )
                    updateNovelHistory(novelHistoryList + newNovel)
                    currentMediaType = MediaType.NOVEL
                    currentScreen = Screen.Details(MediaType.NOVEL, newNovel.id)
                }
            }
            // 音频文件
            ext in listOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "wma") -> {
                val audioName = fileName.substringBeforeLast('.')
                val uriString = uri.toString()
                
                // 检查是否已存在
                val existing = audioHistoryList.find { it.uriString == uriString }
                if (existing != null) {
                    currentMediaType = MediaType.AUDIO
                    currentScreen = Screen.AudioPlayer(existing.id, 0, 0L)
                } else {
                    // 创建新的历史记录
                    val track = AudioTrack(
                        name = audioName,
                        uriString = uriString
                    )
                    val newAudio = AudioHistory(
                        id = uriString,
                        name = audioName,
                        uriString = uriString,
                        coverUriString = null,
                        timestamp = currentTime,
                        tracks = listOf(track)
                    )
                    updateAudioHistory(audioHistoryList + newAudio)
                    currentMediaType = MediaType.AUDIO
                    currentScreen = Screen.AudioPlayer(newAudio.id, 0, 0L)
                }
            }
        }
        
        onFileUriConsumed()
    }

    val batchScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.takePersistableUriPermission(treeUri, takeFlags) } catch (e: Exception) {}

        scanJob = scope.launch {
            scanState = ScanState(isScanning = true, currentFolder = "准备开始...", totalFound = 0)
            try {
                when (currentMediaType) {
                    MediaType.COMIC -> {
                        val existingUris = comicHistoryList.map { it.uriString }.toSet()
                        scanComicsFlow(context, treeUri, existingUris) { name ->
                            if (scanState.isScanning) scanState = scanState.copy(currentFolder = name)
                        }.collect { newComic ->
                            if (comicHistoryList.none { it.uriString == newComic.uriString }) {
                                comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newComic, sortOption)
                                if (scanState.isScanning) scanState = scanState.copy(totalFound = scanState.totalFound + 1)
                            }
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
            } finally {
                scanState = ScanState(isScanning = false)
            }
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentScreen is Screen.Details) {
            val detailScreen = currentScreen as Screen.Details
            when (detailScreen.mediaType) {
                MediaType.COMIC -> {
                    val item = comicHistoryList.find { it.id == detailScreen.mediaId }
                    if (item != null) {
                        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                        val newItem = item.copy(coverUriString = uri.toString())
                        comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                    }
                }
                MediaType.NOVEL -> {
                    val item = novelHistoryList.find { it.id == detailScreen.mediaId }
                    if (item != null) {
                        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                        updateNovelItem(item.id) { it.copy(coverUriString = uri.toString()) }
                    }
                }
                MediaType.AUDIO -> {
                    val item = audioHistoryList.find { it.id == detailScreen.mediaId }
                    if (item != null) {
                        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                        updateAudioItem(item.id) { it.copy(coverUriString = uri.toString()) }
                    }
                }
            }
        }
    }

    val homeBackgroundPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uriString = uri.toString()
                saveHomeBackgroundUri(context, uriString)
                customHomeBackgroundUri = uriString
                Toast.makeText(context, "主页背景已更换", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "设置背景失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    MaterialTheme(colorScheme = when (currentTheme) {
        AppTheme.Sakura -> SakuraColors; AppTheme.Cyberpunk -> CyberpunkColors
        AppTheme.InkStyle -> InkColors; AppTheme.Matcha -> MatchaColors
    }) {
        var isBarsVisible by remember { mutableStateOf(true) }

        // 密码输入对话框
        if (showPasswordDialog) {
            PasswordDialog(
                title = AppStrings.enterPassword(appLanguage),
                onDismiss = { showPasswordDialog = false },
                onConfirm = { input ->
                    if (checkPassword(context, input)) {
                        isHiddenModeUnlocked = true
                        showPasswordDialog = false
                        Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "欢迎回来" else "Welcome back", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, AppStrings.wrongPassword(appLanguage), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 设置密码对话框
        if (showSetPasswordDialog) {
            PasswordDialog(
                title = AppStrings.setPassword(appLanguage),
                onDismiss = { showSetPasswordDialog = false },
                onConfirm = { input ->
                    savePassword(context, input)
                    showSetPasswordDialog = false
                    isHiddenModeUnlocked = true
                    Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "密码已设置" else "Password set", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 解锁方式选择对话框
        if (showUnlockMethodDialog) {
            AlertDialog(
                onDismissRequest = { showUnlockMethodDialog = false },
                title = { Text(AppStrings.chooseDefaultUnlockMethod(appLanguage)) },
                text = {
                    Column {
                        Text(
                            if (appLanguage == AppLanguage.CHINESE) 
                                "请选择您偏好的隐藏空间解锁方式，下次进入时将优先使用该方式。" 
                            else 
                                "Choose your preferred unlock method for the hidden space."
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        saveUnlockMethod(context, UnlockMethod.FINGERPRINT)
                        showUnlockMethodDialog = false
                        Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已设置为指纹解锁" else "Fingerprint unlock set", Toast.LENGTH_SHORT).show()
                    }) {
                        Text(AppStrings.useFingerprint(appLanguage))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        saveUnlockMethod(context, UnlockMethod.PASSWORD)
                        showUnlockMethodDialog = false
                        Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已设置为密码解锁" else "Password unlock set", Toast.LENGTH_SHORT).show()
                    }) {
                        Text(AppStrings.usePassword(appLanguage))
                    }
                }
            )
        }

        // 帮助对话框
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("使用说明") },
                text = {
                    Column {
                        Text("ok。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                        Text("1. 文件夹结构", fontWeight = FontWeight.Bold)
                        Text("   推荐在设备上建立专用文件夹存放资源")
                        Text("   • 漫画 (支持图片格式和常见的压缩包格式，存放逻辑为一级文件夹A为选定的扫描文件夹，在A里放二级文件夹（或压缩包）并以漫画名字命名，若单章节则二级文件夹（压缩包）里直接放图片，若多章节则二级文件夹（压缩包）里放三级文件夹（或二级压缩包），并以章节命名，三级文件夹（或二级压缩包里放图片)")
                        Text("   • 小说 (一级文件夹当中直接存放所有小说txt/epub，txt可自主设置封面，阅读背景，epub直接读取封面)")
                        Text("   • 音频 (一级文件夹当中直接存放所有音频文件/专辑文件夹，专辑文件夹里存放歌曲/封面/歌词文件。)")
                        Spacer(Modifier.height(8.dp))
                        Text("2. 菜单使用", fontWeight = FontWeight.Bold)
                        Text("   主题图标点击切换，长按自定义。每个按钮点一下/长按就知道是干嘛的了。")
                        Spacer(Modifier.height(8.dp))
                        Text("3. 隐藏模式", fontWeight = FontWeight.Bold)
                        Text("   点击右上角锁图标可设置密码，保护隐私内容。在隐藏模式下长按隐藏图标可重设密码。无论扫描还是删除，都不会对本地数据进行任何修改。")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) { Text("知道了") }
                }
            )
        }

        // 删除确认对话框
        if (showDeleteConfirmDialog && itemToDelete != null) {
            val itemName = itemToDelete?.name.orEmpty()
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false; itemToDelete = null },
                title = { Text(AppStrings.deleteConfirmTitle(appLanguage)) },
                text = { Text(if (appLanguage == AppLanguage.CHINESE) "确定要删除 \"${itemName}\" 吗？此操作无法撤销。" else "Delete \"${itemName}\"? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            when (val item = itemToDelete) {
                                is ComicHistory -> {
                                    comicHistoryList = deleteComicHistory(context, comicHistoryList, item.id)
                                }
                                is NovelHistory -> {
                                    updateNovelHistory(novelHistoryList.filter { it.id != item.id })
                                }
                                is AudioHistory -> {
                                    updateAudioHistory(audioHistoryList.filter { it.id != item.id })
                                }
                                null -> {}
                            }
                            // 如果在详情页删除，退回到首页
                            if (currentScreen is Screen.Details) {
                                currentScreen = Screen.Home
                            }
                            showDeleteConfirmDialog = false
                            itemToDelete = null
                            showHomeLongPressDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(AppStrings.delete(appLanguage)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false; itemToDelete = null }) { Text(AppStrings.cancel(appLanguage)) }
                }
            )
        }

        if (showDetailOverlayDialog) {
            AlertDialog(
                onDismissRequest = { showDetailOverlayDialog = false },
                title = { Text(if (appLanguage == AppLanguage.CHINESE) "详情背景遮罩" else "Detail Overlay") },
                text = {
                    Column {
                        Text(if (appLanguage == AppLanguage.CHINESE) "遮罩强度: ${(detailOverlayAlpha * 100).toInt()}" else "Overlay: ${(detailOverlayAlpha * 100).toInt()}%")
                        Slider(
                            value = detailOverlayAlpha,
                            onValueChange = {
                                val newValue = it.coerceIn(0f, 1f)
                                detailOverlayAlpha = newValue
                                saveDetailOverlayAlpha(context, newValue)
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDetailOverlayDialog = false }) { Text(AppStrings.ok(appLanguage)) }
                }
            )
        }

        if (scanState.isScanning) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 6.dp, modifier = Modifier.size(60.dp), color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(24.dp))
                        Text(if (appLanguage == AppLanguage.CHINESE) "正在扫描次元裂缝..." else "Scanning...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(scanState.currentFolder, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(if (appLanguage == AppLanguage.CHINESE) "已捕获: ${scanState.totalFound} 本" else "Found: ${scanState.totalFound}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(32.dp))
                        OutlinedButton(
                            onClick = {
                                scanState = scanState.copy(isScanning = false)
                                scanJob?.cancel()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text(AppStrings.cancel(appLanguage)) }
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = (currentScreen is Screen.Home) || (currentScreen is Screen.Details),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    // 搜索模式的 TopAppBar
                    if (isSearchActive && currentScreen is Screen.Home) {
                        val focusRequester = remember { FocusRequester() }
                        val focusManager = LocalFocusManager.current

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }

                        TopAppBar(
                            title = {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { 
                                        Text(when (currentMediaType) {
                                            MediaType.COMIC -> if (appLanguage == AppLanguage.CHINESE) "搜索漫画..." else "Search comics..."
                                            MediaType.NOVEL -> if (appLanguage == AppLanguage.CHINESE) "搜索小说..." else "Search novels..."
                                            MediaType.AUDIO -> if (appLanguage == AppLanguage.CHINESE) "搜索音频..." else "Search audio..."
                                        })
                                    },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    isSearchActive = false
                                    searchQuery = "" // 退出搜索时清空
                                }) {
                                    Icon(Icons.Rounded.ArrowBack, "退出搜索")
                                }
                            },
                            actions = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, "清空")
                                    }
                                }
                            }
                        )
                    } else {
                        // 正常模式的 TopAppBar
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentScreen) {
                                        is Screen.Home -> if (isHiddenModeUnlocked) {
                                            if (appLanguage == AppLanguage.CHINESE) "隐藏书库" else "Hidden Library"
                                        } else {
                                            when (currentMediaType) {
                                                MediaType.COMIC -> if (appLanguage == AppLanguage.CHINESE) "漫画库" else "Comics"
                                                MediaType.NOVEL -> if (appLanguage == AppLanguage.CHINESE) "小说库" else "Novels"
                                                MediaType.AUDIO -> if (appLanguage == AppLanguage.CHINESE) "音频库" else "Audio"
                                            }
                                        }
                                        is Screen.Details -> {
                                            val detailScreen = currentScreen as Screen.Details
                                            when (detailScreen.mediaType) {
                                                MediaType.COMIC -> comicHistoryList.find { it.id == detailScreen.mediaId }?.name ?: AppStrings.details(appLanguage)
                                                MediaType.NOVEL -> novelHistoryList.find { it.id == detailScreen.mediaId }?.name ?: AppStrings.details(appLanguage)
                                                MediaType.AUDIO -> audioHistoryList.find { it.id == detailScreen.mediaId }?.name ?: AppStrings.details(appLanguage)
                                            }
                                        }
                                        is Screen.ComicReader -> if (appLanguage == AppLanguage.CHINESE) "阅读中" else "Reading"
                                        is Screen.NovelReader -> if (appLanguage == AppLanguage.CHINESE) "阅读中" else "Reading"
                                        is Screen.AudioPlayer -> if (appLanguage == AppLanguage.CHINESE) "播放中" else "Playing"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.clickable {
                                        if (currentScreen is Screen.Home) {
                                            showHelpDialog = true
                                        }
                                    }
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (currentScreen is Screen.Details) Color.Transparent else MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                titleContentColor = if (currentScreen is Screen.Details) Color.White else MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = if (currentScreen is Screen.Details) Color.White else MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = if (currentScreen is Screen.Details) Color.White else MaterialTheme.colorScheme.onBackground
                            ),
                            actions = {
                                if (currentScreen is Screen.Home) {
                                    
                                    // 多选模式下的批量操作
                                    if (isMultiSelectMode && selectedItems.isNotEmpty()) {
                                        // 批量收藏
                                        IconButton(onClick = {
                                            selectedItems.forEach { id ->
                                                when (currentMediaType) {
                                                    MediaType.COMIC -> {
                                                        comicHistoryList.find { it.id == id }?.let { comic ->
                                                            val newItem = comic.copy(isFavorite = true)
                                                            comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                                                        }
                                                    }
                                                    MediaType.NOVEL -> updateNovelItem(id) { it.copy(isFavorite = true) }
                                                    MediaType.AUDIO -> updateAudioItem(id) { it.copy(isFavorite = true) }
                                                }
                                            }
                                            Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已收藏 ${selectedItems.size} 项" else "${selectedItems.size} favorited", Toast.LENGTH_SHORT).show()
                                            selectedItems = emptySet()
                                            isMultiSelectMode = false
                                        }) {
                                            Icon(Icons.Rounded.Favorite, AppStrings.favorite(appLanguage), tint = Color(0xFFFF5252))
                                        }
                                        // 批量隐藏
                                        IconButton(onClick = {
                                            selectedItems.forEach { id ->
                                                when (currentMediaType) {
                                                    MediaType.COMIC -> {
                                                        comicHistoryList.find { it.id == id }?.let { comic ->
                                                            val newItem = comic.copy(isNsfw = true)
                                                            comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                                                        }
                                                    }
                                                    MediaType.NOVEL -> updateNovelItem(id) { it.copy(isNsfw = true) }
                                                    MediaType.AUDIO -> updateAudioItem(id) { it.copy(isNsfw = true) }
                                                }
                                            }
                                            Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已隐藏 ${selectedItems.size} 项" else "${selectedItems.size} hidden", Toast.LENGTH_SHORT).show()
                                            selectedItems = emptySet()
                                            isMultiSelectMode = false
                                        }) {
                                            Icon(Icons.Rounded.VisibilityOff, AppStrings.hide(appLanguage))
                                        }
                                        // 批量删除
                                        IconButton(onClick = {
                                            selectedItems.forEach { id ->
                                                when (currentMediaType) {
                                                    MediaType.COMIC -> {
                                                        comicHistoryList = deleteComicHistory(context, comicHistoryList, id)
                                                    }
                                                    MediaType.NOVEL -> {
                                                        updateNovelHistory(novelHistoryList.filter { it.id != id })
                                                    }
                                                    MediaType.AUDIO -> {
                                                        updateAudioHistory(audioHistoryList.filter { it.id != id })
                                                    }
                                                }
                                            }
                                            Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已删除 ${selectedItems.size} 项" else "${selectedItems.size} deleted", Toast.LENGTH_SHORT).show()
                                            selectedItems = emptySet()
                                            isMultiSelectMode = false
                                        }) {
                                            Icon(Icons.Rounded.Delete, AppStrings.delete(appLanguage), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    // 非多选模式下显示常规菜单
                                    if (!isMultiSelectMode) {
                                        // 搜索按钮
                                        IconButton(onClick = { isSearchActive = true }) {
                                            Icon(Icons.Rounded.Search, "搜索")
                                        }

                                        // 隐藏模式开关
                                        IconButton(onClick = {
                                            if (isHiddenModeUnlocked) {
                                                isHiddenModeUnlocked = false
                                                Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已退出隐藏模式" else "Hidden mode exited", Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (!hasPassword(context)) {
                                                    // 没有密码，先设置密码
                                                    showSetPasswordDialog = true
                                                } else {
                                                    // 检查设备是否支持指纹
                                                    val biometricManager = BiometricManager.from(context)
                                                    val canUseBiometric = biometricManager.canAuthenticate(
                                                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                                                    ) == BiometricManager.BIOMETRIC_SUCCESS
                                                    
                                                    // 加载用户偏好的解锁方式
                                                    val unlockMethod = loadUnlockMethod(context)
                                                    val hasChosenMethod = hasChosenUnlockMethod(context)
                                                    
                                                    if (canUseBiometric && (unlockMethod == UnlockMethod.FINGERPRINT || !hasChosenMethod)) {
                                                        // 使用指纹解锁
                                                        val executor = ContextCompat.getMainExecutor(context)
                                                        val callback = object : BiometricPrompt.AuthenticationCallback() {
                                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                                super.onAuthenticationSucceeded(result)
                                                                isHiddenModeUnlocked = true
                                                                Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "欢迎回来" else "Welcome back", Toast.LENGTH_SHORT).show()
                                                                
                                                                // 如果用户还没选择过默认解锁方式，询问
                                                                if (!hasChosenMethod) {
                                                                    showUnlockMethodDialog = true
                                                                }
                                                            }
                                                            
                                                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                                super.onAuthenticationError(errorCode, errString)
                                                                // 用户取消或错误时，提供密码解锁选项
                                                                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || 
                                                                    errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                                                    showPasswordDialog = true
                                                                } else {
                                                                    Toast.makeText(context, errString.toString(), Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                            
                                                            override fun onAuthenticationFailed() {
                                                                super.onAuthenticationFailed()
                                                                Toast.makeText(context, AppStrings.fingerprintNotRecognized(appLanguage), Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        
                                                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                            .setTitle(AppStrings.fingerprintUnlock(appLanguage))
                                                            .setSubtitle(AppStrings.touchSensor(appLanguage))
                                                            .setNegativeButtonText(AppStrings.usePassword(appLanguage))
                                                            .build()
                                                        
                                                        // 需要 FragmentActivity 来显示 BiometricPrompt
                                                        val activity = context as? FragmentActivity
                                                        if (activity != null) {
                                                            val biometricPrompt = BiometricPrompt(activity, executor, callback)
                                                            biometricPrompt.authenticate(promptInfo)
                                                        } else {
                                                            // 如果无法获取 FragmentActivity，回退到密码
                                                            showPasswordDialog = true
                                                        }
                                                    } else {
                                                        // 使用密码解锁
                                                        showPasswordDialog = true
                                                    }
                                                }
                                            }
                                        }) {
                                            Icon(
                                                if (isHiddenModeUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                                contentDescription = "隐藏模式",
                                                tint = if (isHiddenModeUnlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // 更多菜单
                                        Box {
                                            var showMoreMenu by remember { mutableStateOf(false) }
                                            IconButton(onClick = { showMoreMenu = true }) {
                                                Icon(Icons.Rounded.MoreVert, if (appLanguage == AppLanguage.CHINESE) "更多" else "More")
                                            }
                                            DropdownMenu(
                                                expanded = showMoreMenu,
                                                onDismissRequest = { showMoreMenu = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                
                                                // 排序菜单
                                                DropdownMenuItem(
                                                    text = { Text(AppStrings.sort(appLanguage)) },
                                                    onClick = { 
                                                        showMoreMenu = false
                                                        showSortMenu = true 
                                                    },
                                                    leadingIcon = { Icon(Icons.Rounded.Sort, null) }
                                                )
                                                
                                                // 视图模式
                                                DropdownMenuItem(
                                                    text = { Text(AppStrings.view(appLanguage)) },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        showDisplayModeMenu = true
                                                    },
                                                    leadingIcon = { 
                                                        val currentDisplayMode = when (currentMediaType) {
                                                            MediaType.COMIC -> comicDisplayMode
                                                            MediaType.NOVEL -> novelDisplayMode
                                                            MediaType.AUDIO -> audioModuleDisplayMode
                                                        }
                                                        Icon(currentDisplayMode.icon, null) 
                                                    }
                                                )

                                                // 使用说明
                                                DropdownMenuItem(
                                                    text = { Text(if (appLanguage == AppLanguage.CHINESE) "使用说明" else "Help") },
                                                    onClick = {
                                                        showHelpDialog = true
                                                        showMoreMenu = false
                                                    },
                                                    leadingIcon = { Icon(Icons.Rounded.HelpOutline, null) }
                                                )
                                            }

                                            // 排序菜单 (Restored)
                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false }
                                            ) {
                                                SortOption.values().forEach { option ->
                                                    val optionLabel = when (option) {
                                                        SortOption.TimeDesc -> AppStrings.sortRecentRead(appLanguage)
                                                        SortOption.TimeAsc -> AppStrings.sortEarliestRead(appLanguage)
                                                        SortOption.NameAsc -> AppStrings.sortNameAZ(appLanguage)
                                                        SortOption.NameDesc -> AppStrings.sortNameZA(appLanguage)
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(optionLabel) },
                                                        onClick = {
                                                            sortOption = option
                                                            saveSortOption(context, option)
                                                            // Apply sort immediately
                                                            when (currentMediaType) {
                                                                MediaType.COMIC -> comicHistoryList = applySort(comicHistoryList, option)
                                                                MediaType.NOVEL -> novelHistoryList = applyNovelSort(novelHistoryList, option)
                                                                MediaType.AUDIO -> audioHistoryList = applyAudioSort(audioHistoryList, option)
                                                            }
                                                            showSortMenu = false
                                                        },
                                                        leadingIcon = {
                                                            if (sortOption == option) {
                                                                Icon(Icons.Rounded.Check, null)
                                                            }
                                                        }
                                                    )
                                                }
                                            }


                                            // 视图模式菜单 (Restored)
                                            DropdownMenu(
                                                expanded = showDisplayModeMenu,
                                                onDismissRequest = { showDisplayModeMenu = false }
                                            ) {
                                                DisplayMode.values().forEach { mode ->
                                                    val modeLabel = when (mode) {
                                                        DisplayMode.ListView -> AppStrings.listView(appLanguage)
                                                        DisplayMode.Grid3 -> AppStrings.gridView3(appLanguage)
                                                        DisplayMode.Grid4 -> AppStrings.gridView4(appLanguage)
                                                        DisplayMode.Grid5 -> AppStrings.gridView5(appLanguage)
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(modeLabel) },
                                                        onClick = {
                                                            when (currentMediaType) {
                                                                MediaType.COMIC -> {
                                                                    comicDisplayMode = mode
                                                                    saveDisplayModeForType(context, MediaType.COMIC, mode)
                                                                }
                                                                MediaType.NOVEL -> {
                                                                    novelDisplayMode = mode
                                                                    saveDisplayModeForType(context, MediaType.NOVEL, mode)
                                                                }
                                                                MediaType.AUDIO -> {
                                                                    audioModuleDisplayMode = mode
                                                                    saveDisplayModeForType(context, MediaType.AUDIO, mode)
                                                                }
                                                            }
                                                            showDisplayModeMenu = false
                                                        },
                                                        leadingIcon = {
                                                            val currentDisplayMode = when (currentMediaType) {
                                                                MediaType.COMIC -> comicDisplayMode
                                                                MediaType.NOVEL -> novelDisplayMode
                                                                MediaType.AUDIO -> audioModuleDisplayMode
                                                            }
                                                            if (currentDisplayMode == mode) {
                                                                Icon(Icons.Rounded.Check, null)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (currentScreen is Screen.Details) {
                                    IconButton(onClick = { showDetailOverlayDialog = true }) {
                                        Icon(Icons.Rounded.Tune, contentDescription = "遮罩强度")
                                    }
                                    val detailScreen = currentScreen as Screen.Details
                                    when (detailScreen.mediaType) {
                                        MediaType.COMIC -> {
                                            val comic = comicHistoryList.find { it.id == detailScreen.mediaId }
                                            if (comic != null) {
                                                IconButton(onClick = {
                                                    val newItem = comic.copy(isFavorite = !comic.isFavorite)
                                                    comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                                                }) {
                                                    Icon(
                                                        if (comic.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = AppStrings.favorite(appLanguage),
                                                        tint = if (comic.isFavorite) Color(0xFFFF5252) else Color.White
                                                    )
                                                }
                                                IconButton(onClick = { coverPickerLauncher.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, if (appLanguage == AppLanguage.CHINESE) "更换封面" else "Change Cover") }
                                                var showRename by remember { mutableStateOf(false) }
                                                IconButton(onClick = { showRename = true }) { Icon(Icons.Rounded.Edit, if (appLanguage == AppLanguage.CHINESE) "重命名" else "Rename") }
                                                if (showRename) {
                                                    RenameDialog(comic.name) { newName ->
                                                        comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, comic.copy(name = newName), sortOption)
                                                        showRename = false
                                                    }
                                                }
                                                IconButton(onClick = {
                                                    itemToDelete = comic
                                                    showDeleteConfirmDialog = true
                                                }) { Icon(Icons.Rounded.Delete, AppStrings.delete(appLanguage)) }
                                            }
                                        }
                                        MediaType.NOVEL -> {
                                            val novel = novelHistoryList.find { it.id == detailScreen.mediaId }
                                            if (novel != null) {
                                                IconButton(onClick = {
                                                    updateNovelItem(novel.id) { it.copy(isFavorite = !it.isFavorite) }
                                                }) {
                                                    Icon(
                                                        if (novel.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = AppStrings.favorite(appLanguage),
                                                        tint = if (novel.isFavorite) Color(0xFFFF5252) else Color.White
                                                    )
                                                }
                                                IconButton(onClick = { coverPickerLauncher.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, if (appLanguage == AppLanguage.CHINESE) "更换封面" else "Change Cover") }
                                                var showRename by remember { mutableStateOf(false) }
                                                IconButton(onClick = { showRename = true }) { Icon(Icons.Rounded.Edit, if (appLanguage == AppLanguage.CHINESE) "重命名" else "Rename") }
                                                if (showRename) {
                                                    RenameDialog(novel.name) { newName ->
                                                        updateNovelItem(novel.id) { it.copy(name = newName) }
                                                        showRename = false
                                                    }
                                                }
                                                IconButton(onClick = {
                                                    itemToDelete = novel
                                                    showDeleteConfirmDialog = true
                                                }) { Icon(Icons.Rounded.Delete, AppStrings.delete(appLanguage)) }
                                            }
                                        }
                                        MediaType.AUDIO -> {
                                            val audio = audioHistoryList.find { it.id == detailScreen.mediaId }
                                            if (audio != null) {
                                                IconButton(onClick = {
                                                    updateAudioItem(audio.id) { it.copy(isFavorite = !it.isFavorite) }
                                                }) {
                                                    Icon(
                                                        if (audio.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = AppStrings.favorite(appLanguage),
                                                        tint = if (audio.isFavorite) Color(0xFFFF5252) else Color.White
                                                    )
                                                }
                                                IconButton(onClick = { coverPickerLauncher.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, if (appLanguage == AppLanguage.CHINESE) "更换封面" else "Change Cover") }
                                                var showRename by remember { mutableStateOf(false) }
                                                IconButton(onClick = { showRename = true }) { Icon(Icons.Rounded.Edit, if (appLanguage == AppLanguage.CHINESE) "重命名" else "Rename") }
                                                if (showRename) {
                                                    RenameDialog(audio.name) { newName ->
                                                        updateAudioItem(audio.id) { it.copy(name = newName) }
                                                        showRename = false
                                                    }
                                                }
                                                IconButton(onClick = {
                                                    itemToDelete = audio
                                                    showDeleteConfirmDialog = true
                                                }) { Icon(Icons.Rounded.Delete, AppStrings.delete(appLanguage)) }
                                            }
                                        }
                                    }
                                }
                                    // 主题切换按钮 (只在主页显示)
                                    if (currentScreen is Screen.Home) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp) // 增加点击区域
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = {
                                                        currentTheme = currentTheme.next()
                                                        saveTheme(context, currentTheme)
                                                    },
                                                    onLongClick = {
                                                        showThemeLongPressMenu = true
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(currentTheme.next().primaryColor)
                                                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            )
                                            
                                            DropdownMenu(
                                                expanded = showThemeLongPressMenu,
                                                onDismissRequest = { showThemeLongPressMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(if (appLanguage == AppLanguage.CHINESE) "更换背景" else "Change Background") },
                                                    onClick = {
                                                        showThemeLongPressMenu = false
                                                        homeBackgroundPickerLauncher.launch(arrayOf("image/*")) 
                                                    },
                                                    leadingIcon = { Icon(Icons.Rounded.Image, null) }
                                                )
                                                // 透明度调节
                                                if (customHomeBackgroundUri != null) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(if (appLanguage == AppLanguage.CHINESE) "透明度: ${(customHomeBackgroundAlpha * 100).toInt()}%" else "Opacity: ${(customHomeBackgroundAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                                                Slider(
                                                                    value = customHomeBackgroundAlpha,
                                                                    onValueChange = {
                                                                        customHomeBackgroundAlpha = it
                                                                        saveHomeBackgroundAlpha(context, it)
                                                                    },
                                                                    valueRange = 0.1f..1.0f
                                                                )
                                                            }
                                                        },
                                                        onClick = { /* 阻断点击关闭菜单 */ },
                                                        leadingIcon = { Icon(Icons.Rounded.Opacity, null) }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text(if (appLanguage == AppLanguage.CHINESE) "恢复默认" else "Reset to Default") },
                                                    onClick = {
                                                        showThemeLongPressMenu = false
                                                        customHomeBackgroundUri = null
                                                        saveHomeBackgroundUri(context, "")
                                                        Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "背景已恢复默认" else "Background reset", Toast.LENGTH_SHORT).show()
                                                    },
                                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) }
                                                )
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            },
            containerColor = if (currentScreen is Screen.Details) Color.Black else MaterialTheme.colorScheme.background
        ) { paddingValues ->
                                    // BackHandler for non-Home screens
                                    BackHandler(enabled = currentScreen !is Screen.Home) {
                                        val current = currentScreen
                                        when (current) {
                                            is Screen.ComicReader -> {
                                                currentScreen = Screen.Details(MediaType.COMIC, current.comicId)
                                                isBarsVisible = true
                                            }
                                            is Screen.NovelReader -> {
                                                currentScreen = Screen.Details(MediaType.NOVEL, current.novelId)
                                                isBarsVisible = true
                                            }
                                            is Screen.AudioPlayer -> {
                                                currentScreen = Screen.Details(MediaType.AUDIO, current.audioId)
                                                isBarsVisible = true
                                            }
                                            is Screen.Details -> {
                                                currentScreen = Screen.Home
                                            }
                                            else -> {}
                                        }
                                    }
                                    
                                    // BackHandler for Home screen special states: multi-select, search, hidden mode
                                    BackHandler(
                                        enabled = currentScreen is Screen.Home && (isMultiSelectMode || isSearchActive || isHiddenModeUnlocked)
                                    ) {
                                        when {
                                            // 优先退出多选模式
                                            isMultiSelectMode -> {
                                                isMultiSelectMode = false
                                                selectedItems = emptySet()
                                            }
                                            // 其次退出搜索模式
                                            isSearchActive -> {
                                                isSearchActive = false
                                                searchQuery = ""
                                            }
                                            // 最后退出隐藏空间
                                            isHiddenModeUnlocked -> {
                                                isHiddenModeUnlocked = false
                                                Toast.makeText(context, if (appLanguage == AppLanguage.CHINESE) "已退出隐藏模式" else "Hidden mode exited", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }

            Crossfade(targetState = currentScreen, label = "screen", animationSpec = tween(400)) { screen ->
                when (screen) {
                    is Screen.Home -> {
                        HomeScreen(
                                paddingValues = paddingValues,
                                historyList = displayedHistoryList,
                                currentTheme = currentTheme,
                                customBackgroundUri = customHomeBackgroundUri,
                                customBackgroundAlpha = customHomeBackgroundAlpha,
                                displayMode = when (currentMediaType) {
                                    MediaType.COMIC -> comicDisplayMode
                                    MediaType.NOVEL -> novelDisplayMode
                                    MediaType.AUDIO -> audioModuleDisplayMode
                                },
                                audioDisplayMode = audioDisplayMode,
                            currentMediaType = currentMediaType,
                            isHiddenMode = isHiddenModeUnlocked,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedItems = selectedItems,
                            appLanguage = appLanguage,
                            onMediaTypeChange = { 
                                currentMediaType = it
                                saveLastMediaType(context, it)
                            },
                            onAudioDisplayModeChange = { mode ->
                                audioDisplayMode = mode
                                saveAudioDisplayMode(context, mode)
                            },
                            onBatchScanClick = { batchScanLauncher.launch(null) },
                            onToggleMultiSelectMode = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) selectedItems = emptySet()
                            },
                            onHistoryItemClick = { item ->
                                if (isMultiSelectMode) {
                                    // 多选模式下切换选中状态
                                    selectedItems = if (selectedItems.contains(item.id)) {
                                        selectedItems - item.id
                                    } else {
                                        selectedItems + item.id
                                    }
                                } else {
                                    // 正常模式下打开详情
                                    when (item) {
                                        is ComicHistory -> currentScreen = Screen.Details(MediaType.COMIC, item.id)
                                        is NovelHistory -> currentScreen = Screen.Details(MediaType.NOVEL, item.id)
                                        is AudioHistory -> {
                                            // Check if we're in singles mode with a search query
                                            if (audioDisplayMode == AudioDisplayMode.SINGLES && searchQuery.isNotBlank()) {
                                                // In singles mode with search, navigate directly to player
                                                currentScreen = Screen.AudioPlayer(item.id, item.lastPlayedIndex, 0L)
                                            } else if (audioDisplayMode == AudioDisplayMode.ALBUMS && searchQuery.isNotBlank()) {
                                                // In album mode with search, navigate to detail with first matching track highlighted
                                                val firstMatchingTrack = audioSearchMatchingTracks[item.id]?.firstOrNull()
                                                currentScreen = Screen.Details(MediaType.AUDIO, item.id, firstMatchingTrack)
                                            } else {
                                                // Normal navigation to album detail
                                                currentScreen = Screen.Details(MediaType.AUDIO, item.id)
                                            }
                                        }
                                    }
                                }
                            },
                            onHistoryItemLongClick = { item ->
                                if (!isMultiSelectMode) {
                                    longPressedItem = item
                                    showHomeLongPressDialog = true
                                }
                            },
                            onToggleSelection = { id ->
                                selectedItems = if (selectedItems.contains(id)) {
                                    selectedItems - id
                                } else {
                                    selectedItems + id
                                }
                            },
                            isAudioPlaying = playbackState.isPlaying,
                            currentPlayingAudioId = playbackState.audioId,
                            currentPlayingTrackIndex = playbackState.trackIndex,
                            searchQuery = searchQuery,
                            audioSearchMatchingTracks = audioSearchMatchingTracks,
                            onAudioTrackClick = { audio, trackIndex ->
                                // 根据显示模式决定点击行为
                                if (audioDisplayMode == AudioDisplayMode.SINGLES) {
                                    // 单曲模式：直接播放
                                    currentScreen = Screen.AudioPlayer(audio.id, trackIndex, 0L)
                                } else {
                                    // 专辑模式：进入专辑详情页并高亮该曲目
                                    currentScreen = Screen.Details(MediaType.AUDIO, audio.id, trackIndex)
                                }
                            },
                            onAudioTrackLongClick = { audio, trackIndex ->
                                if (!isMultiSelectMode) {
                                    longPressedItem = audio
                                    longPressedTrackIndex = trackIndex
                                    showHomeLongPressDialog = true
                                }
                            },
                            onNavigateToPlayer = {
                                playbackState.audioId?.let { audioId ->
                                    // 传入 -1 作为位置，表示不需要 seek，保持当前播放位置
                                    currentScreen = Screen.AudioPlayer(audioId, playbackState.trackIndex, -1L, showLyricsInitially = true)
                                }
                            }
                        )
                    }
                    is Screen.Details -> {
                        when (screen.mediaType) {
                            MediaType.COMIC -> {
                                val comic = comicHistoryList.find { it.id == screen.mediaId }
                                if (comic != null) {
                                    ComicDetailScreen(
                                        paddingValues = paddingValues,
                                        comic = comic,
                                        overlayAlpha = detailOverlayAlpha,
                                        appLanguage = appLanguage,
                                        onChapterClick = { chapter, index ->
                                            val initialIndex = if (comic.lastReadIndex == 0) 0 else if (index == comic.lastReadChapterIndex) comic.lastReadIndex else 0
                                            currentScreen = Screen.ComicReader(comic.id, index, initialIndex)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { currentScreen = Screen.Home }
                                }
                            }
                            MediaType.NOVEL -> {
                                val novel = novelHistoryList.find { it.id == screen.mediaId }
                                if (novel != null) {
                                    LaunchedEffect(novel.id) {
                                        if (novel.chapters.size == 1 && isWholeFileChapter(novel.chapters.first())) {
                                            val rawText = readNovelText(context, novel.uriString.toUri())
                                            val parsed = rawText?.let { parseNovelChapters(it, novel.uriString, novel.name) }
                                            if (parsed != null && parsed.isNotEmpty()) {
                                                val shouldUpdate = parsed.size != novel.chapters.size ||
                                                    parsed.firstOrNull()?.endIndex != novel.chapters.firstOrNull()?.endIndex
                                                if (shouldUpdate) {
                                                    updateNovelItem(novel.id) { it.copy(chapters = parsed) }
                                                }
                                            }
                                        }
                                    }
                                    NovelDetailScreen(
                                        paddingValues = paddingValues,
                                        novel = novel,
                                        overlayAlpha = detailOverlayAlpha,
                                        appLanguage = appLanguage,
                                        onChapterClick = { chapter, index ->
                                            val initialPosition = if (index == novel.lastReadChapterIndex) novel.lastReadScrollPosition else 0
                                            currentScreen = Screen.NovelReader(novel.id, index, initialPosition)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { currentScreen = Screen.Home }
                                }
                            }
                            MediaType.AUDIO -> {
                                val audio = audioHistoryList.find { it.id == screen.mediaId }
                                if (audio != null) {
                                    AudioDetailScreen(
                                        paddingValues = paddingValues,
                                        audio = audio,
                                        overlayAlpha = detailOverlayAlpha,
                                        appLanguage = appLanguage,
                                        currentPlayingAudioId = playbackState.audioId,
                                        currentPlayingTrackIndex = playbackState.trackIndex,
                                        isPlaying = playbackState.isPlaying,
                                        highlightTrackIndex = screen.highlightTrackIndex,
                                        onTrackClick = { track, index ->
                                            currentScreen = Screen.AudioPlayer(audio.id, index, 0L)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { currentScreen = Screen.Home }
                                }
                            }
                        }
                    }
                    is Screen.ComicReader -> {
                        val comic = comicHistoryList.find { it.id == screen.comicId }
                        if (comic == null) {
                            LaunchedEffect(Unit) { currentScreen = Screen.Home }
                        } else {
                            // 使用 key 绑定到漫画+章节，确保切换时状态正确重置
                            var images by remember(screen.comicId, screen.chapterIndex) { 
                                mutableStateOf<List<Uri>?>(null) 
                            }
                            val chapter = comic.chapters.getOrNull(screen.chapterIndex)
                            
                            // 每个漫画+章节独立的滚动状态
                            val readerListState = rememberLazyListState(
                                initialFirstVisibleItemIndex = screen.initialScrollIndex.coerceAtLeast(0)
                            )

                            LaunchedEffect(key1 = screen.comicId, key2 = screen.chapterIndex) {
                                if (chapter != null) {
                                    loadComicImages(context, chapter.uriString.toUri(), chapter.sourceType, chapter.internalPath) { loadedImages ->
                                        images = loadedImages
                                    }
                                } else {
                                    images = emptyList()
                                    Toast.makeText(context, "错误：找不到章节", Toast.LENGTH_SHORT).show()
                                }
                            }

                            // 漫画阅读器中隐藏系统状态栏
                            DisposableEffect(Unit) {
                                val activity = context as? Activity
                                val window = activity?.window
                                val decorView = window?.decorView
                                if (window != null && decorView != null) {
                                    val controller = WindowInsetsControllerCompat(window, decorView)
                                    WindowCompat.setDecorFitsSystemWindows(window, false)
                                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                }
                                onDispose {
                                    // 退出阅读器时恢复系统状态栏
                                    if (window != null && decorView != null) {
                                        val controller = WindowInsetsControllerCompat(window, decorView)
                                        WindowCompat.setDecorFitsSystemWindows(window, true)
                                        controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                    }
                                }
                            }

                            DisposableEffect(Unit) {
                                onDispose {
                                    val currentIndex = readerListState.firstVisibleItemIndex
                                    val shouldSave = screen.chapterIndex != comic.lastReadChapterIndex ||
                                        currentIndex != comic.lastReadIndex
                                    if (shouldSave) {
                                        saveComicReaderProgress(
                                            context,
                                            comicHistoryList,
                                            screen.comicId,
                                            screen.chapterIndex,
                                            currentIndex
                                        )?.let {
                                            comicHistoryList = applySort(it, sortOption)
                                        }
                                    }
                                    // 清理漫画图片缓存 (所有类型)
                                    val chapter = comic.chapters.getOrNull(screen.chapterIndex)
                                    if (chapter != null) {
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            clearComicImageCache(context, chapter.uriString.toUri(), chapter.internalPath)
                                        }
                                    }
                                }
                            }

                            if (images == null) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("正在加载图片...")
                                }
                            } else if (images!!.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("本章节没有任何图片", color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { currentScreen = Screen.Details(MediaType.COMIC, screen.comicId) }) {
                                        Text("返回详情页")
                                    }
                                }
                            } else {
                                val chapterName = chapter?.name ?: "第${screen.chapterIndex + 1}章"
                                ReaderScreen(
                                    paddingValues = if (isBarsVisible) paddingValues else PaddingValues(0.dp),
                                    images = images!!,
                                    lazyListState = readerListState,
                                    initialIndex = screen.initialScrollIndex,
                                    isBarsVisible = isBarsVisible,
                                    onToggleBars = { isBarsVisible = !isBarsVisible },
                                    chapterName = chapterName,
                                    chapterIndex = screen.chapterIndex,
                                    totalChapters = comic.chapters.size,
                                    appLanguage = appLanguage,
                                    onPrevChapter = if (screen.chapterIndex > 0) {
                                        {
                                            // 保存当前进度
                                            saveComicReaderProgress(
                                                context, comicHistoryList, screen.comicId,
                                                screen.chapterIndex, readerListState.firstVisibleItemIndex
                                            )?.let { comicHistoryList = applySort(it, sortOption) }
                                            // 切换到上一章
                                            currentScreen = Screen.ComicReader(comic.id, screen.chapterIndex - 1, 0)
                                        }
                                    } else null,
                                    onNextChapter = if (screen.chapterIndex < comic.chapters.size - 1) {
                                        {
                                            // 保存当前进度
                                            saveComicReaderProgress(
                                                context, comicHistoryList, screen.comicId,
                                                screen.chapterIndex, readerListState.firstVisibleItemIndex
                                            )?.let { comicHistoryList = applySort(it, sortOption) }
                                            // 切换到下一章
                                            currentScreen = Screen.ComicReader(comic.id, screen.chapterIndex + 1, 0)
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                    is Screen.NovelReader -> {
                        val novel = novelHistoryList.find { it.id == screen.novelId }
                        if (novel == null) {
                            LaunchedEffect(Unit) { currentScreen = Screen.Home }
                        } else {
                            NovelReaderScreen(
                                paddingValues = paddingValues,
                                novel = novel,
                                chapterIndex = screen.chapterIndex,
                                initialScrollPosition = screen.initialScrollPosition,
                                onToggleBars = { isBarsVisible = !isBarsVisible },
                                isBarsVisible = isBarsVisible,
                                onProgressSave = { chapterIndex, scrollPosition ->
                                    if (chapterIndex != novel.lastReadChapterIndex ||
                                        scrollPosition != novel.lastReadScrollPosition
                                    ) {
                                        updateNovelItem(novel.id) {
                                            it.copy(
                                                lastReadChapterIndex = chapterIndex,
                                                lastReadScrollPosition = scrollPosition,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        }
                                    }
                                },
                                onChaptersUpdate = { chapters, newIndex ->
                                    val safeIndex = newIndex.coerceIn(0, maxOf(0, chapters.size - 1))
                                    updateNovelItem(novel.id) {
                                        it.copy(
                                            chapters = chapters,
                                            lastReadChapterIndex = safeIndex
                                        )
                                    }
                                }
                            )
                        }
                    }
                    is Screen.AudioPlayer -> {
                        val audio = audioHistoryList.find { it.id == screen.audioId }
                        if (audio == null) {
                            LaunchedEffect(Unit) { currentScreen = Screen.Home }
                        } else {
                            AudioPlayerScreen(
                                paddingValues = paddingValues,
                                audio = audio,
                                playlist = audioHistoryList,
                                trackIndex = screen.trackIndex,
                                initialPosition = screen.initialPosition,
                                onToggleBars = { isBarsVisible = !isBarsVisible },
                                isBarsVisible = isBarsVisible,
                                appLanguage = appLanguage,
                                onTrackChange = { newIndex ->
                                    currentScreen = Screen.AudioPlayer(audio.id, newIndex, 0L)
                                },
                                onAudioChange = { newAudioId, newIndex ->
                                    currentScreen = Screen.AudioPlayer(newAudioId, newIndex, 0L)
                                },
                                onUpdateBackground = { newBackgroundUri ->
                                    updateAudioItem(audio.id) { it.copy(customBackgroundUriString = newBackgroundUri) }
                                },
                                showLyricsInitially = screen.showLyricsInitially
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHomeLongPressDialog && longPressedItem != null) {
        val item = longPressedItem!!
        val itemName = if (longPressedTrackIndex != null && item is AudioHistory) {
            // 单曲模式下显示歌曲名
            item.tracks.getOrNull(longPressedTrackIndex!!)?.name ?: item.name
        } else {
            item.name
        }
        val itemIsNsfw = item.isNsfw
        // 单曲模式下检查 track 级别的收藏状态
        val itemIsFavorite = if (longPressedTrackIndex != null && item is AudioHistory) {
            item.tracks.getOrNull(longPressedTrackIndex!!)?.isFavorite ?: false
        } else {
            item.isFavorite
        }
        AlertDialog(
            onDismissRequest = { 
                showHomeLongPressDialog = false
                longPressedTrackIndex = null
            },
            icon = {
                when (currentMediaType) {
                    MediaType.COMIC -> Icon(Icons.Rounded.MenuBook, null)
                    MediaType.NOVEL -> Icon(Icons.Rounded.Menu, null)
                    MediaType.AUDIO -> Icon(Icons.Rounded.Headphones, null)
                }
            },
            title = { Text(itemName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (itemIsNsfw) {
                        Text("当前状态：隐藏内容", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("当前状态：公开可见", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (item) {
                        is ComicHistory -> {
                            if (!isHiddenModeUnlocked && !item.isNsfw) {
                                if (!hasPassword(context)) {
                                    Toast.makeText(context, "请先点击顶部锁图标设置密码", Toast.LENGTH_LONG).show()
                                } else {
                                    val newItem = item.copy(isNsfw = true)
                                    comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                                    Toast.makeText(context, "已设为隐藏，请进入隐藏模式查看", Toast.LENGTH_SHORT).show()
                                    showHomeLongPressDialog = false
                                }
                            } else {
                                val newItem = item.copy(isNsfw = !item.isNsfw)
                                comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                                showHomeLongPressDialog = false
                            }
                        }
                        is NovelHistory -> {
                            if (!isHiddenModeUnlocked && !item.isNsfw) {
                                if (!hasPassword(context)) {
                                    Toast.makeText(context, "请先点击顶部锁图标设置密码", Toast.LENGTH_LONG).show()
                                } else {
                                    updateNovelItem(item.id) { it.copy(isNsfw = true) }
                                    Toast.makeText(context, "已设为隐藏，请进入隐藏模式查看", Toast.LENGTH_SHORT).show()
                                    showHomeLongPressDialog = false
                                }
                            } else {
                                updateNovelItem(item.id) { it.copy(isNsfw = !it.isNsfw) }
                                showHomeLongPressDialog = false
                            }
                        }
                        is AudioHistory -> {
                            if (!isHiddenModeUnlocked && !item.isNsfw) {
                                if (!hasPassword(context)) {
                                    Toast.makeText(context, "请先点击顶部锁图标设置密码", Toast.LENGTH_LONG).show()
                                } else {
                                    updateAudioItem(item.id) { it.copy(isNsfw = true) }
                                    Toast.makeText(context, "已设为隐藏，请进入隐藏模式查看", Toast.LENGTH_SHORT).show()
                                    showHomeLongPressDialog = false
                                }
                            } else {
                                updateAudioItem(item.id) { it.copy(isNsfw = !it.isNsfw) }
                                showHomeLongPressDialog = false
                            }
                        }
                    }
                }) {
                    Text(if (itemIsNsfw) "设为公开" else "设为隐藏", color = if (itemIsNsfw) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        when (item) {
                            is ComicHistory -> {
                                val newItem = item.copy(isFavorite = !item.isFavorite)
                                comicHistoryList = updateComicHistoryAndSort(context, comicHistoryList, newItem, sortOption)
                            }
                            is NovelHistory -> {
                                updateNovelItem(item.id) { it.copy(isFavorite = !it.isFavorite) }
                            }
                            is AudioHistory -> {
                                // 单曲模式下更新单曲收藏，否则更新专辑收藏
                                if (longPressedTrackIndex != null) {
                                    updateAudioTrackFavorite(item.id, longPressedTrackIndex!!)
                                } else {
                                    updateAudioItem(item.id) { it.copy(isFavorite = !it.isFavorite) }
                                }
                            }
                        }
                        showHomeLongPressDialog = false
                        longPressedTrackIndex = null
                    }) {
                        Text(if (itemIsFavorite) "取消收藏" else "加入收藏")
                    }
                    TextButton(onClick = {
                        itemToDelete = item
                        showDeleteConfirmDialog = true
                    }) {
                        Text("删除")
                    }
                }
            }
        )
    }
}


// Note: Utility functions moved to utils/FileUtils.kt, utils/PreferencesManager.kt, utils/Helpers.kt
// Note: UI components moved to ui/components/ and ui/screens/
