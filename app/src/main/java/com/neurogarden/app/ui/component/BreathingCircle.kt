package com.neurogarden.app.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun BreathingCircle(
    inhaleSeconds: Int,
    exhaleSeconds: Int,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    var inhaling by remember { mutableStateOf(true) }
    LaunchedEffect(inhaleSeconds, exhaleSeconds, isRunning, inhaling) {
        if (isRunning) {
            delay((if (inhaling) inhaleSeconds else exhaleSeconds) * 1000L)
            inhaling = !inhaling
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (inhaling) 1f else 0.58f,
        animationSpec = tween(
            durationMillis = (if (inhaling) inhaleSeconds else exhaleSeconds) * 1000,
            easing = FastOutSlowInEasing
        ),
        label = "breathing_scale"
    )

    Box(modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((150 * scale).dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xAA6EE7D8), Color(0x33405B8E), Color.Transparent)
                )
            )
        }
        Text(
            text = if (inhaling) "吸气" else "呼气",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
