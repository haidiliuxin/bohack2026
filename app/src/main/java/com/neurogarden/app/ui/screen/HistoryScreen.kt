package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurogarden.app.data.local.TherapySessionEntity
import com.neurogarden.app.viewmodel.RealtimeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    sessions: List<TherapySessionEntity>,
    realtime: RealtimeUiState,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("关怀记录", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回首页") }
        CareMemorySummary(realtime)
        if (sessions.isEmpty()) {
            Text("还没有关怀记录，完成一次引导后会显示在这里。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sessions) { session ->
                    HistoryItem(session)
                }
            }
        }
    }
}

@Composable
private fun CareMemorySummary(realtime: RealtimeUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("当前关怀记忆摘要", style = MaterialTheme.typography.titleMedium)
            Text("系统判断：${realtime.emotionalState.primaryState}")
            realtime.lastUserEmotionLabel?.let { Text("最近用户标注：$it") }
            Text("趋势：${realtime.trend.trendLabel}，持续 ${realtime.trend.sustainedDeviationMinutes} 分钟")
            Text(realtime.feedbackSummaryText)
            Text("解释：${realtime.emotionalState.explanation}")
        }
    }
}

@Composable
private fun HistoryItem(session: TherapySessionEntity) {
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatter.format(Date(session.startTime)), style = MaterialTheme.typography.titleMedium)
            Text("关怀模式：${session.therapyMode}")
            Text("压力评分：${"%.2f".format(session.beforeStressScore)} → ${"%.2f".format(session.afterStressScore)}")
            Text("心率变化：${session.beforeHeartRate} → ${session.afterHeartRate} BPM")
            Text("呼吸变化：${session.beforeBreathRate} → ${session.afterBreathRate} 次/分钟")
            Text("用户反馈：${session.userFeedback ?: "未填写"}")
        }
    }
}
