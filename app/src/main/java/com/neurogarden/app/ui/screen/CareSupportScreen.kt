package com.neurogarden.app.ui.screen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurogarden.app.algorithm.PersonalizedRiskResult
import com.neurogarden.app.ui.component.BreathingCircle
import com.neurogarden.app.viewmodel.SupportMessage

@Composable
fun CareSupportScreen(
    risk: PersonalizedRiskResult,
    supportMessages: List<SupportMessage>,
    guardianSettings: GuardianSettings,
    notificationSent: Boolean,
    onSendMessage: (String) -> Unit,
    onEmotionLabel: (String) -> Unit,
    onSafe: () -> Unit,
    onNeedCompanion: () -> Unit,
    onFalseAlarm: () -> Unit,
    onHelpful: () -> Unit,
    onNotifyGuardian: () -> Unit,
    onStartBreathing: () -> Unit,
    onBack: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("关怀确认", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(risk.careMessage, style = MaterialTheme.typography.titleMedium)
                Text("当前风险：${risk.riskLevel.displayName} · ${"%.2f".format(risk.riskScore)}")
                risk.guardianTriggerReason?.let { Text("原因：$it") }
            }
        }
        BreathingCircle(inhaleSeconds = 4, exhaleSeconds = 6, isRunning = true)
        if (guardianSettings.gentleModeEnabled) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("先慢慢来", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onSafe, modifier = Modifier.fillMaxWidth()) { Text("我现在还好") }
                    OutlinedButton(onClick = onNeedCompanion, modifier = Modifier.fillMaxWidth()) { Text("想有人陪我") }
                    OutlinedButton(onClick = onStartBreathing, modifier = Modifier.fillMaxWidth()) { Text("跟着呼吸") }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("现在更像什么？", style = MaterialTheme.typography.titleMedium)
                    Text("可以只点一下，不需要解释。")
                    EmotionLabelButton("累", onEmotionLabel)
                    EmotionLabelButton("烦", onEmotionLabel)
                    EmotionLabelButton("低落", onEmotionLabel)
                    EmotionLabelButton("紧张", onEmotionLabel)
                    EmotionLabelButton("没事", onEmotionLabel)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("轻柔对话", style = MaterialTheme.typography.titleMedium)
                    supportMessages.forEach { message ->
                        Text(
                            text = if (message.fromUser) "你：${message.text}" else "NeuroGarden：${message.text}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("可以只说一个词，也可以跳过") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSendMessage(draft)
                            draft = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("发送给 AI 守护助手")
                    }
                }
            }
        }
        if (!guardianSettings.gentleModeEnabled) {
            Button(onClick = onSafe, modifier = Modifier.fillMaxWidth()) { Text("我现在安全") }
            OutlinedButton(onClick = onStartBreathing, modifier = Modifier.fillMaxWidth()) { Text("开始呼吸引导") }
            OutlinedButton(onClick = onNeedCompanion, modifier = Modifier.fillMaxWidth()) { Text("我需要陪伴") }
        }
        OutlinedButton(
            onClick = onNotifyGuardian,
            modifier = Modifier.fillMaxWidth(),
            enabled = guardianSettings.enabled && guardianSettings.contact.isNotBlank()
        ) {
            Text(if (notificationSent) "已打开守护人联系方式" else "联系守护人")
        }
        OutlinedButton(onClick = onFalseAlarm, modifier = Modifier.fillMaxWidth()) { Text("误报了") }
        OutlinedButton(onClick = onHelpful, modifier = Modifier.fillMaxWidth()) { Text("这次提醒有帮助") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回实时状态") }
        Text("守护对象：${guardianSettings.name}（${guardianSettings.relation}）。点击联系守护人会打开系统拨号盘，由你确认是否拨出；应用不会后台自动拨号或发送短信。")
    }
}

@Composable
private fun EmotionLabelButton(label: String, onEmotionLabel: (String) -> Unit) {
    OutlinedButton(onClick = { onEmotionLabel(label) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
