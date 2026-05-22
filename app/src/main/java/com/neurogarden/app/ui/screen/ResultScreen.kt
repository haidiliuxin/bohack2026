package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.neurogarden.app.algorithm.StressCalculator
import com.neurogarden.app.viewmodel.TherapyUiState

@Composable
fun ResultScreen(
    state: TherapyUiState,
    onSave: () -> Unit,
    onAgain: () -> Unit,
    onHistory: () -> Unit,
    onHome: () -> Unit
) {
    val before = StressCalculator.calculate(state.startPacket)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("本次关怀引导完成", style = MaterialTheme.typography.headlineMedium)
        Text("引导时长：3 分钟")
        Text("心率变化：${state.startPacket.heartRate} → ${state.currentPacket.heartRate} BPM")
        Text("呼吸节奏：${state.startPacket.breathRate} → ${state.currentPacket.breathRate} 次/分钟")
        Text("压力评分：${"%.2f".format(before.stressScore)} → ${"%.2f".format(state.result.stressScore)}")
        Text("情绪地形：${before.terrainName} → ${state.result.terrainName}")
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !state.saved) {
            Text(if (state.saved) "已保存" else "保存记录")
        }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("再来一次") }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("查看历史") }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("返回首页") }
    }
}
