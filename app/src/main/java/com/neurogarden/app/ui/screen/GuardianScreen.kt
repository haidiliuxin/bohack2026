package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

data class GuardianSettings(
    val name: String = "妈妈",
    val relation: String = "家人",
    val contact: String = "",
    val enabled: Boolean = false,
    val notifyThreshold: Float = 0.78f,
    val agentCareEnabled: Boolean = true,
    val gentleModeEnabled: Boolean = false,
    val passiveGuardianRunning: Boolean = false,
    val watchSimulationEnabled: Boolean = false,
    val simulatedHeartRate: Int = 96,
    val simulatedBreathRate: Int = 20,
    val simulatedMotionLevel: Float = 0.10f
)

@Composable
fun GuardianScreen(
    settings: GuardianSettings,
    onSettingsChange: (GuardianSettings) -> Unit,
    onStartPassiveGuardian: () -> Unit,
    onStopPassiveGuardian: () -> Unit,
    onCallGuardian: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onClearHabitMemory: () -> Unit,
    clearMemoryMessage: String?,
    onPrivacyGuide: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("守护模式", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("守护联系人", style = MaterialTheme.typography.titleMedium)
                Text("这里保存你希望在需要陪伴时联系的人。应用不会后台自动拨号，点击联系时会打开系统拨号盘，由你确认是否拨出。")
                OutlinedTextField(
                    value = settings.name,
                    onValueChange = { onSettingsChange(settings.copy(name = it)) },
                    label = { Text("联系人姓名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = settings.relation,
                    onValueChange = { onSettingsChange(settings.copy(relation = it)) },
                    label = { Text("联系人关系") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = settings.contact,
                    onValueChange = { onSettingsChange(settings.copy(contact = it)) },
                    label = { Text("电话号码") },
                    placeholder = { Text("例如 13800000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = onCallGuardian,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.contact.isNotBlank()
                ) {
                    Text("测试拨号 / 联系守护人")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("开关说明", style = MaterialTheme.typography.titleMedium)
                ToggleRow(
                    title = "允许守护提醒",
                    description = "开启后，当检测到持续偏离且你主动确认需要陪伴时，才会显示联系守护人的入口。",
                    checked = settings.enabled
                ) {
                    onSettingsChange(settings.copy(enabled = it))
                }
                ToggleRow(
                    title = "Agent 个性化关怀",
                    description = "开启后，AI 会结合近期反馈生成更贴合你的提示语；关闭后只使用本地固定提示。",
                    checked = settings.agentCareEnabled
                ) {
                    onSettingsChange(settings.copy(agentCareEnabled = it))
                }
                ToggleRow(
                    title = "简化关怀模式",
                    description = "开启后减少文字和选项，适合疲惫、老人或不想被复杂信息打扰的时候。",
                    checked = settings.gentleModeEnabled
                ) {
                    onSettingsChange(settings.copy(gentleModeEnabled = it))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("提醒敏感度", style = MaterialTheme.typography.titleMedium)
                Text("当前通知阈值：${"%.2f".format(settings.notifyThreshold)}")
                Text(thresholdDescription(settings.notifyThreshold))
                Slider(
                    value = settings.notifyThreshold,
                    onValueChange = { onSettingsChange(settings.copy(notifyThreshold = it)) },
                    valueRange = 0.55f..0.95f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("数值越低越敏感，越容易提醒；数值越高越保守，能减少误报。建议日常使用 0.75 到 0.85。")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("手表数据模拟", style = MaterialTheme.typography.titleMedium)
                Text("没有真实手表时可以开启这里。被动守护会用这些后台设定的心率/呼吸数据，和手机端真实打字节奏一起综合判断。")
                ToggleRow(
                    title = "启用模拟手表数据",
                    description = "开启后，后台提醒必须同时满足：输入节奏异常 + 模拟心率/呼吸异常。",
                    checked = settings.watchSimulationEnabled
                ) {
                    onSettingsChange(settings.copy(watchSimulationEnabled = it))
                }
                Text("模拟心率：${settings.simulatedHeartRate} BPM")
                Slider(
                    value = settings.simulatedHeartRate.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(simulatedHeartRate = it.toInt())) },
                    valueRange = 60f..130f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("模拟呼吸：${settings.simulatedBreathRate} 次/分钟")
                Slider(
                    value = settings.simulatedBreathRate.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(simulatedBreathRate = it.toInt())) },
                    valueRange = 8f..32f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("运动干扰：${"%.2f".format(settings.simulatedMotionLevel)}")
                Slider(
                    value = settings.simulatedMotionLevel,
                    onValueChange = { onSettingsChange(settings.copy(simulatedMotionLevel = it)) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("测试建议：心率设为 96 以上、呼吸设为 20 以上、运动干扰低于 0.60，然后启动被动守护并去微信/备忘录快速打字和删除。")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("被动守护", style = MaterialTheme.typography.titleMedium)
                Text("被动守护是在后台记录输入节奏特征，用于判断是否持续偏离。它不会读取你输入的具体文字。")
                Text("当前状态：${if (settings.passiveGuardianRunning) "运行中" else "未运行"}")
                Button(
                    onClick = onStartPassiveGuardian,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !settings.passiveGuardianRunning
                ) {
                    Text("启动被动守护")
                }
                OutlinedButton(
                    onClick = onStopPassiveGuardian,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.passiveGuardianRunning
                ) {
                    Text("停止被动守护")
                }
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开无障碍设置")
                }
            }
        }

        OutlinedButton(onClick = onPrivacyGuide, modifier = Modifier.fillMaxWidth()) {
            Text("查看隐私与权限说明")
        }
        OutlinedButton(onClick = onClearHabitMemory, modifier = Modifier.fillMaxWidth()) {
            Text("清除本地习惯记忆")
        }
        clearMemoryMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("保存并返回")
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun thresholdDescription(value: Float): String = when {
    value < 0.68f -> "偏敏感：更容易提醒，适合你希望尽早被提醒的时候。"
    value < 0.86f -> "平衡：兼顾及时提醒和减少误报，推荐日常使用。"
    else -> "偏保守：只有较明显、持续的状态偏离才提醒。"
}
