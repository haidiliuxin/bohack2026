package com.neurogarden.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeuroLightColors = lightColorScheme(
    primary = Color(0xFF2F7CF6),
    secondary = Color(0xFF23C9D4),
    tertiary = Color(0xFFFFC45D),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5FA),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    outline = Color(0xFFE7ECF3)
)

@Composable
fun NeuroGardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeuroLightColors, content = content)
}
