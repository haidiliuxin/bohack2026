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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neurogarden.app.algorithm.CareMode
import com.neurogarden.app.algorithm.CareModePolicy
import com.neurogarden.app.algorithm.DailyMonitoringSummary
import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.data.local.TherapySessionEntity
import com.neurogarden.app.passive.AccessibilitySignalStore
import com.neurogarden.app.viewmodel.RealtimeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.flow.Flow

enum class MainTab(val title: String) {
    TODAY("今日"),
    HISTORY("历史"),
    GUARDIAN("守护"),
    SETTINGS("设置")
}

@Composable
fun MainDashboardScreen(
    realtime: RealtimeUiState,
    sessions: List<TherapySessionEntity>,
    todayRiskEvents: List<RiskEventEntity>,
    recentRiskEvents: List<RiskEventEntity>,
    todaySummary: DailyMonitoringSummary,
    sevenDaySummaries: List<DailyMonitoringSummary>,
    careMode: CareMode,
    careModePolicy: CareModePolicy,
    guardianSettings: GuardianSettings,
    onGuardianSettingsChange: (GuardianSettings) -> Unit,
    onStartPassiveGuardian: () -> Unit,
    onStopPassiveGuardian: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnectWear: () -> Unit,
    onContinueMock: () -> Unit,
    onFeedback: (String) -> Unit,
    onEventFeedback: (Long, String) -> Unit,
    observeRiskEventById: (Long) -> Flow<RiskEventEntity?>,
    onClearHabitMemory: () -> Unit,
    onSeedDemoMode: (String) -> Unit,
    onCareModeChange: (CareMode) -> Unit,
    onDebugLog: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.TODAY) }
    var selectedEventId by remember { mutableStateOf<Long?>(null) }
    val selectedEventState = selectedEventId?.let { id ->
        observeRiskEventById(id).collectAsState(initial = null)
    }
    val selectedEvent = selectedEventState?.value
    val latestEvent = todayRiskEvents.firstOrNull() ?: recentRiskEvents.firstOrNull()

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            selectedEventId = null
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
                    event = selectedEvent,
                    feedbackTuningMessage = realtime.guardianFeedbackTuningMessage,
                    onBack = { selectedEventId = null },
                    onFeedback = { feedback -> onEventFeedback(selectedEvent.id, feedback) }
                )
            } else {
                when (tab) {
                    MainTab.TODAY -> TodayMonitorScreen(
                        realtime = realtime,
                        summary = todaySummary,
                        careMode = careMode,
                        events = todayRiskEvents,
                        onOpenEvent = { selectedEventId = it.id },
                        onNextScenario = onContinueMock
                    )

                    MainTab.HISTORY -> HistoryDashboardScreen(
                        realtime = realtime,
                        sessions = sessions,
                        riskEvents = recentRiskEvents,
                        summaries = sevenDaySummaries
                    )

                    MainTab.GUARDIAN -> GuardianDashboardScreen(
                        settings = guardianSettings,
                        careMode = careMode,
                        policy = careModePolicy,
                        latestEvent = latestEvent,
                        onSettingsChange = onGuardianSettingsChange,
                        onStartPassiveGuardian = onStartPassiveGuardian,
                        onStopPassiveGuardian = onStopPassiveGuardian,
                        onFeedback = { feedback ->
                            latestEvent?.let { onEventFeedback(it.id, feedback) } ?: onFeedback(feedback)
                        }
                    )

                    MainTab.SETTINGS -> SettingsDashboardScreen(
                        settings = guardianSettings,
                        careMode = careMode,
                        careModePolicy = careModePolicy,
                        onCareModeChange = onCareModeChange,
                        onSettingsChange = onGuardianSettingsChange,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onConnectWear = onConnectWear,
                        onClearHabitMemory = onClearHabitMemory,
                        onSeedDemoMode = onSeedDemoMode,
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
    summary: DailyMonitoringSummary,
    careMode: CareMode,
    events: List<RiskEventEntity>,
    onOpenEvent: (RiskEventEntity) -> Unit,
    onNextScenario: () -> Unit
) {
    val latestScore = events.firstOrNull()?.riskScore ?: realtime.personalizedRisk.riskScore
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("今日监测", style = MaterialTheme.typography.headlineMedium)
        Text("当前模式：${careMode.toModeLabel()}", style = MaterialTheme.typography.titleMedium)
        DailySummaryCard(summary)
        WeatherContextCard(realtime.weather.displayText())
        ScoreCard(
            title = "当前状态评分",
            score = "%.2f".format(latestScore),
            level = events.firstOrNull()?.riskLevel?.toRiskLabel(careMode) ?: realtime.personalizedRisk.riskLevel.displayName
        )
        ChartCard("今日状态评分曲线", events.toScorePoints(latestScore))
        ChartCard("心率曲线", listOf(72f, 84f, 96f, realtime.packet.heartRate.toFloat()), maxValue = 130f)
        ChartCard("呼吸频率曲线", listOf(12f, 15f, 18f, realtime.packet.breathRate.toFloat()), maxValue = 32f)
        MetricGrid(realtime)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("异常事件列表", style = MaterialTheme.typography.titleMedium)
                if (events.isEmpty()) {
                    Text("今天还没有生成风险事件。系统会在状态评分达到观察阈值后记录事件。")
                } else {
                    events.forEach { event ->
                        OutlinedButton(
                            onClick = { onOpenEvent(event) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${event.timeRangeText()} / ${event.riskLevel.toRiskLabel(careMode)} / ${"%.2f".format(event.riskScore)}")
                        }
                        Text(event.reasonList().joinToString("；"))
                    }
                }
            }
        }
        OutlinedButton(onClick = onNextScenario, modifier = Modifier.fillMaxWidth()) {
            Text("切换模拟监测状态")
        }
    }
}

