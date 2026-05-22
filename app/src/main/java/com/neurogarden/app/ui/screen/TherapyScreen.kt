package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurogarden.app.ui.component.BreathingCircle
import com.neurogarden.app.ui.component.EmotionTerrainView
import com.neurogarden.app.ui.component.HeartRateCard
import com.neurogarden.app.ui.component.TherapyControlPanel
import com.neurogarden.app.viewmodel.TherapyUiState

@Composable
fun TherapyScreen(
    state: TherapyUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("关怀引导 · ${state.plan.mode.displayName}", style = MaterialTheme.typography.headlineSmall)
        EmotionTerrainView(state.result.stressScore, state.result.terrainName, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BreathingCircle(state.plan.inhaleSeconds, state.plan.exhaleSeconds, state.isRunning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeartRateCard("当前心率", "${state.currentPacket.heartRate} BPM", Modifier.fillMaxWidth())
                HeartRateCard("呼吸节奏", "${state.currentPacket.breathRate} 次/分钟", Modifier.fillMaxWidth())
            }
        }
        Text("压力评分：${"%.2f".format(state.result.stressScore)}")
        Text("场景：${state.plan.sceneName} · 音效：${state.plan.soundName}")
        Card(Modifier.fillMaxWidth()) {
            Text(state.plan.aiText, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        }
        TherapyControlPanel(state.isRunning, onStart, onPause, onFinish)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回实时状态")
        }
    }
}
