package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurogarden.app.ui.component.EmotionTerrainView
import com.neurogarden.app.ui.component.HeartRateCard
import com.neurogarden.app.viewmodel.RealtimeUiState

@Composable
fun RealtimeScreen(
    state: RealtimeUiState,
    onStartTherapy: () -> Unit,
    onNextScenario: () -> Unit,
    onOpenCare: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("实时状态", style = MaterialTheme.typography.headlineMedium)
        EmotionTerrainView(
            stressScore = state.result.stressScore,
            terrainName = state.result.terrainName,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HeartRateCard("当前心率", "${state.packet.heartRate} BPM", Modifier.weight(1f))
            HeartRateCard("呼吸节奏", "${state.packet.breathRate} 次/分钟", Modifier.weight(1f))
        }
        Text("身体状态：${state.bodyState}")
        Text("压力评分：${"%.2f".format(state.result.stressScore)}")
        Text("情绪状态：${state.result.state.displayName}")
        Text("情绪地形：${state.result.terrainName}")
        Text(state.habitLearningStatus)
        Text(state.thresholdStatus)
        Text("可能状态：${state.emotionalState.primaryState} · 置信度 ${"%.0f".format(state.emotionalState.confidence * 100)}%")
        Text(state.emotionalState.explanation)
        state.emotionalState.interferenceReason?.let {
            Text(it, color = MaterialTheme.colorScheme.tertiary)
        }
        Text("趋势判断：${state.trend.trendLabel} · 持续 ${state.trend.sustainedDeviationMinutes} 分钟")
        Text(state.trend.explanation)
        Text(state.feedbackSummaryText)
        Text("个体化风险：${state.personalizedRisk.riskLevel.displayName} · ${"%.2f".format(state.personalizedRisk.riskScore)}")
        Text("AI 建议：${state.personalizedRisk.suggestedAction}")
        Text(state.personalizedRisk.careMessage)
        state.personalizedRisk.guardianTriggerReason?.let {
            Text("守护提醒原因：$it", color = MaterialTheme.colorScheme.tertiary)
        }
        state.result.warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        Button(onClick = onStartTherapy, modifier = Modifier.fillMaxWidth()) { Text("开始关怀引导") }
        OutlinedButton(onClick = onOpenCare, modifier = Modifier.fillMaxWidth()) {
            Text("进入守护关怀")
        }
        OutlinedButton(onClick = onNextScenario, modifier = Modifier.fillMaxWidth()) {
            Text("切换模拟状态：当前 ${state.scenario.title}")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回首页")
        }
    }
}