@Composable
private fun DailySummaryCard(summary: DailyMonitoringSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("今日摘要", style = MaterialTheme.typography.titleMedium)
            Text(summary.summaryText)
            Text("最高风险时段：${summary.highestRiskTimeSegment.toSegmentLabel()}")
            Text("异常事件数量：${summary.riskEventCount}")
            Text("数据可信度：${summary.dataQualityLevel.toQualityLabel()}")
            Text("天气因素：${summary.weatherContext}")
            Text("主要异常指标：${summary.topContributingMetrics.ifEmpty { listOf("暂无") }.joinToString("、")}")
            Text("反馈统计：${summary.guardianFeedbackCount} 次，确认 ${summary.confirmedAbnormalCount} 次，误报 ${summary.falseAlarmCount} 次")
        }
    }
}

@Composable
private fun MetricGrid(realtime: RealtimeUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("实时指标", style = MaterialTheme.typography.titleMedium)
            Text("最近心率来源：${heartRateSource(realtime)}")
            Text("运动状态：${realtime.bodyState} / motionLevel ${"%.2f".format(realtime.packet.motionLevel)}")
            Text("打字速度：模拟 ${typingFor(realtime)} 字/分钟")
            Text("删除频率：模拟 ${"%.2f".format(deleteFor(realtime))}")
            Text("停顿时长：模拟 ${"%.1f".format(pauseFor(realtime))} 秒")
            Text("个人基线：${realtime.habitLearningStatus}")
        }
    }
}

@Composable
private fun WeatherContextCard(weatherText: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("环境因素", style = MaterialTheme.typography.titleMedium)
            Text(weatherText)
            Text("天气会作为状态解释的辅助因素；网络不可用时自动使用 Mock 天气。")
        }
    }
}

