package com.alendawang.manhua.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 落樱主题配色
val SakuraColors = lightColorScheme(
    primary = Color(0xFFFF80AB),
    onPrimary = Color.White,
    secondary = Color(0xFFF48FB1),
    tertiary = Color(0xFFFFC1E3),
    background = Color(0xFFFFF0F5), // LavenderBlush
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFFEBF0),
    onSurface = Color(0xFF4A2C36),
    error = Color(0xFFBA1A1A)
)

// 暗黑主题配色
val CyberpunkColors = darkColorScheme(
    primary = Color(0xFF00E5FF), // Cyan Accent
    onPrimary = Color.Black,
    secondary = Color(0xFFFF4081), // Pink Accent
    tertiary = Color(0xFF76FF03), // Lime Accent
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFFF5252)
)

// 水墨主题配色
val InkColors = lightColorScheme(
    primary = Color(0xFF263238),
    onPrimary = Color.White,
    secondary = Color(0xFF546E7A),
    tertiary = Color(0xFF90A4AE),
    background = Color(0xFFECEFF1),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFCFD8DC),
    onSurface = Color(0xFF212121),
    error = Color(0xFFB00020)
)

// 抹茶主题配色
val MatchaColors = lightColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color.White,
    secondary = Color(0xFF9CCC65),
    tertiary = Color(0xFFDCEDC8),
    background = Color(0xFFF1F8E9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8F5E9),
    onSurface = Color(0xFF1B5E20),
    error = Color(0xFFD32F2F)
)
