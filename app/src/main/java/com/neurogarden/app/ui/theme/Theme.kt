package com.neurogarden.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeuroColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF6EE7D8),
    secondary = Color(0xFFB9A7FF),
    tertiary = Color(0xFFFFC48C),
    background = Color(0xFF071316),
    surface = Color(0xFF102023),
    surfaceVariant = Color(0xFF1B3034),
    onPrimary = Color(0xFF05201D),
    onSecondary = Color(0xFF17102E),
    onBackground = Color(0xFFE8F4F1),
    onSurface = Color(0xFFE8F4F1)
)

@Composable
fun NeuroGardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeuroColors, content = content)
}