@Composable
private fun EventDetailScreen(
    event: RiskEventEntity,
    feedbackTuningMessage: String?,
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
                Text("异常时间段：${event.timeRangeText()}")
                Text("风险评分：${"%.2f".format(event.riskScore)}")
                Text("置信度：${"%.0f".format(event.confidence * 100)}%")
                Text("是否建议提醒：${if (event.guardianNotified) "是" else "否"}")
                Text("反馈结果：${event.guardianFeedback ?: "暂无反馈"}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("结构化分析原因", style = MaterialTheme.typography.titleMedium)
                event.reasonList().forEach { Text("· $it") }
                Text("分析摘要：${event.agentAnalysis}")
                Text("建议动作：${event.suggestedAction}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("相对个人基线偏离", style = MaterialTheme.typography.titleMedium)
                Text("心率：${event.heartRateDeviationPercent.signedPercent()}")
                Text("呼吸：${event.breathRateDeviationPercent.signedPercent()}")
                Text("打字速度：${event.typingSpeedDeviationPercent.signedPercent()}")
                Text("删除频率：${event.deleteRateDeviationPercent.signedPercent()}")
                Text("停顿时长：${event.pauseDurationDeviationPercent.signedPercent()}")
                Text("运动干扰：${"%.2f".format(event.motionLevel)}")
                Text("天气：${event.weather} / 时间段：${event.timeSegment}")
            }
        }
        GuardianFeedbackButtons(onFeedback)
        feedbackTuningMessage?.let { message ->
            Card(Modifier.fillMaxWidth()) {
                Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回今日")
        }
    }
}

@Composable
private fun GuardianFeedbackButtons(onFeedback: (String) -> Unit) {
    val actions = listOf("确认异常", "标记误报", "已联系本人", "继续观察", "提高该类提醒优先级", "降低该类提醒优先级")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("反馈", style = MaterialTheme.typography.titleMedium)
            actions.forEach { action ->
                OutlinedButton(onClick = { onFeedback(action) }, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun HistoryDashboardScreen(
    realtime: RealtimeUiState,
    sessions: List<TherapySessionEntity>,
    riskEvents: List<RiskEventEntity>,
    summaries: List<DailyMonitoringSummary>
) {
    val averageRisk = riskEvents.map { it.riskScore }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("历史趋势", style = MaterialTheme.typography.headlineMedium)
        ChartCard("最近 7 天状态趋势", riskEvents.toScorePoints(realtime.personalizedRisk.riskScore))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("7 天事件统计", style = MaterialTheme.typography.titleMedium)
                Text("风险事件：${riskEvents.size} 条")
                Text("平均评分：${"%.2f".format(averageRisk)}")
                Text("误报标记：${riskEvents.count { it.isFalseAlarm }} 条")
                Text("已反馈：${riskEvents.count { it.guardianFeedback != null }} 条")
            }
        }
        summaries.forEach { summary ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(summary.date, style = MaterialTheme.typography.titleSmall)
                    Text("最高评分：${"%.2f".format(summary.maxRiskScore)} / 异常 ${summary.riskEventCount} 条")
                    Text("误报 ${summary.falseAlarmCount} 条 / 确认 ${summary.confirmedAbnormalCount} 条 / 可信度 ${summary.dataQualityLevel.toQualityLabel()}")
                }
            }
        }
        if (riskEvents.isEmpty() && sessions.isEmpty()) {
            Text("还没有历史风险事件。")
        }
    }
}

