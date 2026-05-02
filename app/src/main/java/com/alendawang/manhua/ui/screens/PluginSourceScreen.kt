package com.alendawang.manhua.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alendawang.manhua.engine.PluginSourceManager
import com.alendawang.manhua.engine.SearchResult
import com.alendawang.manhua.engine.SourceFetcher
import com.alendawang.manhua.model.plugin.LegadoSource
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PluginSourceScreen(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sources by remember { mutableStateOf(PluginSourceManager.getSources(context)) }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf<LegadoSource?>(null) }

    // 多选管理状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedUrls by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                // 多选模式顶栏
                TopAppBar(
                    title = { Text("已选 ${selectedUrls.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedUrls = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        // 全选
                        IconButton(onClick = {
                            selectedUrls = sources.map { it.bookSourceUrl }.toSet()
                        }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "全选")
                        }
                        // 反选
                        IconButton(onClick = {
                            val allUrls = sources.map { it.bookSourceUrl }.toSet()
                            selectedUrls = allUrls - selectedUrls
                        }) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "反选")
                        }
                        // 批量删除
                        IconButton(onClick = {
                            if (selectedUrls.isNotEmpty()) {
                                PluginSourceManager.deleteSources(context, selectedUrls)
                                sources = PluginSourceManager.getSources(context)
                                Toast.makeText(context, "已删除 ${selectedUrls.size} 个书源", Toast.LENGTH_SHORT).show()
                                selectedUrls = emptySet()
                                isMultiSelectMode = false
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                // 普通顶栏
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedSource != null) selectedSource?.bookSourceName ?: "测试" else title,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedSource != null) {
                                selectedSource = null
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedSource == null && !isMultiSelectMode) {
                FloatingActionButton(onClick = { showImportDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "导入书源")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (selectedSource == null) {
                // 书源列表
                if (sources.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无导入的书源\n请点击右下角按钮导入 JSON 规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sources, key = { it.bookSourceUrl }) { source ->
                            val isSelected = source.bookSourceUrl in selectedUrls
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                selectedUrls = if (isSelected) selectedUrls - source.bookSourceUrl
                                                else selectedUrls + source.bookSourceUrl
                                            } else {
                                                selectedSource = source
                                            }
                                        },
                                        onLongClick = {
                                            if (!isMultiSelectMode) {
                                                isMultiSelectMode = true
                                                selectedUrls = setOf(source.bookSourceUrl)
                                            }
                                        }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isMultiSelectMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedUrls = if (isSelected) selectedUrls - source.bookSourceUrl
                                                else selectedUrls + source.bookSourceUrl
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            source.bookSourceName.takeIf { it.isNotBlank() } ?: "未知源",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            source.bookSourceUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // 显示标签
                                        Row {
                                            if (source.searchUrl.isNotBlank()) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("搜索", style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(24.dp).padding(end = 4.dp)
                                                )
                                            }
                                            if (source.exploreUrl.isNotBlank()) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("发现", style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 书源详情页 (搜索 + 发现)
                SourceDetailView(source = selectedSource!!)
            }
        }
    }

    // 导入对话框
    if (showImportDialog) {
        var importJson by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入书源规则 (JSON)") },
            text = {
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text("粘贴 Legado JSON 规则或网络 URL") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 10
                )
            },
            confirmButton = {
                val coroutineScope = rememberCoroutineScope()
                var isImporting by remember { mutableStateOf(false) }
                TextButton(
                    onClick = {
                        if (importJson.isNotBlank() && !isImporting) {
                            isImporting = true
                            val input = importJson.trim()
                            coroutineScope.launch {
                                val result = if (input.startsWith("http://") || input.startsWith("https://")) {
                                    PluginSourceManager.importFromUrl(context, input)
                                } else {
                                    PluginSourceManager.importSources(context, input)
                                }
                                if (result.isSuccess) {
                                    Toast.makeText(context, "成功导入 ${result.getOrNull()} 个书源", Toast.LENGTH_SHORT).show()
                                    sources = PluginSourceManager.getSources(context)
                                    showImportDialog = false
                                } else {
                                    Toast.makeText(context, "导入失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                                isImporting = false
                            }
                        }
                    },
                    enabled = !isImporting
                ) {
                    Text(if (isImporting) "导入中..." else "导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 书源详情页 - 搜索 + 发现 Tab
 */
@Composable
fun SourceDetailView(source: LegadoSource) {
    val hasExplore = source.exploreUrl.isNotBlank()
    var selectedTab by remember { mutableStateOf(0) } // 0=搜索, 1=发现

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasExplore) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("搜索") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("发现") })
            }
        }

        when (selectedTab) {
            0 -> SearchTabView(source = source)
            1 -> if (hasExplore) ExploreTabView(source = source)
        }
    }
}

/**
 * 搜索标签页 - 支持分页加载
 */
