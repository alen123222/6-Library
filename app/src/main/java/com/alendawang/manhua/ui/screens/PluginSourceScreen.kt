package com.alendawang.manhua.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import com.alendawang.manhua.engine.PluginSourceManager
import com.alendawang.manhua.engine.SearchResult
import com.alendawang.manhua.engine.SourceFetcher
import com.alendawang.manhua.model.plugin.LegadoSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSourceScreen(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sources by remember { mutableStateOf(PluginSourceManager.getSources(context)) }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf<LegadoSource?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (selectedSource != null) "测试: ${selectedSource?.bookSourceName}" else title,
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
        },
        floatingActionButton = {
            if (selectedSource == null) {
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
                // 书源列表页面
                if (sources.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无导入的书源\n请点击右下角按钮导入 JSON 规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sources, key = { it.bookSourceUrl }) { source ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable { selectedSource = source },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(source.bookSourceName.takeIf { it.isNotBlank() } ?: "未知源", fontWeight = FontWeight.Bold)
                                        Text(source.bookSourceUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        PluginSourceManager.deleteSource(context, source.bookSourceUrl)
                                        sources = PluginSourceManager.getSources(context)
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 书源测试页面
                SourceTestView(source = selectedSource!!)
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
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SourceTestView(source: LegadoSource) {
    val coroutineScope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("斗破") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索关键字测试") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (keyword.isBlank() || isSearching) return@Button
                isSearching = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        searchResults = SourceFetcher.search(source, keyword)
                        if (searchResults.isEmpty()) {
                            errorMessage = "未抓取到任何结果，可能是规则不兼容或被网站拦截。"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "解析出错: ${e.message}"
                    } finally {
                        isSearching = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSearching) "正在抓取解析中..." else "测试当前源")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        } else if (searchResults.isEmpty() && !isSearching) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Construction,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "纯净解析引擎测试",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(searchResults) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(
                                model = result.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp, 80.dp).background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(result.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(result.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = result.intro,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
