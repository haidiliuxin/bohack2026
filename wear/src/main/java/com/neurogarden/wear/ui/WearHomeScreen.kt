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
import androidx.compose.foundation.layout.widthIn
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
            contentPadding = PaddingValues(top = 30.dp, bottom = 34.dp, start = 42.dp, end = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { WatchHeader(state) }
            item { VitalCircle(state) }
            item { MetricStrip(state) }
            item { SourceStrip(state) }
            item { StatusSnapshot(state) }
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
                        label = { Text("同步手机", fontSize = 12.sp) },
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
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .widthIn(max = 260.dp)
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
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE9FFFA),
            maxLines = 1
        )
        Text(
            text = if (state.phoneConnected) "手机已同步" else "等待手机同步",
            fontSize = 10.sp,
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
            .size(132.dp)
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
                    fontSize = 40.sp,
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
                maxLines = 1,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MetricStrip(state: WatchVitalUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 270.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF102025))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SmallMetric("呼吸", "${state.breathRate}/min")
        SmallMetric("运动", state.motionLabel)
        SmallMetric("状态", state.statusLabel)
    }
}

@Composable
private fun SourceStrip(state: WatchVitalUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 270.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0C171B))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SmallMetric("心率", state.heartRateSource.label)
        SmallMetric("呼吸", state.breathRateSource.label)
        SmallMetric("运动", state.motionSource.label)
    }
}

@Composable
private fun SmallMetric(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 10.sp, color = Color(0xFF7F9691), maxLines = 1)
        Text(value, fontSize = 12.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun StatusSnapshot(state: WatchVitalUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 270.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D1A1E))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "状态线索：${state.statusLabel}",
            fontSize = 12.sp,
            color = state.riskState.toColor(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "置信度 ${"%.0f".format(state.confidence * 100)}% · 数据质量 ${state.dataQuality.label}",
            fontSize = 11.sp,
            color = Color(0xFFC5D8D3),
            maxLines = 1
        )
        Text(
            text = "同步 ${state.lastSyncText()} · 心率 ${state.heartRateSource.label}",
            fontSize = 11.sp,
            color = Color(0xFF93AAA5),
            maxLines = 1
        )
        Text(
            text = state.lastCommandText,
            fontSize = 11.sp,
            color = Color(0xFF93AAA5),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EvidenceCard(state: WatchVitalUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 270.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0C171B))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("判断依据", fontSize = 12.sp, color = Color(0xFFE9FFFA), fontWeight = FontWeight.SemiBold)
        state.observedClues.take(2).ifEmpty { listOf("体征节律平稳") }.forEach {
            WatchLine("证据", it)
        }
        state.counterEvidence.take(2).forEach {
            WatchLine("限制", it)
        }
        WatchLine("说明", state.uncertainty)
    }
}

@Composable
private fun WatchLine(prefix: String, text: String) {
    Text(
        text = "$prefix：$text",
        fontSize = 11.sp,
        color = Color(0xFFB7C8C4),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun WatchVitalUiState.lastSyncText(): String =
    if (lastSyncTime <= 0L) "--:--" else DateFormat.format("HH:mm", lastSyncTime).toString()

private fun WatchRiskState.toColor(): Color = when (this) {
    WatchRiskState.STABLE -> Color(0xFF6EE7D8)
    WatchRiskState.OBSERVE -> Color(0xFFFFD166)
    WatchRiskState.ALERT -> Color(0xFFFF7A8A)
}
