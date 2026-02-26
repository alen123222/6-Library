package com.alendawang.manhua.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alendawang.manhua.model.AudioPlayerConfig
import com.alendawang.manhua.model.FontType
import com.alendawang.manhua.model.ReaderBackgroundColor
import com.alendawang.manhua.model.PageFlipMode
import com.alendawang.manhua.model.ReaderConfig

// --- 密码对话框 ---
@Composable
fun PasswordDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    label = { Text("输入密码") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(password) }) { Text("确定") }
                }
            }
        }
    }
}

// --- 重命名对话框 ---
@Composable
fun RenameDialog(oldName: String, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(oldName) }
    Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("重命名", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onConfirm(name) }) { Text("确定") }
                }
            }
        }
    }
}

// --- 小说阅读器设置对话框 ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelSettingsDialog(
    config: ReaderConfig,
    onDismiss: () -> Unit,
    onConfigChange: (ReaderConfig) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit
) {
    val scrollState = rememberScrollState()
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.8f).coerceAtLeast(240.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读设置") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 字号
                    Text("字号: ${config.fontSize.toInt()}")
                    Slider(
                        value = config.fontSize,
                        onValueChange = { onConfigChange(config.copy(fontSize = it)) },
                        valueRange = 12f..36f,
                        steps = 24
                    )

                    // 行间距
                    Text("行间距: ${String.format("%.1f", config.lineHeightRatio)}")
                    Slider(
                        value = config.lineHeightRatio,
                        onValueChange = { onConfigChange(config.copy(lineHeightRatio = it)) },
                        valueRange = 1.2f..2.5f,
                        steps = 13
                    )

                    // 段落间距
                    Text("段落间距: ${config.paragraphSpacing.toInt()}")
                    Slider(
                        value = config.paragraphSpacing,
                        onValueChange = { onConfigChange(config.copy(paragraphSpacing = it)) },
                        valueRange = 8f..32f,
                        steps = 12
                    )

                    // 左右边距
                    Text("左右边距: ${config.horizontalPadding.toInt()}")
                    Slider(
                        value = config.horizontalPadding,
                        onValueChange = { onConfigChange(config.copy(horizontalPadding = it)) },
                        valueRange = 8f..32f,
                        steps = 12
                    )

                    // 背景颜色
                    Text("背景颜色")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderBackgroundColor.values().forEach { bgColor ->
                            FilterChip(
                                selected = config.backgroundColor == bgColor,
                                onClick = { onConfigChange(config.copy(backgroundColor = bgColor)) },
                                label = { Text(bgColor.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = bgColor.color,
                                    selectedLabelColor = bgColor.textColor
                                )
                            )
                        }
                    }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("自定义背景")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = onPickBackground) { Text("选择图片") }
                        if (config.customBackgroundUriString != null) {
                            OutlinedButton(onClick = onClearBackground) { Text("清除") }
                        }
                    }
                    if (config.customBackgroundUriString != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("遮罩强度: ${(config.customBackgroundOverlayAlpha * 100).toInt()}")
                        Slider(
                            value = config.customBackgroundOverlayAlpha,
                            onValueChange = {
                                onConfigChange(
                                    config.copy(customBackgroundOverlayAlpha = it.coerceIn(0f, 1f))
                                )
                            },
                            valueRange = 0f..1f,
                            steps = 99
                        )
                    }

                    
                    // 字体颜色
                    Text("字体颜色")
                    var showTextColorPicker by remember { mutableStateOf(false) }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 预设颜色
                        val textColors = listOf(
                            "默认" to null,
                            "黑色" to Color.Black.toArgb(),
                            "深灰" to Color.DarkGray.toArgb(),
                            "灰色" to Color.Gray.toArgb(),
                            "白色" to Color.White.toArgb()
                        )
                        
                        textColors.forEach { (label, colorInt) ->
                            FilterChip(
                                selected = config.customTextColor == colorInt,
                                onClick = { onConfigChange(config.copy(customTextColor = colorInt)) },
                                label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = if (colorInt != null) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorInt))
                                                .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        )
                                    }
                                } else null
                            )
                        }
                        
                        // 自定义按钮
                        FilterChip(
                            selected = config.customTextColor != null && textColors.none { it.second == config.customTextColor },
                            onClick = { showTextColorPicker = true },
                            label = { Text("自定义", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(14.dp)) }
                        )
                    }

                    if (showTextColorPicker) {
                        var red by remember { mutableFloatStateOf(config.customTextColor?.let { Color(it).red } ?: 0f) }
                        var green by remember { mutableFloatStateOf(config.customTextColor?.let { Color(it).green } ?: 0f) }
                        var blue by remember { mutableFloatStateOf(config.customTextColor?.let { Color(it).blue } ?: 0f) }
                        
                        AlertDialog(
                            onDismissRequest = { showTextColorPicker = false },
                            title = { Text("自定义字体颜色") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(red, green, blue))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                                    )
                                    Text("红: ${(red * 255).toInt()}")
                                    Slider(value = red, onValueChange = { red = it })
                                    Text("绿: ${(green * 255).toInt()}")
                                    Slider(value = green, onValueChange = { green = it })
                                    Text("蓝: ${(blue * 255).toInt()}")
                                    Slider(value = blue, onValueChange = { blue = it })
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    onConfigChange(config.copy(customTextColor = Color(red, green, blue).toArgb()))
                                    showTextColorPicker = false
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTextColorPicker = false }) { Text("取消") }
                            }
                        )
                    }
                    Text("字体类型")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FontType.values().forEach { font ->
                            FilterChip(
                                selected = config.fontType == font,
                                onClick = { onConfigChange(config.copy(fontType = font)) },
                                label = { Text(font.label, fontSize = 12.sp) }
                            )
                        }
                }

                    // 翻页模式
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("翻页模式")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageFlipMode.values().forEach { mode ->
                            FilterChip(
                                selected = config.pageFlipMode == mode,
                                onClick = { onConfigChange(config.copy(pageFlipMode = mode)) },
                                label = { Text(mode.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

// --- 音频播放器设置对话框 ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioPlayerSettingsDialog(
    config: AudioPlayerConfig,
    hasCustomBackground: Boolean = false,
    onConfigChange: (AudioPlayerConfig) -> Unit,
    onBackgroundChange: (String?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var expandLyricsFontSection by remember { mutableStateOf(false) }
    var expandFloatingLyricsSection by remember { mutableStateOf(false) }
    var expandSpeedSection by remember { mutableStateOf(false) }
    var expandOverlaySection by remember { mutableStateOf(false) }
    
    // 监听悬浮歌词被关闭的广播
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == com.alendawang.manhua.FloatingLyricsService.ACTION_FLOATING_LYRICS_DISABLED) {
                    // 更新配置状态
                    onConfigChange(config.copy(floatingLyricsEnabled = false))
                }
            }
        }
        val filter = android.content.IntentFilter(com.alendawang.manhua.FloatingLyricsService.ACTION_FLOATING_LYRICS_DISABLED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    val backgroundPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { 
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) 
            } catch (_: Exception) {}
            onBackgroundChange(uri.toString())
        }
    }
    
    // 保存悬浮歌词配置到 SharedPreferences (供 Service 读取)
    fun saveFloatingConfig(color: Int, size: Float) {
        context.getSharedPreferences("audio_player_config", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("floating_lyric_color", color)
            .putFloat("floating_lyric_text_size", size)
            .apply()
        com.alendawang.manhua.FloatingLyricsService.updateConfig(context)
    }
    
    // 权限提示对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("需要悬浮窗权限") },
            text = { Text("悬浮歌词需要「显示在其他应用上层」权限。点击「去设置」后，请在设置页面开启此权限。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    com.alendawang.manhua.FloatingLyricsService.openOverlayPermissionSettings(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("取消") }
            }
        )
    }
    
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.7f).coerceAtLeast(400.dp)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放器设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ===== 歌词字体大小 - 第一项 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandLyricsFontSection = !expandLyricsFontSection }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FormatSize, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("歌词字体大小", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${config.lyricsFontSize.toInt()}sp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            if (expandLyricsFontSection) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = expandLyricsFontSection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 字号滑块
                        Text("字体大小", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = config.lyricsFontSize,
                            onValueChange = { onConfigChange(config.copy(lyricsFontSize = it)) },
                            valueRange = 12f..32f,
                            steps = 10
                        )
                        
                        // 颜色选择 - 彩色圆圈
                        Text("字体颜色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            com.alendawang.manhua.model.LyricsColor.values().forEach { colorOption ->
                                val isSelected = config.lyricsColor == colorOption.colorInt
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorOption.colorInt))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onConfigChange(config.copy(lyricsColor = colorOption.colorInt))
                                        },
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            null,
                                            tint = if (colorOption == com.alendawang.manhua.model.LyricsColor.White || colorOption == com.alendawang.manhua.model.LyricsColor.LightYellow) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                HorizontalDivider()
                
                // 悬浮歌词 - 一行：标签 + 开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { 
                                if (config.floatingLyricsEnabled) {
                                    expandFloatingLyricsSection = !expandFloatingLyricsSection 
                                }
                            },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lyrics, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("悬浮歌词", style = MaterialTheme.typography.bodyLarge)
                        if (config.floatingLyricsEnabled) {
                            Icon(
                                if (expandFloatingLyricsSection) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                modifier = Modifier.size(20.dp).padding(start = 4.dp)
                            )
                        }
                    }
                    Switch(
                        checked = config.floatingLyricsEnabled,
                        onCheckedChange = { enabled ->
                            // 同步到 SharedPreferences（供 ReaderScreens 读取）
                            context.getSharedPreferences("audio_player_config", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("floating_lyrics_enabled", enabled)
                                .apply()
                            
                            if (enabled) {
                                if (com.alendawang.manhua.FloatingLyricsService.hasOverlayPermission(context)) {
                                    onConfigChange(config.copy(floatingLyricsEnabled = true))
                                    com.alendawang.manhua.FloatingLyricsService.start(context)
                                    expandFloatingLyricsSection = true
                                } else {
                                    // 没权限时重置设置
                                    context.getSharedPreferences("audio_player_config", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("floating_lyrics_enabled", false)
                                        .apply()
                                    showPermissionDialog = true
                                }
                            } else {
                                onConfigChange(config.copy(floatingLyricsEnabled = false))
                                com.alendawang.manhua.FloatingLyricsService.stop(context)
                                expandFloatingLyricsSection = false
                            }
                        }
                    )
                }
                
                // 悬浮歌词详细设置 - 展开式
                androidx.compose.animation.AnimatedVisibility(visible = expandFloatingLyricsSection && config.floatingLyricsEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 颜色选择 - 5个彩色圆圈
                        Text("字体颜色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            com.alendawang.manhua.model.FloatingLyricColor.values().forEach { colorOption ->
                                val isSelected = config.floatingLyricColor == colorOption.colorInt
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorOption.colorInt))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onConfigChange(config.copy(floatingLyricColor = colorOption.colorInt))
                                            saveFloatingConfig(colorOption.colorInt, config.floatingLyricTextSize)
                                        },
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            null,
                                            tint = if (colorOption == com.alendawang.manhua.model.FloatingLyricColor.Black) Color.White else Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 字号设置
                        Text("字体大小: ${config.floatingLyricTextSize.toInt()}sp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = config.floatingLyricTextSize,
                            onValueChange = { 
                                onConfigChange(config.copy(floatingLyricTextSize = it))
                                saveFloatingConfig(config.floatingLyricColor, it)
                            },
                            valueRange = 12f..28f,
                            steps = 8
                        )
                        
                        // 提示
                        Text(
                            "双击歌词返回播放界面 | 长按显示关闭按钮",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider()
                
                // 背景设置 - 一行：标签 + 图标按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Image, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("背景设置", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row {
                        IconButton(
                            onClick = { backgroundPickerLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(20.dp))
                        }
                        if (hasCustomBackground) {
                            IconButton(
                                onClick = { onBackgroundChange(null) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                
                HorizontalDivider()
                
                // 遮罩透明度 - 可折叠
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandOverlaySection = !expandOverlaySection }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("遮罩透明度", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${(config.overlayAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            if (expandOverlaySection) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = expandOverlaySection) {
                    Slider(
                        value = config.overlayAlpha,
                        onValueChange = { onConfigChange(config.copy(overlayAlpha = it)) },
                        valueRange = 0f..0.9f,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }
                
                HorizontalDivider()
                
                // 播放速度 - 可折叠
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandSpeedSection = !expandSpeedSection }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("播放速度", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${config.playbackSpeed}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            if (expandSpeedSection) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = expandSpeedSection) {
                    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speedOptions.forEach { speed ->
                            val isSelected = kotlin.math.abs(config.playbackSpeed - speed) < 0.01f
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onConfigChange(config.copy(playbackSpeed = speed))
                                    com.alendawang.manhua.AudioPlaybackService.setSpeed(context, speed)
                                },
                                label = { Text("${speed}x", fontSize = 13.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