@Composable
private fun GuardianDashboardScreen(
    settings: GuardianSettings,
    careMode: CareMode,
    policy: CareModePolicy,
    latestEvent: RiskEventEntity?,
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
        Text(careMode.guardianModeDescription(), style = MaterialTheme.typography.bodyMedium)
        if (careMode == CareMode.SPECIAL_CARE) {
            Card(Modifier.fillMaxWidth()) {
                Text("特殊关怀模式会采用更敏感的状态偏离提醒，但仍只处理结构化统计特征，且不提供医疗诊断。", modifier = Modifier.padding(14.dp))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("守护策略", style = MaterialTheme.typography.titleMedium)
                Text("当前模式：${careMode.toModeLabel()}")
                Text("提醒阈值：${"%.2f".format(policy.notificationThreshold)}")
                Text("每日提醒上限：${policy.maxDailyGuardianAlerts}")
                Text("最近事件：${latestEvent?.let { "${it.riskLevel.toRiskLabel(careMode)} ${it.timeRangeText()}" } ?: "暂无"}")
                if (careMode != CareMode.SELF_MONITORING) {
                    Text("当前联系人：${settings.name} / ${settings.relation}")
                    Slider(
                        value = settings.notifyThreshold,
                        onValueChange = { onSettingsChange(settings.copy(notifyThreshold = it)) },
                        valueRange = 0.55f..0.95f
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.enabled && careMode != CareMode.SELF_MONITORING,
                        onClick = { onSettingsChange(settings.copy(enabled = !settings.enabled)) },
                        enabled = careMode != CareMode.SELF_MONITORING,
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
        if (careMode == CareMode.SELF_MONITORING) {
            Card(Modifier.fillMaxWidth()) {
                Text("自我监测模式默认隐藏监护人提醒，只展示个人趋势和温和提醒。", modifier = Modifier.padding(14.dp))
            }
        } else {
            GuardianFeedbackButtons(onFeedback)
        }
    }
}

@Composable
private fun SettingsDashboardScreen(
    settings: GuardianSettings,
    careMode: CareMode,
    careModePolicy: CareModePolicy,
    onCareModeChange: (CareMode) -> Unit,
    onSettingsChange: (GuardianSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnectWear: () -> Unit,
    onClearHabitMemory: () -> Unit,
    onSeedDemoMode: (String) -> Unit,
    onDebugLog: () -> Unit
) {
    val context = LocalContext.current
    val typingStatus = AccessibilitySignalStore.status(context.applicationContext)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        CareModeSelector(careMode, careModePolicy, onCareModeChange)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("隐私与权限", style = MaterialTheme.typography.titleMedium)
                Text("不保存用户输入的完整文字，只保存打字速度、删除频率、停顿时长等统计特征。")
                Text("Agent 只接收结构化特征，不接收原始文本。健康数据默认本地保存。")
                Text("监护提醒必须用户授权。本系统不是医疗诊断工具。")
                Text("健康权限：用于心率、呼吸、运动状态和 Wear OS 数据。")
                Text("通知权限：用于本地波动提醒、守护确认和照护确认。")
                Text("网络权限：用于天气数据、Agent API 和可选远程分析；失败时会本地兜底。")
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开无障碍输入节奏权限")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("输入节奏采集状态", style = MaterialTheme.typography.titleMedium)
                Text("无障碍服务：${if (typingStatus.accessibilityEnabled) "已开启" else "未开启"}")
                Text("今日输入节奏样本：${typingStatus.todaySampleCount}")
                Text("最近采集时间：${typingStatus.lastCollectedAt.toReadableTime()}")
                Text("当前采集中：${if (typingStatus.collectingNow) "是" else "否"}")
                Text("系统只统计打字速度、删除频率、停顿时长和输入节奏变化。")
                Text("系统不会保存用户输入原文、聊天内容、密码内容或具体语义文本。")
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("前往系统无障碍设置")
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
        DemoModeCard(onSeedDemoMode)
        OutlinedButton(onClick = onDebugLog, modifier = Modifier.fillMaxWidth()) { Text("查看采集 Debug") }
        OutlinedButton(onClick = onClearHabitMemory, modifier = Modifier.fillMaxWidth()) { Text("清除本地习惯记忆和风险事件") }
    }
}

@Composable
private fun CareModeSelector(
    careMode: CareMode,
    policy: CareModePolicy,
    onCareModeChange: (CareMode) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("使用模式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CareMode.entries.forEach { mode ->
                    FilterChip(
                        selected = careMode == mode,
                        onClick = { onCareModeChange(mode) },
                        label = { Text(mode.toModeLabel()) }
                    )
                }
            }
            Text("当前策略：敏感度 ${"%.2f".format(policy.riskSensitivity)}，通知阈值 ${"%.2f".format(policy.notificationThreshold)}，隐私级别 ${policy.privacyLevel}")
        }
    }
}

