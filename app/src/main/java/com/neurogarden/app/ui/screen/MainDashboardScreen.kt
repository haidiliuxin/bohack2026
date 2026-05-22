package com.neurogarden.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.neurogarden.app.data.local.TherapySessionEntity
import com.neurogarden.app.viewmodel.RealtimeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class MainTab(val title: String) {
    TODAY("今日"),
    HISTORY("历史"),
    GUARDIAN("守护"),
    SETTINGS("设置")
}

data class RiskEventUi(
    val timeRange: String,
    val riskLevel: String,
    val riskScore: Float,
    val confidence: Float,
    val reasons: List<String>,
    val deviations: Map<String, Float>,
    val notifiedGuardian: Boolean,
    val guardianFeedback: String
)

@Composable
fun MainDashboardScreen(
    realtime: RealtimeUiState,
    sessions: List<TherapySessionEntity>,
    guardianSettings: GuardianSettings,
    onGuardianSettingsChange: (GuardianSettings) -> Unit,
    onStartPassiveGuardian: () -> Unit,
    onStopPassiveGuardian: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnectWear: () -> Unit,
    onContinueMock: () -> Unit,
    onFeedback: (String) -> Unit,
    onClearHabitMemory: () -> Unit,
    onDebugLog: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.TODAY) }
    var selectedEvent by remember(realtime) { mutableStateOf<RiskEventUi?>(null) }
    val event = remember(realtime, guardianSettings) { realtime.toRiskEvent(guardianSettings.enabled) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.values().forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            selectedEvent = null
                            tab = item
                        },
                        label = { Text(item.title) },
                        icon = { Text(item.title.take(1)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            if (selectedEvent != null) {
                EventDetailScreen(
                    event = selectedEvent!!,
                    onBack = { selectedEvent = null },
                    onFeedback = onFeedback
                )
            } else {
                when (tab) {
                    MainTab.TODAY -> TodayMonitorScreen(
                        realtime = realtime,
                        event = event,
                        onOpenEvent = { selectedEvent = event },
                        onNextScenario = onContinueMock
                    )
                    MainTab.HISTORY -> HistoryDashboardScreen(realtime, sessions)
                    MainTab.GUARDIAN -> GuardianDashboardScreen(
                        settings = guardianSettings,
                        onSettingsChange = onGuardianSettingsChange,
                        onStartPassiveGuardian = onStartPassiveGuardian,
                        onStopPassiveGuardian = onStopPassiveGuardian,
                        onFeedback = onFeedback
                    )
                    MainTab.SETTINGS -> SettingsDashboardScreen(
                        settings = guardianSettings,
                        onSettingsChange = onGuardianSettingsChange,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onConnectWear = onConnectWear,
                        onClearHabitMemory = onClearHabitMemory,
                        onDebugLog = onDebugLog
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayMonitorScreen(
    realtime: RealtimeUiState,
    event: RiskEventUi,
    onOpenEvent: () -> Unit,
    onNextScenario: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("今日监测", style = MaterialTheme.typography.headlineMedium)
        ScoreCard("当前风险评分", "%.2f".format(event.riskScore), event.riskLevel)
        ChartCard("今日风险评分曲线", listOf(0.18f, 0.22f, 0.31f, event.riskScore))
        ChartCard("心率曲线", listOf(72f, 84f, 96f, realtime.packet.heartRate.toFloat()), maxValue = 130f)
        ChartCard("呼吸频率曲线", listOf(12f, 15f, 18f, realtime.packet.breathRate.toFloat()), maxValue = 32f)
        MetricGrid(realtime)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("天气因素", style = MaterialTheme.typography.titleMedium)
                Text("当前版本预留天气接口：暂按普通天气处理。后续 Agent 将接收天气、温度、气压、降雨等结构化字段。")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("异常事件列表", style = MaterialTheme.typography.titleMedium)
                Text("${event.timeRange} · ${event.riskLevel} · 评分 ${"%.2f".format(event.riskScore)}")
                Text(event.reasons.joinToString("；"))
                Button(onClick = onOpenEvent, modifier = Modifier.fillMaxWidth()) {
                    Text("查看异常详情")
                }
            }
        }
        OutlinedButton(onClick = onNextScenario, modifier = Modifier.fillMaxWidth()) {
            Text("切换模拟监测状态")
        }
    }
}

@Composable
private fun MetricGrid(realtime: RealtimeUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("实时指标", style = MaterialTheme.typography.titleMedium)
            Text("运动状态：${realtime.bodyState} / motionLevel ${"%.2f".format(realtime.packet.motionLevel)}")
            Text("打字速度：模拟 ${typingFor(realtime)} 字/分钟")
            Text("删除频率：模拟 ${"%.2f".format(deleteFor(realtime))}")
            Text("停顿时长：模拟 ${"%.1f".format(pauseFor(realtime))} 秒")
            Text("个人基线：${realtime.habitLearningStatus}")
        }
    }
}