@Composable
fun SearchTabView(source: LegadoSource) {
    val coroutineScope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // 监听滚动到底部 → 加载更多
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3 &&
                    searchResults.isNotEmpty() && !isLoadingMore && !isSearching && hasMore
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            isLoadingMore = true
            try {
                val nextPage = currentPage + 1
                val moreResults = SourceFetcher.search(source, keyword, nextPage)
                if (moreResults.isNotEmpty()) {
                    searchResults = searchResults + moreResults
                    currentPage = nextPage
                } else {
                    hasMore = false
                }
            } catch (_: Exception) {
                hasMore = false
            } finally {
                isLoadingMore = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索关键字") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (keyword.isBlank() || isSearching) return@Button
                isSearching = true
                errorMessage = null
                currentPage = 1
                hasMore = true
                searchResults = emptyList()
                coroutineScope.launch {
                    try {
                        searchResults = SourceFetcher.search(source, keyword, 1)
                        if (searchResults.isEmpty()) {
                            errorMessage = "未抓取到任何结果。"
                        }
                    } catch (e: Exception) {
                        errorMessage = "解析出错: ${e.message}"
                    } finally {
                        isSearching = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSearching) "正在搜索..." else "搜索")
        }

        Spacer(modifier = Modifier.height(8.dp))
        ResultListView(
            results = searchResults,
            isLoading = isSearching,
            isLoadingMore = isLoadingMore,
            errorMessage = errorMessage,
            listState = listState,
            source = source,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 发现标签页
 */
@Composable
fun ExploreTabView(source: LegadoSource) {
    val coroutineScope = rememberCoroutineScope()
    val kinds = remember { SourceFetcher.parseExploreKinds(source.exploreUrl) }
    var selectedKindUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // 分页加载
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3 &&
                    results.isNotEmpty() && !isLoadingMore && !isLoading && hasMore && selectedKindUrl != null
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && selectedKindUrl != null) {
            isLoadingMore = true
            try {
                val nextPage = currentPage + 1
                val moreResults = SourceFetcher.explore(source, selectedKindUrl!!, nextPage)
                if (moreResults.isNotEmpty()) {
                    results = results + moreResults
                    currentPage = nextPage
                } else {
                    hasMore = false
                }
            } catch (_: Exception) {
                hasMore = false
            } finally {
                isLoadingMore = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 分类按钮横向滚动
        if (kinds.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kinds) { (name, url) ->
                    val isActive = url == selectedKindUrl
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            selectedKindUrl = url
                            isLoading = true
                            errorMessage = null
                            currentPage = 1
                            hasMore = true
                            results = emptyList()
                            coroutineScope.launch {
                                try {
                                    results = SourceFetcher.explore(source, url, 1)
                                    if (results.isEmpty()) {
                                        errorMessage = "该分类暂无内容。"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "加载出错: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        label = { Text(name) }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("该书源未配置发现分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 结果列表
        ResultListView(
            results = results,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            errorMessage = errorMessage,
            listState = listState,
            source = source,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
    }
}

/**
 * 通用结果列表组件 (搜索和发现共用) — 带下载功能
 */
@Composable
fun ResultListView(
    results: List<SearchResult>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    source: LegadoSource,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 下载对话框状态
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadTarget by remember { mutableStateOf<SearchResult?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }
    var downloadCurrent by remember { mutableIntStateOf(0) }
    var downloadTotal by remember { mutableIntStateOf(0) }

    if (isLoading && results.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (errorMessage != null && results.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    } else if (results.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Construction,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("等待操作...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxWidth(), state = listState) {
            items(results) { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            downloadTarget = result
                            showDownloadDialog = true
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = result.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp, 72.dp).background(Color.Gray.copy(alpha = 0.3f)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(result.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (result.author.isNotBlank()) {
                                Text(result.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                            if (result.intro.isNotBlank()) {
                                Text(
                                    result.intro,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            // 加载更多指示器
            if (isLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("加载更多...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    // 下载确认/进度对话框
    if (showDownloadDialog && downloadTarget != null) {
        val target = downloadTarget!!
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) {
                    showDownloadDialog = false
                    downloadTarget = null
                }
            },
            title = {
                Text(
                    if (isDownloading) "正在下载" else "下载确认",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (!isDownloading) {
                        Text("书名：${target.name}")
                        if (target.author.isNotBlank()) Text("作者：${target.author}")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "将下载所有章节并保存为 TXT 文件（含封面）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("《${target.name}》")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (downloadTotal > 0) downloadCurrent.toFloat() / downloadTotal else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            downloadProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    TextButton(onClick = {
                        isDownloading = true
                        downloadProgress = "正在获取目录..."
                        downloadCurrent = 0
                        downloadTotal = 0

                        coroutineScope.launch {
                            try {
                                // 确定下载目录
                                val downloadDir = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "Manhua"
                                )

                                val (txtFile, coverFile) = SourceFetcher.downloadBook(
                                    source = source,
                                    detailUrl = target.detailUrl,
                                    bookName = target.name,
                                    coverUrl = target.coverUrl,
                                    outputDir = downloadDir,
                                    onProgress = { current, total, chapterName ->
                                        downloadCurrent = current
                                        downloadTotal = total
                                        downloadProgress = "第 $current / $total 章: $chapterName"
                                    }
                                )

                                Toast.makeText(
                                    context,
                                    "下载完成！已保存到 ${txtFile.parentFile?.name}/${txtFile.name}",
                                    Toast.LENGTH_LONG
                                ).show()

                                isDownloading = false
                                showDownloadDialog = false
                                downloadTarget = null
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                                downloadProgress = "下载失败: ${e.message}"
                                isDownloading = false
                            }
                        }
                    }) {
                        Text("开始下载")
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = {
                        showDownloadDialog = false
                        downloadTarget = null
                    }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
