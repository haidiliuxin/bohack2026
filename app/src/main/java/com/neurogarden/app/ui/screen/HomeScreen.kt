package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onStartCheck: () -> Unit,
    onMock: () -> Unit,
    onChat: () -> Unit,
    onDebugLog: () -> Unit,
    onHistory: () -> Unit,
    onGuardian: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("NeuroGarden", style = MaterialTheme.typography.displaySmall)
        Text("AI 个体化情绪守护与康养关怀系统", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(18.dp))
        Text("学习你的心率、呼吸、输入节奏、停顿时长和主动反馈，识别相对个人日常节奏的状态偏离。")
        Text("不使用前置摄像头，不上传原始音频或完整隐私文本；习惯数据默认本地保存。")
        Text("本应用仅用于压力感知、情绪支持和康养关怀，不构成医学诊断或治疗建议。")
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStartCheck, modifier = Modifier.fillMaxWidth()) {
            Text("开始检测")
        }
        OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
            Text("AI 关怀对话")
        }
        OutlinedButton(onClick = onMock, modifier = Modifier.fillMaxWidth()) {
            Text("进入模拟体验")
        }
        OutlinedButton(onClick = onGuardian, modifier = Modifier.fillMaxWidth()) {
            Text("守护模式")
        }
        OutlinedButton(onClick = onDebugLog, modifier = Modifier.fillMaxWidth()) {
            Text("采集日志")
        }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
            Text("查看历史记录")
        }
    }
}