@Composable
private fun EventDetailScreen(
    event: RiskEventUi,
    onBack: () -> Unit,
    onFeedback: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("异常事件详情", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("异常时间段：${event.timeRange}")
                Text("风险等级：${event.riskLevel}")
                Text("风险评分：${"%.2f".format(event.riskScore)}")
                Text("置信度：${"%.0f".format(event.confidence * 100)}%")
                Text("是否通知监护人：${if (event.notifiedGuardian) "建议通知" else "暂不通知"}")
                Text("监护人反馈结果：${event.guardianFeedback}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agent 分析原因", style = MaterialTheme.typography.titleMedium)
                event.reasons.forEach { Text("· $it") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("相对个人基线偏离", style = MaterialTheme.typography.titleMedium)
                event.deviations.forEach { (name, value) ->
                    Text("$name：${if (value >= 0f) "+" else ""}${"%.0f".format(value)}%")
                }
            }
        }
        GuardianFeedbackButtons(onFeedback)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回今日")
        }
    }
}

@Composable
private fun GuardianFeedbackButtons(onFeedback: (String) -> Unit) {
    val actions = listOf(
        "确认异常",
        "标记误报",
        "已联系本人",
        "继续观察",
        "提高该类提醒优先级",
        "降低该类提醒优先级"
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("监护人反馈", style = MaterialTheme.typography.titleMedium)
            actions.forEach { action ->
                OutlinedButton(onClick = { onFeedback(action) }, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun HistoryDashboardScreen(realtime: RealtimeUiState, sessions: List<TherapySessionEntity>) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("历史趋势", style = MaterialTheme.typography.headlineMedium)
        ChartCard("近段风险趋势", listOf(0.22f, 0.28f, 0.35f, realtime.personalizedRisk.riskScore))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("趋势摘要", style = MaterialTheme.typography.titleMedium)
                Text(realtime.trend.trendLabel)
                Text(realtime.trend.explanation)
                Text(realtime.feedbackSummaryText)
            }
        }
        sessions.take(8).forEach {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.startTime)))
                    Text("评分：${"%.2f".format(it.beforeStressScore)} -> ${"%.2f".format(it.afterStressScore)}")
                    Text("心率：${it.beforeHeartRate} -> ${it.afterHeartRate} BPM")
                }
            }
        }
    }
}

@Composable
private fun GuardianDashboardScreen(
    settings: GuardianSettings,
    onSettingsChange: (GuardianSettings) -> Unit,
    onStartPassiveGuardian: () -> Unit,
    onStopPassiveGuardian: () -> Unit,
    onFeedback: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("守护", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("监护人提醒", style = MaterialTheme.typography.titleMedium)
                Text("当前联系人：${settings.name} / ${settings.relation}")
                Text("提醒阈值：${"%.2f".format(settings.notifyThreshold)}")
                Slider(
                    value = settings.notifyThreshold,
                    onValueChange = { onSettingsChange(settings.copy(notifyThreshold = it)) },
                    valueRange = 0.55f..0.95f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.enabled,
                        onClick = { onSettingsChange(settings.copy(enabled = !settings.enabled)) },
                        label = { Text("授权提醒") }
                    )
                    FilterChip(
                        selected = settings.passiveGuardianRunning,
                        onClick = {
                            if (settings.passiveGuardianRunning) onStopPassiveGuardian() else onStartPassiveGuardian()
                        },
                        label = { Text(if (settings.passiveGuardianRunning) "守护运行中" else "启动守护") }
                    )
                }
            }
        }
        GuardianFeedbackButtons(onFeedback)
    }
}

