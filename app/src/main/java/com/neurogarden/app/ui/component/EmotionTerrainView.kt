package com.neurogarden.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun EmotionTerrainView(
    stressScore: Float,
    terrainName: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "terrain")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "terrain_time"
    )
    val amplitude = 12f + stressScore * 90f
    val frequency = 1.1f + stressScore * 4.2f

    Box(
        modifier
            .height(220.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF14292E), Color(0xFF071316))),
                RoundedCornerShape(24.dp)
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path()
            val baseY = size.height * (0.70f - stressScore * 0.18f)
            path.moveTo(0f, size.height)
            path.lineTo(0f, baseY)
            val steps = 90
            for (i in 0..steps) {
                val x = size.width * i / steps
                val ratio = i / steps.toFloat()
                val ridge = sin(ratio * frequency * 6.28f + time) * amplitude
                val sharp = sin(ratio * frequency * 18f - time * 0.7f) * amplitude * stressScore * 0.35f
                path.lineTo(x, baseY - ridge - sharp)
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(Color(0xFF79E6D0), Color(0xFF405B8E), Color(0xFF1D263A))
                )
            )
            drawLine(
                color = Color(0x88B9A7FF),
                start = Offset(0f, baseY),
                end = Offset(size.width, baseY),
                strokeWidth = 2f
            )
            drawPath(path, Color(0x5579E6D0), style = Stroke(width = 2f))
        }
        Text(
            text = terrainName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.TopStart).padding(18.dp)
        )
    }
}
