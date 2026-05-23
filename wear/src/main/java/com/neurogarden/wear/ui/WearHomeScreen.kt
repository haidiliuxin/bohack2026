package com.neurogarden.wear.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState

@Composable
fun WearHomeScreen(
    state: WatchVitalUiState,
    onRefresh: () -> Unit,
    onSyncPhone: () -> Unit,
    onBreathing: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF061114))
    ) {
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            contentPadding = PaddingValues(top = 34.dp, bottom = 30.dp, start = 26.dp, end = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { WatchHeader(state) }
            item { VitalCircle(state) }
            item { EmotionSnapshot(state) }
            item { MetricStrip(state) }
            item { EvidenceCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactChip(
                        onClick = onRefresh,
                        label = { Text("刷新", fontSize = 12.sp) },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                    CompactChip(
                        onClick = onSyncPhone,
                        label = { Text("同步", fontSize = 12.sp) },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
            item {
                Chip(
                    onClick = onBreathing,
                    label = { Text("呼吸引导", fontSize = 13.sp) },
                    secondaryLabel = { Text("4 秒吸气 / 6 秒呼气", fontSize = 11.sp) },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF143B3A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WatchHeader(state: WatchVitalUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "NeuroGarden",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE9FFFA),
            maxLines = 1
        )
        Text(
            text = if (state.phoneConnected) "手机已同步" else "等待手机同步",
            fontSize = 11.sp,
            color = if (state.phoneConnected) Color(0xFF6EE7D8) else Color(0xFFFFD38A),
            maxLines = 1
        )
    }
}

@Composable
private fun VitalCircle(state: WatchVitalUiState) {
    val color = state.riskState.toColor()
    Box(
        modifier = Modifier
            .size(154.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.32f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.48f,
                center = center
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = (state.heartRate.coerceIn(55, 130) - 55) / 75f * 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.heartRate.toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = " bpm",
                    fontSize = 14.sp,
                    color = Color(0xFFB8C9C6),
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Text(
                text = state.riskState.label,
                fontSize = 14.sp,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmotionSnapshot(state: WatchVitalUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D1A1E))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "状态线索：${state.emotionLabel}",
            fontSize = 13.sp,
            color = state.riskState.toColor(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "置信度 ${"%.0f".format(state.confidence * 100)}% · 数据质量 ${state.dataQuality.label}",
            fontSize = 12.sp,
            color = Color(0xFFC5D8D3),
            maxLines = 1
        )
        Text(
            text = "同步 ${state.lastSyncText()} · 来源 ${state.dataSource.label}",
            fontSize = 12.sp,
            color = Color(0xFF93AAA5),
            maxLines = 1
        )
    }
}

@Composable
private fun MetricStrip(state: WatchVitalUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF102025))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SmallMetric("呼吸", "${state.breathRate}/min")
        SmallMetric("运动", state.motionLabel())
        SmallMetric("模式", state.riskState.label)
    }
}

@Composable
private fun SmallMetric(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = Color(0xFF7F9691), maxLines = 1)
        Text(value, fontSize = 13.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun EvidenceCard(state: WatchVitalUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0C171B))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("判断依据", fontSize = 13.sp, color = Color(0xFFE9FFFA), fontWeight = FontWeight.SemiBold)
        state.observedClues.take(3).ifEmpty { listOf("体征节律平稳") }.forEach {
            WatchLine("证据", it)
        }
        state.counterEvidence.take(2).forEach {
            WatchLine("限制", it)
        }
        WatchLine("不确定", state.uncertainty)
    }
}

@Composable
private fun WatchLine(prefix: String, text: String) {
    Text(
        text = "$prefix：$text",
        fontSize = 12.sp,
        color = Color(0xFFB7C8C4),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun WatchVitalUiState.motionLabel(): String = when {
    motionLevel < 0.25f -> "低"
    motionLevel < 0.60f -> "中"
    else -> "高"
}

private fun WatchVitalUiState.lastSyncText(): String =
    if (lastSyncTime <= 0L) "--:--" else DateFormat.format("HH:mm", lastSyncTime).toString()

private fun WatchRiskState.toColor(): Color = when (this) {
    WatchRiskState.STABLE -> Color(0xFF6EE7D8)
    WatchRiskState.OBSERVE -> Color(0xFFFFD166)
    WatchRiskState.ALERT -> Color(0xFFFF7A8A)
}