@Composable
private fun SettingsDashboardScreen(
    settings: GuardianSettings,
    onSettingsChange: (GuardianSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnectWear: () -> Unit,
    onClearHabitMemory: () -> Unit,
    onDebugLog: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("隐私与权限", style = MaterialTheme.typography.titleMedium)
                Text("不保存用户输入的完整文字，只保存打字速度、删除频率、停顿时长等统计特征。")
                Text("Agent 只接收结构化特征，不接收原始文本。健康数据默认本地保存。")
                Text("监护人提醒必须用户授权。本系统不是医疗诊断工具。")
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开无障碍输入节奏权限")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设备连接", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onConnectWear, modifier = Modifier.fillMaxWidth()) { Text("连接 Wear OS 手表") }
                OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) { Text("打开蓝牙设置") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agent API", style = MaterialTheme.typography.titleMedium)
                Text("Agent 职责：分析结构化特征，输出风险评分、风险等级、置信度、主因和建议动作；不进行心理陪聊或医疗诊断。")
                FilterChip(
                    selected = settings.agentCareEnabled,
                    onClick = { onSettingsChange(settings.copy(agentCareEnabled = !settings.agentCareEnabled)) },
                    label = { Text(if (settings.agentCareEnabled) "Agent 分析已启用" else "仅本地规则") }
                )
            }
        }
        OutlinedButton(onClick = onDebugLog, modifier = Modifier.fillMaxWidth()) { Text("查看采集 Debug") }
        OutlinedButton(onClick = onClearHabitMemory, modifier = Modifier.fillMaxWidth()) { Text("清除本地习惯记忆") }
    }
}

@Composable
private fun ScoreCard(title: String, score: String, level: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(score, style = MaterialTheme.typography.displaySmall)
            Text(level)
        }
    }
}

@Composable
private fun ChartCard(title: String, points: List<Float>, maxValue: Float = 1f) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val path = Path()
                points.forEachIndexed { index, raw ->
                    val x = size.width * index / max(1, points.lastIndex)
                    val normalized = (raw / maxValue).coerceIn(0f, 1f)
                    val y = size.height - normalized * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(Color(0xFF6EE7D8), 5f, Offset(x, y))
                }
                drawPath(path, Color(0xFF6EE7D8), style = Stroke(width = 4f))
            }
        }
    }
}

private fun RealtimeUiState.toRiskEvent(notifyEnabled: Boolean): RiskEventUi {
    val heartDeviation = ((packet.heartRate - 72f) / 72f) * 100f
    val breathDeviation = ((packet.breathRate - 12f) / 12f) * 100f
    val typingDeviation = ((typingFor(this) - 100f) / 100f) * 100f
    val reasons = buildList {
        if (heartDeviation > 15f) add("心率高于个人基线")
        if (breathDeviation > 25f) add("呼吸频率高于个人基线")
        if (typingFor(this@toRiskEvent) < 80f) add("输入节奏低于平时水平")
        if (deleteFor(this@toRiskEvent) > 0.12f) add("删除频率升高")
        if (packet.motionLevel < 0.6f) add("运动干扰较低，生理信号可信度较高")
    }.ifEmpty { listOf("当前未发现明显异常，仅保持观察") }
    return RiskEventUi(
        timeRange = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
        riskLevel = personalizedRisk.riskLevel.displayName,
        riskScore = personalizedRisk.riskScore,
        confidence = personalizedRisk.confidence,
        reasons = reasons,
        deviations = mapOf(
            "心率" to heartDeviation,
            "呼吸" to breathDeviation,
            "打字速度" to typingDeviation,
            "删除频率" to (deleteFor(this) * 100f),
            "停顿时长" to ((pauseFor(this) - 1.5f) / 1.5f * 100f)
        ),
        notifiedGuardian = notifyEnabled && personalizedRisk.guardianTriggerReason != null,
        guardianFeedback = lastUserEmotionLabel ?: "暂无反馈"
    )
}

private fun typingFor(state: RealtimeUiState): Float = when (state.scenario.name) {
    "CALM" -> 118f
    "TENSE" -> 92f
    "ANXIOUS" -> 68f
    else -> 84f
}

private fun deleteFor(state: RealtimeUiState): Float = when (state.scenario.name) {
    "CALM" -> 0.03f
    "TENSE" -> 0.09f
    "ANXIOUS" -> 0.19f
    else -> 0.12f
}

private fun pauseFor(state: RealtimeUiState): Float = when (state.scenario.name) {
    "CALM" -> 1.2f
    "TENSE" -> 2.1f
    "ANXIOUS" -> 3.8f
    else -> 2.6f
}
