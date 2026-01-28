package com.alendawang.manhua.ui.screens

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.TypedValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.alendawang.manhua.R
import com.alendawang.manhua.model.*
import com.alendawang.manhua.ui.components.*
import com.alendawang.manhua.utils.*
import com.alendawang.manhua.viewmodel.NovelReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
fun NovelReaderScreen(
    paddingValues: PaddingValues,
    novel: NovelHistory,
    chapterIndex: Int,
    initialScrollPosition: Int,
    onToggleBars: () -> Unit,
    isBarsVisible: Boolean,
    onProgressSave: (Int, Int) -> Unit,
    onChaptersUpdate: (List<NovelChapter>, Int) -> Unit
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val preloadScope = rememberCoroutineScope()
    val editScope = rememberCoroutineScope()
    val viewModel = remember { NovelReaderViewModel(context, onProgressSave) }
    data class ChapterBlock(
        val chapterIndex: Int,
        val title: String,
        val paragraphs: List<String>,
        val isEpub: Boolean = false,
        val epubParts: List<CharSequence>? = null
    )

    val chapterCache = remember { mutableMapOf<String, String>() }
    val loadedChapters = remember(novel.id) { mutableStateListOf<ChapterBlock>() }
    val loadingChapters = remember(novel.id) { mutableStateListOf<Int>() }

    var currentChapterText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    var isProgressDragging by remember { mutableStateOf(false) }
    var progressDragValue by remember { mutableFloatStateOf(0f) }
    var initialScrollApplied by remember(novel.id, chapterIndex) { mutableStateOf(false) }
    val sessionStartTime = remember { System.currentTimeMillis() }
    var sessionMinutes by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val backgroundPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.updateConfig(
                viewModel.config.copy(customBackgroundUriString = uri.toString())
            )
        }
    }

    suspend fun loadChapterBlock(index: Int) {
        if (index !in novel.chapters.indices) return
        if (loadedChapters.any { it.chapterIndex == index } || loadingChapters.contains(index)) return
        loadingChapters.add(index)
        
        withContext(Dispatchers.IO) {
            val chapter = novel.chapters[index]
            val block = if (chapter.isEpubChapter) {
                // EPUB handling - 图片已在getEpubChapterContent中提取到缓存
                val html = getEpubChapterContent(context, chapter.uriString.toUri(), chapter.internalPath ?: "")
                if (html.isNotEmpty()) {
                    val screenWidth = context.resources.displayMetrics.widthPixels - 
                        (viewModel.config.horizontalPadding * 2 * context.resources.displayMetrics.density).toInt()
                    
                    // 优化的ImageGetter：从缓存文件加载，使用下采样
                    val imageGetter = android.text.Html.ImageGetter { source ->
                        try {
                            val filePath = if (source.startsWith("file://")) source.substring(7) else source
                            val file = java.io.File(filePath)
                            if (!file.exists()) return@ImageGetter null
                            
                            // 先获取图片尺寸
                            val options = android.graphics.BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeFile(filePath, options)
                            
                            // 计算下采样率
                            var inSampleSize = 1
                            if (options.outWidth > screenWidth) {
                                while ((options.outWidth / inSampleSize) > screenWidth * 2) {
                                    inSampleSize *= 2
                                }
                            }
                            
                            // 解码图片
                            options.inJustDecodeBounds = false
                            options.inSampleSize = inSampleSize
                            val bitmap = android.graphics.BitmapFactory.decodeFile(filePath, options)
                            
                            if (bitmap != null) {
                                val d = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                val ratio = d.intrinsicHeight.toFloat() / d.intrinsicWidth.toFloat()
                                val height = (screenWidth * ratio).toInt()
                                d.setBounds(0, 0, screenWidth, height)
                                d
                            } else null
                        } catch (e: Exception) { null }
                    }
                    
                    val spanned = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
                    val parts = splitSpanned(spanned, 3000)
                    ChapterBlock(index, chapter.name, List(parts.size) { "EPUB_PART" }, true, parts)
                } else {
                    ChapterBlock(index, chapter.name, listOf("Content Error"), true, listOf(android.text.SpannableString("Error loading content")))
                }
            } else {
                // TXT handling
                val cacheKey = chapter.uriString
                val fullText = chapterCache[cacheKey]
                    ?: readNovelText(context, chapter.uriString.toUri())
                    ?: ""
                if (fullText.isNotEmpty()) {
                    chapterCache[cacheKey] = fullText
                }
                val chapterContent = extractChapterText(fullText, chapter)
                val paragraphs = chapterContent.split("\n\n")
                ChapterBlock(index, chapter.name, paragraphs)
            }
            
            withContext(Dispatchers.Main) {
                val insertAt = loadedChapters.indexOfFirst { it.chapterIndex > index }
                    .let { if (it == -1) loadedChapters.size else it }
                loadedChapters.add(insertAt, block)
                loadingChapters.remove(index)
            }
        }
    }

    // 连贯滚动：先加载当前章，再预加载下一章
    LaunchedEffect(novel.id, chapterIndex) {
        loadedChapters.clear()
        loadingChapters.clear()
        initialScrollApplied = false
        isLoading = true
        loadChapterBlock(chapterIndex)
        isLoading = false
        // Preload next 3 chapters
        (1..3).forEach { offset ->
            val nextIndex = chapterIndex + offset
            if (nextIndex <= novel.chapters.lastIndex) {
                preloadScope.launch { loadChapterBlock(nextIndex) }
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val availableWidthPx = with(density) {
        val screenWidthPx = LocalConfiguration.current.screenWidthDp.dp.roundToPx()
        (screenWidthPx - (viewModel.config.horizontalPadding * 2).dp.roundToPx()).coerceAtLeast(1)
    }
    val paragraphSpacingPx = with(density) { viewModel.config.paragraphSpacing.dp.roundToPx() }

    val chapterStarts by remember(loadedChapters) {
        derivedStateOf {
            var cursor = 0
            loadedChapters.map { block ->
                val start = cursor
                cursor += block.paragraphs.size
                Triple(block.chapterIndex, start, block.paragraphs.size)
            }
        }
    }

    val paragraphs by remember(loadedChapters) {
        derivedStateOf { loadedChapters.flatMap { it.paragraphs } }
    }

    val currentChapterIndex by remember(chapterStarts, listState) {
        derivedStateOf {
            val itemIndex = listState.firstVisibleItemIndex
            chapterStarts.lastOrNull { itemIndex >= it.second }?.first ?: chapterIndex
        }
    }

    val currentChapter = novel.chapters.getOrNull(currentChapterIndex)

    DisposableEffect(Unit) {
        onDispose {
            val chapterStart = chapterStarts.firstOrNull { it.first == currentChapterIndex }?.second ?: 0
            val relativeIndex = (listState.firstVisibleItemIndex - chapterStart).coerceAtLeast(0)
            val packed = packScrollProgress(
                relativeIndex,
                listState.firstVisibleItemScrollOffset
            )
            onProgressSave(currentChapterIndex, packed)
        }
    }

    LaunchedEffect(currentChapterIndex) {
        val chapter = currentChapter
        if (chapter != null) {
            // EPUB章节内容已通过loadChapterBlock加载到loadedChapters，跳过冗余的文本读取
            if (chapter.isEpubChapter) {
                // 只更新章节标题，避免重复加载
                viewModel.chapterTitle = chapter.name
                currentChapterText = null  // EPUB不需要纯文本编辑功能
            } else {
                // TXT章节：正常加载
                val cacheKey = chapter.uriString
                val fullText = chapterCache[cacheKey]
                    ?: readNovelText(context, chapter.uriString.toUri())
                    ?: ""
                if (fullText.isNotEmpty()) {
                    chapterCache[cacheKey] = fullText
                }
                val chapterContent = extractChapterText(fullText, chapter)
                currentChapterText = chapterContent
                viewModel.loadChapter(chapterContent, chapter.name)
            }
        } else {
            currentChapterText = null
            viewModel.chapterTitle = ""
        }
    }

    // 自动隐藏 UI（3秒后）
    LaunchedEffect(viewModel.showMenu) {
        if (viewModel.showMenu) {
            kotlinx.coroutines.delay(5000)
            viewModel.showMenu = false
        }
    } 

    LaunchedEffect(viewModel.showMenu, isBarsVisible) {
        if (viewModel.showMenu != isBarsVisible) {
            onToggleBars()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val elapsed = System.currentTimeMillis() - sessionStartTime
            sessionMinutes = (elapsed / 60000L).toInt()
            kotlinx.coroutines.delay(1000)
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            // 阅读器中隐藏系统状态栏
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose {
            // 退出阅读器时恢复系统状态栏
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, view)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ReaderBackground(
            config = viewModel.config,
            modifier = Modifier.fillMaxSize()
        )
        // 内容区域
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = viewModel.config.customTextColor?.let { Color(it) } ?: viewModel.config.backgroundColor.textColor)
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载文本...", color = viewModel.config.customTextColor?.let { Color(it) } ?: viewModel.config.backgroundColor.textColor)
            }
        } else if (paragraphs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = viewModel.config.customTextColor?.let { Color(it) } ?: viewModel.config.backgroundColor.textColor)
                Spacer(modifier = Modifier.height(16.dp))
                Text("本章内容为空", color = viewModel.config.customTextColor?.let { Color(it) } ?: viewModel.config.backgroundColor.textColor)
            }
        } else {
            val textStyle = remember(viewModel.config) {
                TextStyle(
                    fontSize = viewModel.config.fontSize.sp,
                    lineHeight = (viewModel.config.fontSize * viewModel.config.lineHeightRatio).sp,
                    color = viewModel.config.customTextColor?.let { Color(it) } ?: viewModel.config.backgroundColor.textColor,
                    fontFamily = when (viewModel.config.fontType) {
                        FontType.Serif -> FontFamily.Serif
                        FontType.SansSerif -> FontFamily.SansSerif
                        FontType.Monospace -> FontFamily.Monospace
                        FontType.System -> FontFamily.Default
                    }
                )
            }
            val scrollProgress by remember(paragraphs) {
                derivedStateOf {
                    val totalItems = paragraphs.size
                    if (totalItems == 0) {
                        0f
                    } else {
                        val firstIndex = listState.firstVisibleItemIndex
                        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                        val itemSize = (visibleItem?.size ?: 1).coerceAtLeast(1)
                        val offsetFraction = listState.firstVisibleItemScrollOffset.toFloat() / itemSize
                        val current = (firstIndex + offsetFraction).coerceIn(0f, totalItems.toFloat())
                        (current / totalItems.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }

            val autoAdvanceThresholdPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            val isAtEnd by remember(paragraphs, listState) {
                derivedStateOf {
                    if (paragraphs.isEmpty()) return@derivedStateOf false
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                    lastVisible.index >= paragraphs.lastIndex &&
                        (lastVisible.offset + lastVisible.size) <= (layoutInfo.viewportEndOffset + autoAdvanceThresholdPx)
                }
            }

            LaunchedEffect(isAtEnd) {
                if (isAtEnd) {
                    val lastLoadedIndex = loadedChapters.maxOfOrNull { it.chapterIndex } ?: chapterIndex
                    if (lastLoadedIndex < novel.chapters.lastIndex) {
                        preloadScope.launch { loadChapterBlock(lastLoadedIndex + 1) }
                    }
                }
            }

            LaunchedEffect(
                chapterStarts,
                initialScrollPosition,
                availableWidthPx,
                viewModel.config,
                paragraphs.size,
                initialScrollApplied
            ) {
                if (initialScrollApplied || paragraphs.isEmpty()) return@LaunchedEffect
                val chapterStart = chapterStarts.firstOrNull { it.first == chapterIndex }?.second ?: return@LaunchedEffect
                val chapterParagraphs = loadedChapters.firstOrNull { it.chapterIndex == chapterIndex }?.paragraphs
                    ?: return@LaunchedEffect

                val (index, offset) = if (isPackedProgress(initialScrollPosition)) {
                    unpackScrollProgress(initialScrollPosition)
                } else if (initialScrollPosition > 0) {
                    estimateIndexFromLegacyPx(
                        legacyPx = initialScrollPosition,
                        paragraphs = chapterParagraphs,
                        textMeasurer = textMeasurer,
                        textStyle = textStyle,
                        widthPx = availableWidthPx,
                        paragraphSpacingPx = paragraphSpacingPx
                    )
                } else {
                    0 to 0
                }

                val targetIndex = (chapterStart + index).coerceIn(0, paragraphs.lastIndex)
                listState.scrollToItem(targetIndex, offset)
                initialScrollApplied = true
            }

            fun requestChapterScroll(targetIndex: Int, targetPosition: Int) {
                scrollScope.launch {
                    loadChapterBlock(targetIndex)
                    if (targetIndex + 1 <= novel.chapters.lastIndex) {
                        preloadScope.launch { loadChapterBlock(targetIndex + 1) }
                    }
                    if (paragraphs.isEmpty()) return@launch
                    val start = chapterStarts.firstOrNull { it.first == targetIndex }?.second
                        ?: snapshotFlow { chapterStarts.firstOrNull { it.first == targetIndex }?.second }
                            .filter { it != null }
                            .first()!!
                    val chapterParagraphs = loadedChapters.firstOrNull { it.chapterIndex == targetIndex }?.paragraphs
                        ?: emptyList()
                    val (relIndex, offset) = if (isPackedProgress(targetPosition)) {
                        unpackScrollProgress(targetPosition)
                    } else if (targetPosition > 0) {
                        estimateIndexFromLegacyPx(
                            legacyPx = targetPosition,
                            paragraphs = chapterParagraphs,
                            textMeasurer = textMeasurer,
                            textStyle = textStyle,
                            widthPx = availableWidthPx,
                            paragraphSpacingPx = paragraphSpacingPx
                        )
                    } else {
                        0 to 0
                    }
                    val targetItemIndex = (start + relIndex).coerceIn(0, paragraphs.lastIndex)
                    listState.scrollToItem(targetItemIndex, offset)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            viewModel.showMenu = !viewModel.showMenu
                            if (viewModel.showMenu != isBarsVisible) {
                                onToggleBars()
                            }
                        }
                    },
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = viewModel.config.horizontalPadding.dp,
                    vertical = 32.dp
                )
            ) {
                itemsIndexed(
                    items = paragraphs,
                    key = { index, _ ->
                        val blockInfo = chapterStarts.find { index >= it.second && index < it.second + it.third }
                        if (blockInfo != null) {
                            "ch${blockInfo.first}_p${index - blockInfo.second}"
                        } else {
                            "unknown_$index"
                        }
                    }
                ) { index, paragraph ->
                    val blockInfo = chapterStarts.find { index >= it.second && index < it.second + it.third }
                    val block = if (blockInfo != null) loadedChapters.find { it.chapterIndex == blockInfo.first } else null
                    
                    if (block?.isEpub == true && block.epubParts != null) {
                        val localIndex = if (blockInfo != null) index - blockInfo.second else 0
                        val part = block.epubParts.getOrNull(localIndex)
                        
                        if (part != null) {
                            AndroidView<android.widget.TextView>(
                                factory = { ctx ->
                                    android.widget.TextView(ctx).apply {
                                        movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                        setTextIsSelectable(true)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
                                            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                                        }
                                        setOnClickListener {
                                            viewModel.showMenu = !viewModel.showMenu
                                        }
                                    }
                                },
                                update = { view ->
                                    // 使用post延迟文本设置，避免阻塞重组过程
                                    view.post {
                                        view.text = part
                                        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, viewModel.config.fontSize)
                                        view.setTextColor(viewModel.config.backgroundColor.textColor.toArgb())
                                    }
                                    view.setOnClickListener {
                                        viewModel.showMenu = !viewModel.showMenu
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (localIndex == block.epubParts.lastIndex) 32.dp else 0.dp)
                            )
                        }
                    } else {
                        if (paragraph.isBlank()) {
                            Spacer(modifier = Modifier.height(viewModel.config.paragraphSpacing.dp))
                        } else {
                            Text(text = paragraph, style = textStyle)
                            if (index != paragraphs.lastIndex) {
                                Spacer(modifier = Modifier.height(viewModel.config.paragraphSpacing.dp))
                            }
                        }
                    }
                }
            }

            // 底部状态栏（Legado 风格）
            AnimatedVisibility(
                visible = viewModel.showMenu,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.chapterTitle,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { showChapterList = true }) {
                                Icon(Icons.Rounded.List, contentDescription = "目录", tint = Color.White)
                            }
                            IconButton(onClick = {
                                val chapter = currentChapter
                                if (chapter == null) return@IconButton
                                editorText = currentChapterText.orEmpty()
                                showEditor = true
                                viewModel.showMenu = false
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "编辑", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.showSettings = true }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = Color.White)
                            }
                        }
                        // 阅读进度条
                        val currentChapterInfo = chapterStarts.firstOrNull { it.first == currentChapterIndex }
                        val currentStart = currentChapterInfo?.second ?: 0
                        val currentSize = currentChapterInfo?.third ?: paragraphs.size
                        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                        val itemSize = (visibleItem?.size ?: 1).coerceAtLeast(1)
                        val offsetFraction = (listState.firstVisibleItemScrollOffset.toFloat() / itemSize)
                            .coerceIn(0f, 1f)
                        val currentProgress = if (currentSize <= 0) {
                            0f
                        } else {
                            val relativeIndex = (listState.firstVisibleItemIndex - currentStart)
                                .coerceIn(0, currentSize - 1)
                            val current = (relativeIndex + offsetFraction).coerceIn(0f, currentSize.toFloat())
                            (current / currentSize.toFloat()).coerceIn(0f, 1f)
                        }
                        val displayProgress = if (isProgressDragging) {
                            progressDragValue.coerceIn(0f, 1f)
                        } else {
                            currentProgress.coerceIn(0f, 1f)
                        }
                        Slider(
                            value = displayProgress,
                            onValueChange = { value ->
                                isProgressDragging = true
                                progressDragValue = value.coerceIn(0f, 1f)
                                if (paragraphs.isNotEmpty() && currentSize > 0) {
                                    val target = (progressDragValue * currentSize).coerceIn(0f, currentSize.toFloat())
                                    val relativeIndex = target.toInt().coerceIn(0, currentSize - 1)
                                    val fraction = (target - relativeIndex).coerceIn(0f, 1f)
                                    val targetIndex = (currentStart + relativeIndex).coerceIn(0, paragraphs.lastIndex)
                                    val targetOffset = (itemSize * fraction).roundToInt()
                                    scrollScope.launch { listState.scrollToItem(targetIndex, targetOffset) }
                                }
                            },
                            onValueChangeFinished = {
                                isProgressDragging = false
                                if (paragraphs.isNotEmpty() && currentSize > 0) {
                                    val target = (progressDragValue * currentSize).coerceIn(0f, currentSize.toFloat())
                                    val relativeIndex = target.toInt().coerceIn(0, currentSize - 1)
                                    val fraction = (target - relativeIndex).coerceIn(0f, 1f)
                                    val targetIndex = (currentStart + relativeIndex).coerceIn(0, paragraphs.lastIndex)
                                    val targetOffset = (itemSize * fraction).roundToInt()
                                    scrollScope.launch { listState.scrollToItem(targetIndex, targetOffset) }
                                }
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        // 状态信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 电量
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.BatteryFull,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${getBatteryLevel(context)}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            // 阅读进度
                            Text(
                                text = "${(displayProgress * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            // 时间
                            Text(
                                text = getCurrentTime(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "进度: ${(displayProgress * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "时长: ${sessionMinutes}分钟",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 设置菜单
            if (viewModel.showSettings) {
                NovelSettingsDialog(
                    config = viewModel.config,
                    onDismiss = { viewModel.showSettings = false },
                    onConfigChange = { viewModel.updateConfig(it) },
                    onPickBackground = { backgroundPickerLauncher.launch(arrayOf("image/*")) },
                    onClearBackground = { viewModel.updateConfig(viewModel.config.copy(customBackgroundUriString = null)) }
                )
            }

            if (showChapterList) {
                ModalBottomSheet(onDismissRequest = { showChapterList = false }) {
                    val chapterListState = rememberLazyListState()
                    LaunchedEffect(currentChapterIndex) {
                        chapterListState.scrollToItem(currentChapterIndex)
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("章节目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                            LazyColumn(
                                state = chapterListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(novel.chapters) { index, chapter ->
                                    val isCurrent = index == currentChapterIndex
                                    ListItem(
                                        headlineContent = { Text(chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { if (isCurrent) Text("当前阅读") else null },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showChapterList = false
                                                val targetPosition = if (index == novel.lastReadChapterIndex) {
                                                    novel.lastReadScrollPosition
                                                } else {
                                                    0
                                                }
                                                requestChapterScroll(index, targetPosition)
                                            }
                                    )
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

            if (showEditor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderBackground(
                        config = viewModel.config,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "编辑章节",
                                color = viewModel.config.backgroundColor.textColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row {
                                TextButton(onClick = { showEditor = false }) { Text("取消") }
                                TextButton(onClick = {
                                    val chapter = currentChapter
                                    if (chapter != null) {
                                        editScope.launch {
                                            val fullText = chapterCache[chapter.uriString]
                                                ?: readNovelText(context, chapter.uriString.toUri())
                                                ?: ""
                                            val updatedFullText = if (isWholeFileChapter(chapter)) {
                                                editorText
                                            } else {
                                                replaceChapterText(fullText, chapter, editorText)
                                            }
                                            saveNovelText(context, chapter.uriString.toUri(), updatedFullText)
                                            chapterCache[chapter.uriString] = updatedFullText

                                            val updatedChapters = parseNovelChapters(updatedFullText, chapter.uriString, novel.name)
                                            val updatedIndex = updatedChapters.indexOfFirst { it.name == chapter.name }
                                                .takeIf { it >= 0 } ?: currentChapterIndex
                                            val safeIndex = updatedIndex.coerceIn(0, maxOf(0, updatedChapters.size - 1))
                                            onChaptersUpdate(updatedChapters, safeIndex)

                                            val updatedChapter = updatedChapters.getOrNull(safeIndex)
                                            val updatedText = if (updatedChapter != null) {
                                                extractChapterText(updatedFullText, updatedChapter)
                                            } else {
                                                editorText
                                            }
                                            currentChapterText = updatedText
                                            if (updatedChapter != null) {
                                                viewModel.loadChapter(updatedText, updatedChapter.name)
                                            } else {
                                                viewModel.loadChapter(updatedText, chapter.name)
                                            }
                                            val updatedParagraphs = updatedText.split("\n\n")
                                            val loadedIndex = loadedChapters.indexOfFirst { it.chapterIndex == safeIndex }
                                            if (loadedIndex != -1) {
                                                loadedChapters[loadedIndex] = loadedChapters[loadedIndex].copy(
                                                    title = updatedChapter?.name ?: loadedChapters[loadedIndex].title,
                                                    paragraphs = updatedParagraphs
                                                )
                                            }
                                            showEditor = false
                                        }
                                    } else {
                                        showEditor = false
                                    }
                                }) { Text("保存") }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editorText,
                            onValueChange = { editorText = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                fontSize = viewModel.config.fontSize.sp,
                                lineHeight = (viewModel.config.fontSize * viewModel.config.lineHeightRatio).sp,
                                color = viewModel.config.backgroundColor.textColor,
                                fontFamily = when (viewModel.config.fontType) {
                                    FontType.Serif -> FontFamily.Serif
                                    FontType.SansSerif -> FontFamily.SansSerif
                                    FontType.Monospace -> FontFamily.Monospace
                                    FontType.System -> FontFamily.Default
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = viewModel.config.backgroundColor.textColor.copy(alpha = 0.4f),
                                unfocusedIndicatorColor = viewModel.config.backgroundColor.textColor.copy(alpha = 0.2f),
                                cursorColor = viewModel.config.backgroundColor.textColor,
                                focusedTextColor = viewModel.config.backgroundColor.textColor,
                                unfocusedTextColor = viewModel.config.backgroundColor.textColor
                            ),
                            singleLine = false
                        )
                    }
                }
            }
        }
    }
}

// --- 高性能文本页面 ---
@Composable
fun NovelContentPage(
    modifier: Modifier = Modifier,
    pageContent: PageContent?,
    config: ReaderConfig,
    onTapLeft: () -> Unit = {},
    onTapRight: () -> Unit = {},
    onTapCenter: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {}
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }

    Canvas(
        modifier = modifier
            .padding(
                horizontal = config.horizontalPadding.dp,
                vertical = 32.dp
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val screenWidth = size.width
                        when {
                            offset.x < screenWidth / 3 -> onTapLeft()
                            offset.x > screenWidth * 2 / 3 -> onTapRight()
                            else -> onTapCenter()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var dragDelta = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        dragDelta += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragDelta <= -swipeThresholdPx) {
                            onSwipeUp()
                        } else if (dragDelta >= swipeThresholdPx) {
                            onSwipeDown()
                        }
                        dragDelta = 0f
                    },
                    onDragCancel = { dragDelta = 0f }
                )
            }
    ) {
        val paint = android.graphics.Paint().apply {
            textSize = config.fontSize * density.density
            color = config.customTextColor ?: config.backgroundColor.textColor.toArgb()
            typeface = when (config.fontType) {
                FontType.Serif -> android.graphics.Typeface.SERIF
                FontType.SansSerif -> android.graphics.Typeface.SANS_SERIF
                FontType.Monospace -> android.graphics.Typeface.MONOSPACE
                FontType.System -> android.graphics.Typeface.DEFAULT
            }
            isAntiAlias = true
        }

        val availableWidth = size.width
        val lineHeight = config.fontSize * config.lineHeightRatio * density.density
        val paragraphSpacing = config.paragraphSpacing * density.density
        val lines = pageContent?.text?.split("\n") ?: emptyList()

        // 获取字体度量信息，设置正确的初始y位置
        val fontMetrics = paint.fontMetrics
        var y = -fontMetrics.ascent // 从第一行的基线开始
        for (line in lines) {
            if (line.isEmpty()) {
                y += paragraphSpacing
                continue
            }

            val words = splitTextToLines(line, paint, availableWidth)
            for (word in words) {
                (this.drawContext.canvas.nativeCanvas as android.graphics.Canvas).drawText(word, 0f, y, paint)
                y += lineHeight
            }
            y += paragraphSpacing
        }
    }
}

// --- 文本分行算法 ---
private fun splitTextToLines(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    var remaining = text

    while (remaining.isNotEmpty()) {
        val breakIndex = paint.breakText(remaining, true, maxWidth, null)
        if (breakIndex > 0) {
            // 确保不会在多字节字符中间断开
            var actualBreakIndex = breakIndex
            if (remaining.length > breakIndex) {
                val codePointAt = remaining.codePointAt(breakIndex)
                if (Character.isSupplementaryCodePoint(codePointAt) || codePointAt > 0x7F) {
                    // 向后查找安全的断点
                    var safePoint = breakIndex
                    while (safePoint > 0 && remaining[safePoint].isHighSurrogate()) {
                        safePoint--
                    }
                    if (safePoint > 0) {
                        actualBreakIndex = safePoint
                    }
                }
            }
            lines.add(remaining.substring(0, actualBreakIndex))
            remaining = remaining.substring(actualBreakIndex)
        } else {
            lines.add(remaining)
            break
        }
    }
    return lines
}