@Composable
private fun DemoModeCard(onSeedDemoMode: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Demo Mode", style = MaterialTheme.typography.titleMedium)
            Text("写入结构化模拟数据，用于演示今日摘要、历史趋势和反馈调参。")
            OutlinedButton(onClick = { onSeedDemoMode("stable_day") }, modifier = Modifier.fillMaxWidth()) { Text("模拟稳定一天") }
            OutlinedButton(onClick = { onSeedDemoMode("mild_wave") }, modifier = Modifier.fillMaxWidth()) { Text("模拟轻度波动") }
            OutlinedButton(onClick = { onSeedDemoMode("night_event") }, modifier = Modifier.fillMaxWidth()) { Text("模拟夜间异常") }
            OutlinedButton(onClick = { onSeedDemoMode("guardian_confirmed") }, modifier = Modifier.fillMaxWidth()) { Text("模拟监护人确认后调参") }
        }
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

private fun List<RiskEventEntity>.toScorePoints(fallback: Float): List<Float> {
    val points = takeLast(8).map { it.riskScore }
    return points.ifEmpty { listOf(0.18f, 0.22f, 0.31f, fallback) }
}

private fun RiskEventEntity.reasonList(): List<String> =
    mainReasons.split("|").map { it.trim() }.filter { it.isNotEmpty() }

private fun RiskEventEntity.timeRangeText(): String {
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return if (startTime == endTime) {
        format.format(Date(startTime))
    } else {
        "${format.format(Date(startTime))} - ${format.format(Date(endTime))}"
    }
}

private fun String.toRiskLabel(mode: CareMode = CareMode.FAMILY_GUARDIAN): String = when (mode) {
    CareMode.SELF_MONITORING -> when (this) {
        "urgent_support", "guardian_check", "support" -> "明显波动"
        "observe" -> "轻度波动"
        "stable" -> "稳定"
        else -> this
    }
    CareMode.FAMILY_GUARDIAN -> when (this) {
        "urgent_support" -> "建议立即确认"
        "guardian_check" -> "建议确认"
        "support" -> "守护提醒"
        "observe" -> "观察"
        "stable" -> "稳定"
        else -> this
    }
    CareMode.SPECIAL_CARE -> when (this) {
        "urgent_support" -> "照护确认"
        "guardian_check" -> "状态偏离提醒"
        "support" -> "照护观察"
        "observe" -> "轻度偏离"
        "stable" -> "稳定"
        else -> this
    }
}

private fun CareMode.toModeLabel(): String = when (this) {
    CareMode.SELF_MONITORING -> "自我监测"
    CareMode.FAMILY_GUARDIAN -> "家庭守护"
    CareMode.SPECIAL_CARE -> "特殊关怀"
}

private fun CareMode.guardianModeDescription(): String = when (this) {
    CareMode.SELF_MONITORING -> "当前为自我监测模式，默认不通知监护人，提醒文案更温和。"
    CareMode.FAMILY_GUARDIAN -> "当前为家庭守护模式，可使用守护提醒和反馈调参。"
    CareMode.SPECIAL_CARE -> "当前为特殊关怀模式，提醒更敏感，同时限制每日提醒次数并强化隐私提示。"
}

private fun String.toQualityLabel(): String = when (this) {
    "high" -> "高"
    "medium" -> "中"
    "low" -> "低"
    else -> this
}

private fun String.toSegmentLabel(): String = when (this) {
    "late_night" -> "凌晨"
    "morning" -> "上午"
    "afternoon" -> "下午"
    "night" -> "夜间"
    "none" -> "暂无"
    else -> this
}

private fun Float.signedPercent(): String =
    "${if (this >= 0f) "+" else ""}${"%.0f".format(this)}%"

private fun heartRateSource(state: RealtimeUiState): String =
    if (state.bodyState.contains("Wear OS") || state.bodyState.contains("实时采集")) "Real" else "Mock"

private fun Long.toReadableTime(): String =
    if (this <= 0L) {
        "暂无"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))
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
