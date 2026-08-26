package com.sarchiver.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Sage = Color(0xFF2E7D4F)
val SageContainer = Color(0xFFC8E6C9)
val Cream = Color(0xFFF4FBF4)
val Leaf = Color(0xFF1B5E3B)

private val Light = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = Leaf,
    secondary = Color(0xFF4C6354),
    background = Cream,
    surface = Color.White,
    surfaceVariant = Color(0xFFE7F2E8),
    outline = Color(0xFF6F7B72),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD5A6),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF145C34),
    background = Color(0xFF101510),
    surface = Color(0xFF171D18),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun SarchiverTheme(mode: ThemeMode, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val ctx = LocalContext.current
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, content = content)
}
