package com.neurogarden.wear.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import kotlinx.coroutines.delay

@Composable
fun WearBreathingScreen(
    inhaleSeconds: Int = 4,
    exhaleSeconds: Int = 6,
    onBack: () -> Unit
) {
    var phase by remember { mutableStateOf("吸气") }
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween((inhaleSeconds + exhaleSeconds) * 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    LaunchedEffect(inhaleSeconds, exhaleSeconds) {
        while (true) {
            phase = "吸气"
            delay(inhaleSeconds * 1000L)
            phase = "呼气"
            delay(exhaleSeconds * 1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF061114))
    ) {
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            contentPadding = PaddingValues(top = 36.dp, bottom = 28.dp, start = 28.dp, end = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "呼吸引导",
                    style = MaterialTheme.typography.title3,
                    color = Color(0xFFE9FFFA)
                )
            }
            item {
                BreathCircle(phase = phase, scale = scale)
            }
            item {
                Text(
                    text = "$inhaleSeconds 秒吸气 / $exhaleSeconds 秒呼气",
                    fontSize = 12.sp,
                    color = Color(0xFFB7C8C4)
                )
            }
            item {
                Text(
                    text = "手表轻震同步节奏",
                    fontSize = 12.sp,
                    color = Color(0xFF93AAA5)
                )
            }
            item {
                Chip(
                    onClick = onBack,
                    label = { Text("返回体征", fontSize = 13.sp) },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Composable
private fun BreathCircle(phase: String, scale: Float) {
    Box(
        modifier = Modifier.size(156.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.34f * scale
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF6EE7D8).copy(alpha = 0.42f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.50f
                ),
                radius = size.minDimension * 0.48f,
                center = center
            )
            drawCircle(
                color = Color(0xFF6EE7D8),
                radius = radius,
                center = center,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(
                color = Color(0xFF9FF8EC).copy(alpha = 0.18f),
                radius = radius * 0.72f,
                center = center
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phase,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (phase == "吸气") "慢慢展开" else "慢慢放下",
                fontSize = 12.sp,
                color = Color(0xFFB7C8C4)
            )
        }
    }
}
