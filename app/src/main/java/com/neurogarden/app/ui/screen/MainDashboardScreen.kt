package com.neurogarden.app.ui.screen

import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurogarden.app.agent.ChatTextSanitizer
import com.neurogarden.app.algorithm.CareMode
import com.neurogarden.app.algorithm.CareModePolicy
import com.neurogarden.app.algorithm.DailyMonitoringSummary
import com.neurogarden.app.algorithm.DiscomfortBoundaryCalculator
import com.neurogarden.app.data.local.EmotionEvaluationRecordEntity
import com.neurogarden.app.data.local.FeedbackRecordEntity
import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.TherapySessionEntity
import com.neurogarden.app.guardian.CareLoopRecord
import com.neurogarden.app.guardian.GuardianFeedbackAction
import com.neurogarden.app.guardian.GuardianFeedbackRecord
import com.neurogarden.app.guardian.GuardianNotificationRecord
import com.neurogarden.app.guardian.GuardianSettingsSnapshot
import com.neurogarden.app.passive.AccessibilitySignalStore
import com.neurogarden.app.ui.component.NeuroAlertDialog
import com.neurogarden.app.ui.component.NeuroCard
import com.neurogarden.app.ui.component.NeuroColors
import com.neurogarden.app.ui.component.NeuroGradientCard
import com.neurogarden.app.ui.component.NeuroListRow
import com.neurogarden.app.ui.component.NeuroPrimaryButton
import com.neurogarden.app.ui.component.NeuroSecondaryButton
import com.neurogarden.app.viewmodel.DashboardChartData
import com.neurogarden.app.viewmodel.RealtimeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow

enum class MainTab(
    val title: String,
    val icon: String
) {
    TODAY("今日", "✦"),
    GUARDIAN("守护", "♡"),
    CHAT("话聊助手", "◌"),
    SETTINGS("设置", "●")
}

private data class MoodVisual(
    val title: String,
    val line: String,
    val faceColor: Color,
    val accentColor: Color,
    val textColor: Color
)

private data class HealthInfoDraft(
    val name: String = "",
    val phone: String = "",
    val age: String = "",
    val height: String = "",
    val bloodType: String = "",
    val bodyStatus: String = "",
    val medicalHistory: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val note: String = ""
)

private data class PersonalInfoDraft(
    val nickname: String = "林林",
    val name: String = "林林",
    val phone: String = "",
    val age: String = "",
    val gender: String = "",
    val height: String = "",
    val weight: String = "",
    val bloodType: String = "",
    val note: String = ""
)

@Composable
fun MainDashboardScreen(
    realtime: RealtimeUiState,
    sessions: List<TherapySessionEntity>,
    todayRiskEvents: List<RiskEventEntity>,
    recentRiskEvents: List<RiskEventEntity>,
    todaySummary: DailyMonitoringSummary,
    todayChartData: DashboardChartData,
    sevenDaySummaries: List<DailyMonitoringSummary>,
    careMode: CareMode,
    careModePolicy: CareModePolicy,
    wearConnectionStatus: String,
    feedbackRecords: List<FeedbackRecordEntity>,
    emotionEvaluations: List<EmotionEvaluationRecordEntity>,
    thresholdProfiles: List<ThresholdProfileEntity>,
    guardianSettings: GuardianSettings,
    guardianProfile: GuardianSettingsSnapshot,
    guardianNotificationRecords: List<GuardianNotificationRecord>,
    guardianFeedbackRecords: List<GuardianFeedbackRecord>,
    careLoopRecords: List<CareLoopRecord>,
    onGuardianSettingsChange: (GuardianSettings) -> Unit,
    onGuardianProfileChange: (GuardianSettingsSnapshot) -> Unit,
    onStartPassiveGuardian: () -> Unit,
    onStopPassiveGuardian: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onConnectWear: () -> Unit,
    onSendWearBreathPattern: () -> Unit,
    onContinueMock: () -> Unit,
    onFeedback: (String) -> Unit,
    onBeginSupportConversation: () -> Unit,
    onSendSupportReply: (String) -> Unit,
    onEventFeedback: (Long, String) -> Unit,
    onSimulateGuardianNotification: (RiskEventEntity) -> Unit,
    onSendGuardianSms: (RiskEventEntity) -> Unit,
    onRemoteGuardianFeedback: (RiskEventEntity, GuardianFeedbackAction) -> Unit,
    observeRiskEventById: (Long) -> Flow<RiskEventEntity?>,
    onClearHabitMemory: () -> Unit,
    onSeedDemoMode: (String) -> Unit,
    onCareModeChange: (CareMode) -> Unit,
    onDismissIntegrationDemoAlert: () -> Unit,
    onDebugLog: () -> Unit,
    openChatRequest: Int = 0
) {
    var tab by remember { mutableStateOf(MainTab.TODAY) }
    var selectedEventId by remember { mutableStateOf<Long?>(null) }
    var mindfulnessMode by remember { mutableStateOf(false) }
    var dismissedAlertEventId by remember { mutableStateOf<Long?>(null) }
    var dismissedAlertAt by remember { mutableStateOf(0L) }
    var showGuardianHealthEditor by remember { mutableStateOf(false) }
    var showPersonalInfoEditor by remember { mutableStateOf(false) }
    var showPermissionPage by remember { mutableStateOf(false) }
    var healthInfo by remember {
        mutableStateOf(
            HealthInfoDraft(
                emergencyName = guardianProfile.guardianName,
                emergencyPhone = guardianProfile.phone,
                note = guardianProfile.emergencyNote
            )
        )
    }
    var personalInfo by remember { mutableStateOf(PersonalInfoDraft()) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(openChatRequest) {
        if (openChatRequest > 0) {
            selectedEventId = null
            mindfulnessMode = false
            onBeginSupportConversation()
            tab = MainTab.CHAT
        }
    }

    val selectedEvent = selectedEventId
        ?.let { observeRiskEventById(it).collectAsState(initial = null).value }
    val latestEvent = todayRiskEvents.firstOrNull() ?: recentRiskEvents.firstOrNull()
    val alertEvent = latestEvent?.takeIf {
        tab == MainTab.TODAY &&
            it.id != dismissedAlertEventId &&
            selectedEventId == null &&
            System.currentTimeMillis() - dismissedAlertAt >= 15L * 60L * 1000L &&
            DiscomfortBoundaryCalculator.shouldShowPopup(
                it.riskScore,
                careMode,
                todaySummary.dataQualityLevel
            ) &&
            it.riskLevel != "stable"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = NeuroColors.Background,
        bottomBar = {
            if (!(tab == MainTab.CHAT && (mindfulnessMode || imeVisible))) {
                FloatingTabBar(
                    current = tab,
                    onSelect = {
                        selectedEventId = null
                        mindfulnessMode = false
                        tab = it
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NeuroColors.Background)
                .padding(if (mindfulnessMode || tab == MainTab.CHAT) PaddingValues(0.dp) else padding)
        ) {
            when (tab) {
                MainTab.TODAY -> TodayTab(
                    realtime = realtime,
                    summary = todaySummary,
                    chartData = todayChartData,
                    sevenDaySummaries = sevenDaySummaries,
                    events = todayRiskEvents.ifEmpty { recentRiskEvents },
                    wearConnectionStatus = wearConnectionStatus,
                    onOpenEvent = { selectedEventId = it.id },
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onConnectWear = onConnectWear
                )

                MainTab.GUARDIAN -> if (showGuardianHealthEditor) {
                    HealthInfoEditPage(
                        info = healthInfo,
                        onInfoChange = { healthInfo = it },
                        onSave = {
                            healthInfo = it
                            onGuardianProfileChange(
                                guardianProfile.copy(
                                    guardianName = it.emergencyName.ifBlank { guardianProfile.guardianName },
                                    phone = it.emergencyPhone.ifBlank { guardianProfile.phone },
                                    emergencyNote = listOf(
                                        it.bodyStatus.takeIf { value -> value.isNotBlank() }?.let { value -> "身体状况：$value" },
                                        it.medicalHistory.takeIf { value -> value.isNotBlank() }?.let { value -> "既往病史：$value" },
                                        it.note.takeIf { value -> value.isNotBlank() }?.let { value -> "备注：$value" }
                                    ).filterNotNull().joinToString("；").ifBlank { guardianProfile.emergencyNote }
                                )
                            )
                            showGuardianHealthEditor = false
                        },
                        onBack = { showGuardianHealthEditor = false }
                    )
                } else GuardianTab(
                    realtime = realtime,
                    summary = todaySummary,
                    settings = guardianSettings,
                    profile = guardianProfile,
                    notificationRecords = guardianNotificationRecords,
                    feedbackRecords = guardianFeedbackRecords,
                    careMode = careMode,
                    policy = careModePolicy,
                    latestEvent = latestEvent,
                    onSettingsChange = onGuardianSettingsChange,
                    onProfileChange = onGuardianProfileChange,
                    onStartPassiveGuardian = onStartPassiveGuardian,
                    onStopPassiveGuardian = onStopPassiveGuardian,
                    onFeedback = { feedback ->
                        latestEvent?.let { onEventFeedback(it.id, feedback) } ?: onFeedback(feedback)
                    },
                    onSimulateGuardianNotification = {
                        latestEvent?.let(onSimulateGuardianNotification)
                    },
                    onSendGuardianSms = {
                        latestEvent?.let(onSendGuardianSms)
                    },
                    onRemoteGuardianFeedback = { action ->
                        latestEvent?.let { onRemoteGuardianFeedback(it, action) }
                    },
                    onOpenHealthInfo = { showGuardianHealthEditor = true },
                    healthInfo = healthInfo
                )

                MainTab.CHAT -> ChatTab(
                    realtime = realtime,
                    latestEvent = latestEvent,
                    careMode = careMode,
                    mindfulnessMode = mindfulnessMode,
                    onMindfulnessChange = { mindfulnessMode = it },
                    onBeginSupportConversation = onBeginSupportConversation,
                    onSendSupportReply = onSendSupportReply
                )

                MainTab.SETTINGS -> when {
                    showPersonalInfoEditor -> PersonalInfoEditPage(
                        info = personalInfo,
                        onInfoChange = { personalInfo = it },
                        onSave = {
                            personalInfo = it
                            showPersonalInfoEditor = false
                        },
                        onBack = { showPersonalInfoEditor = false }
                    )
                    showPermissionPage -> DataPermissionPage(
                        typingEnabled = AccessibilitySignalStore.status(LocalContext.current.applicationContext).accessibilityEnabled,
                        overlayEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                            Settings.canDrawOverlays(LocalContext.current.applicationContext),
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onBack = { showPermissionPage = false }
                    )
                    else -> SettingsTab(
                    realtime = realtime,
                    settings = guardianSettings,
                    careMode = careMode,
                    policy = careModePolicy,
                    wearConnectionStatus = wearConnectionStatus,
                    feedbackRecords = feedbackRecords,
                    emotionEvaluations = emotionEvaluations,
                    thresholdProfiles = thresholdProfiles,
                    sessions = sessions,
                    careLoopRecords = careLoopRecords,
                    onCareModeChange = onCareModeChange,
                    onSettingsChange = onGuardianSettingsChange,
                    onProfileChange = onGuardianProfileChange,
                    profile = guardianProfile,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onConnectWear = onConnectWear,
                    onSendWearBreathPattern = onSendWearBreathPattern,
                    onContinueMock = onContinueMock,
                    onClearHabitMemory = onClearHabitMemory,
                    onSeedDemoMode = onSeedDemoMode,
                    onDebugLog = onDebugLog,
                    personalInfo = personalInfo,
                    onOpenPersonalInfo = { showPersonalInfoEditor = true },
                    onOpenPermissionPage = { showPermissionPage = true }
                )
                }
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailDialog(
            event = event,
            careMode = careMode,
            onDismiss = { selectedEventId = null },
            onOpenChat = {
                selectedEventId = null
                onBeginSupportConversation()
                tab = MainTab.CHAT
            },
            onSendGuardianSms = { onSendGuardianSms(event) }
        )
    }

    alertEvent?.let { event ->
        GentleRiskAlertDialog(
            event = event,
            careMode = careMode,
            onDismiss = {
                dismissedAlertEventId = event.id
                dismissedAlertAt = System.currentTimeMillis()
            },
            onOpenEvent = {
                dismissedAlertEventId = event.id
                dismissedAlertAt = System.currentTimeMillis()
                selectedEventId = event.id
            },
            onOpenChat = {
                dismissedAlertEventId = event.id
                dismissedAlertAt = System.currentTimeMillis()
                onBeginSupportConversation()
                tab = MainTab.CHAT
            },
            onSafe = {
                dismissedAlertEventId = event.id
                dismissedAlertAt = System.currentTimeMillis()
                onFeedback("我现在安全")
            },
            onNeedCompanion = {
                dismissedAlertEventId = event.id
                dismissedAlertAt = System.currentTimeMillis()
                onFeedback("我需要陪伴")
                onBeginSupportConversation()
                tab = MainTab.CHAT
            }
        )
    }

    realtime.integrationDemoAlert?.let { message ->
        NeuroAlertDialog(
            title = "联调演示提醒",
            confirmText = "知道了",
            onConfirm = onDismissIntegrationDemoAlert,
            onDismiss = onDismissIntegrationDemoAlert
        ) {
            Text(message, color = NeuroColors.TextSecondary)
        }
    }
}

@Composable
private fun FloatingTabBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(18.dp, RoundedCornerShape(30.dp), clip = false)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(30.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val selected = current == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) NeuroColors.BlueSoft else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(top = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        tab.icon,
                        color = if (selected) NeuroColors.Blue else NeuroColors.TextMuted,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tab.title,
                        color = if (selected) NeuroColors.Blue else NeuroColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayTab(
    realtime: RealtimeUiState,
    summary: DailyMonitoringSummary,
    chartData: DashboardChartData,
    sevenDaySummaries: List<DailyMonitoringSummary>,
    events: List<RiskEventEntity>,
    wearConnectionStatus: String,
    onOpenEvent: (RiskEventEntity) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnectWear: () -> Unit
) {
    val risk = latestRiskScore(events, realtime)
    val moodScore = riskToMoodScore(risk)
    val updateText = relativeTime(realtime.packet.timestamp)
    val temperature = estimatedTemperature(realtime, risk)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 38.dp, end = 22.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "你好，林林",
                color = NeuroColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "关注身心节律，把握更好的你",
                color = NeuroColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                title = "心率",
                value = "${realtime.packet.heartRate} BPM",
                subtitle = "$updateText · ${heartTrendLabel(realtime)}",
                icon = "♡",
                brush = Brush.linearGradient(listOf(Color(0xFFFF8A7A), Color(0xFFFF6F87))),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "呼吸",
                value = "${realtime.packet.breathRate} 次/分",
                subtitle = "$updateText · ${breathStateLabel(realtime.packet.breathRate)}",
                icon = "≋",
                brush = Brush.linearGradient(listOf(Color(0xFF8EE8C7), Color(0xFF68CFAF))),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                title = "体温",
                value = "%.1f°C".format(temperature),
                subtitle = "$updateText · 实时皮温",
                icon = "♨",
                brush = Brush.linearGradient(listOf(Color(0xFFC5B6FF), Color(0xFFA391F3))),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "设备",
                value = deviceValue(wearConnectionStatus),
                subtitle = deviceSubtitle(wearConnectionStatus),
                icon = "▣",
                brush = Brush.linearGradient(listOf(Color(0xFF91B4FF), Color(0xFF5E92F4))),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (wearConnectionStatus.contains("未连接")) onConnectWear() else onOpenBluetoothSettings()
                    }
            )
        }

        MoodScoreCard(score = moodScore)
        HistoryScoreCard(summaries = sevenDaySummaries, chartData = chartData)
        WarningRecordsCard(events = events, onOpenEvent = onOpenEvent)
        Text(
            summary.summaryText.ifBlank { "今日守护正在运行，实时数据会持续刷新。" },
            color = NeuroColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    brush: Brush,
    modifier: Modifier = Modifier
) {
    NeuroGradientCard(
        modifier = modifier.height(126.dp),
        brush = brush
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(icon, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                value,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun MoodScoreCard(score: Int) {
    val mood = moodVisual(score)
    NeuroCard(modifier = Modifier.fillMaxWidth()) {
        Text("今日节律评分", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            MoodFace(score = score, modifier = Modifier.size(132.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    score.toString(),
                    color = mood.textColor,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    mood.title,
                    color = NeuroColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    mood.line,
                    color = NeuroColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MoodFace(score: Int, modifier: Modifier = Modifier) {
    val visual = moodVisual(score)
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.31f
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = Stroke(width = 5f, cap = StrokeCap.Round)
        if (score >= 85) {
            repeat(14) { index ->
                val angle = (2 * PI * index / 14).toFloat()
                val start = Offset(
                    center.x + cos(angle) * radius * 1.28f,
                    center.y + sin(angle) * radius * 1.28f
                )
                val end = Offset(
                    center.x + cos(angle) * radius * 1.63f,
                    center.y + sin(angle) * radius * 1.63f
                )
                drawLine(visual.accentColor, start, end, strokeWidth = 7f, cap = StrokeCap.Round)
            }
        }
        drawCircle(visual.faceColor, radius, center)
        drawCircle(visual.accentColor.copy(alpha = 0.28f), radius * 0.19f, Offset(center.x - radius * 0.45f, center.y + radius * 0.10f))
        drawCircle(visual.accentColor.copy(alpha = 0.28f), radius * 0.19f, Offset(center.x + radius * 0.45f, center.y + radius * 0.10f))
        val ink = Color(0xFF26344D)
        when {
            score >= 70 -> {
                drawArc(
                    color = ink,
                    startAngle = 190f,
                    sweepAngle = 155f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.30f),
                    size = Size(radius * 0.38f, radius * 0.30f),
                    style = stroke
                )
                drawArc(
                    color = ink,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(center.x + radius * 0.18f, center.y - radius * 0.30f),
                    size = Size(radius * 0.38f, radius * 0.30f),
                    style = stroke
                )
                drawArc(
                    color = ink,
                    startAngle = 25f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.36f, center.y + radius * 0.02f),
                    size = Size(radius * 0.72f, radius * 0.50f),
                    style = stroke
                )
            }
            score >= 55 -> {
                drawLine(ink, Offset(center.x - radius * 0.52f, center.y - radius * 0.18f), Offset(center.x - radius * 0.20f, center.y - radius * 0.13f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(ink, Offset(center.x + radius * 0.20f, center.y - radius * 0.13f), Offset(center.x + radius * 0.52f, center.y - radius * 0.18f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(ink, Offset(center.x - radius * 0.28f, center.y + radius * 0.36f), Offset(center.x + radius * 0.28f, center.y + radius * 0.34f), strokeWidth = 5f, cap = StrokeCap.Round)
            }
            score >= 40 -> {
                drawArc(ink, 205f, 125f, false, Offset(center.x - radius * 0.55f, center.y - radius * 0.28f), Size(radius * 0.34f, radius * 0.22f), style = stroke)
                drawArc(ink, 210f, 120f, false, Offset(center.x + radius * 0.20f, center.y - radius * 0.28f), Size(radius * 0.34f, radius * 0.22f), style = stroke)
                drawArc(ink, 205f, 130f, false, Offset(center.x - radius * 0.32f, center.y + radius * 0.22f), Size(radius * 0.64f, radius * 0.42f), style = stroke)
            }
            else -> {
                drawLine(ink, Offset(center.x - radius * 0.48f, center.y - radius * 0.22f), Offset(center.x - radius * 0.25f, center.y + radius * 0.02f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(ink, Offset(center.x - radius * 0.25f, center.y - radius * 0.22f), Offset(center.x - radius * 0.48f, center.y + radius * 0.02f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(ink, Offset(center.x + radius * 0.25f, center.y - radius * 0.22f), Offset(center.x + radius * 0.48f, center.y + radius * 0.02f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(ink, Offset(center.x + radius * 0.48f, center.y - radius * 0.22f), Offset(center.x + radius * 0.25f, center.y + radius * 0.02f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawArc(ink, 205f, 130f, false, Offset(center.x - radius * 0.30f, center.y + radius * 0.20f), Size(radius * 0.60f, radius * 0.42f), style = stroke)
            }
        }
    }
}

@Composable
private fun HistoryScoreCard(
    summaries: List<DailyMonitoringSummary>,
    chartData: DashboardChartData
) {
    val points = historyMoodScores(summaries, chartData)
    NeuroCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("近 7 天节律趋势", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("本周评分均匀分布展示", color = NeuroColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("平均 ${points.average().roundToInt()}", color = NeuroColors.Blue, fontWeight = FontWeight.Bold)
        }
        ScoreLineChart(
            points = points,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, color = NeuroColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScoreLineChart(
    points: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val safePoints = points.ifEmpty { listOf(68, 72, 78, 84, 86, 81, 86) }
        val left = 20f
        val right = size.width - 20f
        val top = 16f
        val bottom = size.height - 28f
        val path = Path()
        safePoints.forEachIndexed { index, score ->
            val x = left + (right - left) * index / max(1, safePoints.lastIndex)
            val normalized = ((score - 20f) / 80f).coerceIn(0f, 1f)
            val y = bottom - normalized * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(NeuroColors.Blue, 6f, Offset(x, y))
            drawCircle(Color.White, 3f, Offset(x, y))
        }
        drawPath(path, NeuroColors.Blue, style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawLine(NeuroColors.Line, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)
    }
}

@Composable
private fun WarningRecordsCard(
    events: List<RiskEventEntity>,
    onOpenEvent: (RiskEventEntity) -> Unit
) {
    NeuroCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("近期提醒记录", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("查看全部", color = NeuroColors.Blue, style = MaterialTheme.typography.labelMedium)
        }
        if (events.isEmpty()) {
            DemoWarningRow("今天 08:32", "心率偏高提醒", "120 次/分")
            DemoWarningRow("昨天 15:40", "久坐提醒", "已久坐 82 分钟")
            DemoWarningRow("昨天 07:10", "睡眠不足提醒", "睡眠 5 小时 12 分")
        } else {
            events.take(4).forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenEvent(event) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallStatusDot(color = eventColor(event.riskScore))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.riskLevel.toRiskLabel(), color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(event.reasonList().take(1).joinToString().ifBlank { "状态出现短暂波动" }, color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(event.startTime.toReadableTime(), color = NeuroColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DemoWarningRow(time: String, title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SmallStatusDot(NeuroColors.Blue)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(detail, color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(time, color = NeuroColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SmallStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun GuardianTab(
    realtime: RealtimeUiState,
    summary: DailyMonitoringSummary,
    settings: GuardianSettings,
    profile: GuardianSettingsSnapshot,
    notificationRecords: List<GuardianNotificationRecord>,
    feedbackRecords: List<GuardianFeedbackRecord>,
    careMode: CareMode,
    policy: CareModePolicy,
    latestEvent: RiskEventEntity?,
    onSettingsChange: (GuardianSettings) -> Unit,
    onProfileChange: (GuardianSettingsSnapshot) -> Unit,
    onStartPassiveGuardian: () -> Unit,
    onStopPassiveGuardian: () -> Unit,
    onFeedback: (String) -> Unit,
    onSimulateGuardianNotification: () -> Unit,
    onSendGuardianSms: () -> Unit,
    onRemoteGuardianFeedback: (GuardianFeedbackAction) -> Unit,
    onOpenHealthInfo: () -> Unit,
    healthInfo: HealthInfoDraft
) {
    val context = LocalContext.current
    var emotionSensitivity by remember { mutableFloatStateOf(0.60f) }
    var bodySensitivity by remember { mutableFloatStateOf(0.50f) }
    var typingSensitivity by remember { mutableFloatStateOf(0.70f) }
    var showRecords by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 42.dp, end = 22.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("守护", color = NeuroColors.TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        NeuroCard(modifier = Modifier.fillMaxWidth()) {
            Text("阈值灵敏度设置", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            ThresholdSlider("情绪波动", emotionSensitivity, NeuroColors.Lavender) { emotionSensitivity = it }
            ThresholdSlider("身体信号", bodySensitivity, NeuroColors.Coral) { bodySensitivity = it }
            ThresholdSlider("输入节奏", typingSensitivity, NeuroColors.Blue) { typingSensitivity = it }
            ThresholdSlider("提醒频率", settings.notifyThreshold, NeuroColors.Amber) {
                onSettingsChange(settings.copy(notifyThreshold = it.coerceIn(0.55f, 0.95f)))
            }
            Text(
                "当前：适中，适合日常守护。敏感度越高，系统越早提示，但也可能增加打扰。",
                color = NeuroColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        NeuroCard(modifier = Modifier.fillMaxWidth()) {
            Text("运行状态", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(NeuroColors.BlueSoft)
                        .clickable { showRecords = true }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("今日提醒", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(todayNotificationCount(notificationRecords).toString(), color = NeuroColors.Blue, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("次", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI 评估", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        guardianEvaluation(realtime, summary, latestEvent),
                        color = NeuroColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.passiveGuardianRunning,
                    onClick = {
                        if (settings.passiveGuardianRunning) onStopPassiveGuardian() else onStartPassiveGuardian()
                    },
                    label = { Text(if (settings.passiveGuardianRunning) "守护运行中" else "启动守护") }
                )
                FilterChip(
                    selected = profile.notificationEnabled,
                    onClick = { onProfileChange(profile.copy(notificationEnabled = !profile.notificationEnabled)) },
                    label = { Text(if (profile.notificationEnabled) "通知已开" else "通知未开") }
                )
            }
        }

        NeuroListRow(
            title = "紧急联系人",
            subtitle = "${profile.guardianName.ifBlank { settings.name }} · ${profile.relationship.ifBlank { settings.relation }} · ${profile.authorizationStatus.displayName}",
            leading = { SmallStatusDot(NeuroColors.Coral) },
            trailing = { Text("编辑", color = NeuroColors.Blue) },
            modifier = Modifier.clickable { onOpenHealthInfo() }
        )
        NeuroListRow(
            title = "个人简介 / 病例信息",
            subtitle = healthInfo.bodyStatus.ifBlank {
                healthInfo.medicalHistory.ifBlank {
                    profile.emergencyNote.ifBlank { "点击补充基础健康信息" }
                }
            },
            leading = { SmallStatusDot(NeuroColors.Blue) },
            trailing = { Text("编辑", color = NeuroColors.Blue) },
            modifier = Modifier.clickable { onOpenHealthInfo() }
        )

        if (latestEvent != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NeuroSecondaryButton(
                    text = "模拟通知",
                    onClick = {
                        onSimulateGuardianNotification()
                        Toast.makeText(context, "模拟通知已发送", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                NeuroSecondaryButton(
                    text = "短信通知",
                    onClick = {
                        onSendGuardianSms()
                        Toast.makeText(context, "已打开短信通知草稿", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NeuroSecondaryButton(
                    text = "我还好",
                    onClick = {
                        onFeedback("我还好")
                        Toast.makeText(context, "已记录：我还好", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NeuroSecondaryButton(
                    text = "继续观察",
                    onClick = {
                        onRemoteGuardianFeedback(GuardianFeedbackAction.KEEP_WATCHING)
                        Toast.makeText(context, "已继续观察", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                NeuroSecondaryButton(
                    text = "标记误报",
                    onClick = {
                        onRemoteGuardianFeedback(GuardianFeedbackAction.FALSE_POSITIVE)
                        Toast.makeText(context, "已标记为误报", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            "模式：${careMode.toModeLabel()} · 提醒阈值 ${"%.0f".format(policy.notificationThreshold * 100)}% · 反馈记录 ${feedbackRecords.size}",
            color = NeuroColors.TextMuted,
            style = MaterialTheme.typography.labelMedium
        )
    }

    if (showRecords) {
        NeuroAlertDialog(
            title = "今日提醒记录",
            confirmText = "知道了",
            onConfirm = { showRecords = false },
            onDismiss = { showRecords = false }
        ) {
            if (notificationRecords.isEmpty()) {
                Text("今天还没有提醒记录。", color = NeuroColors.TextSecondary)
            } else {
                notificationRecords.take(6).forEach {
                    Text("${it.sentAt.toReadableTime()} · ${it.status.displayName} · ${it.reason}", color = NeuroColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ThresholdSlider(
    title: String,
    value: Float,
    color: Color,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = NeuroColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text("${"%.0f".format(value * 100)}%", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChatTab(
    realtime: RealtimeUiState,
    latestEvent: RiskEventEntity?,
    careMode: CareMode,
    mindfulnessMode: Boolean,
    onMindfulnessChange: (Boolean) -> Unit,
    onBeginSupportConversation: () -> Unit,
    onSendSupportReply: (String) -> Unit
) {
    val score = riskToMoodScore(latestRiskScore(listOfNotNull(latestEvent), realtime))
    var draft by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val keyboardHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val keyboardVisible = keyboardHeight > 0.dp
    val inputBottomPadding = if (keyboardVisible) keyboardHeight + 8.dp else 106.dp
    val contentBottomPadding = inputBottomPadding + 82.dp
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        onBeginSupportConversation()
    }

    LaunchedEffect(realtime.supportMessages.size, keyboardVisible) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    if (mindfulnessMode) {
        MindfulnessMode(score = score, onExit = { onMindfulnessChange(false) })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 22.dp, top = 42.dp, end = 22.dp, bottom = contentBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(Modifier.size(44.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text("NeuroGarden", color = NeuroColors.Blue, fontWeight = FontWeight.Bold)
                }
                MindfulnessIcon(onClick = { onMindfulnessChange(true) })
            }

            Spacer(Modifier.height(if (keyboardVisible) 2.dp else 26.dp))
            ParticleSphere(score = score, size = if (keyboardVisible) 178.dp else 260.dp)
            if (realtime.supportMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    realtime.supportMessages.forEach { message ->
                        ChatBubble(fromUser = message.fromUser, text = ChatTextSanitizer.cleanAssistantReply(message.text))
                    }
                }
            } else {
                Text(
                    "可以从一句很短的话开始。这里不会保存被动采集到的输入原文。",
                    color = NeuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "当前模式：${careMode.toModeLabel()}",
                color = NeuroColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        ChatInputBar(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    onSendSupportReply(text)
                    draft = ""
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 22.dp, end = 22.dp, bottom = inputBottomPadding)
        )

    }
}
@Composable
private fun MindfulnessMode(score: Int, onExit: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onExit
            )
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        ParticleSphere(score = score, size = 310.dp, mindfulness = true)
        Text(
            "吸气，跟着光慢慢变大\n呼气，慢慢放下\n轻触屏幕返回",
            color = NeuroColors.TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ParticleSphere(
    score: Int,
    size: Dp,
    mindfulness: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "particle-sphere")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mindfulness) 9800 else sphereSpeed(score)),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val breath by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mindfulness) 4300 else breathSpeed(score)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val baseColor = sphereColor(score)
    Canvas(modifier = Modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.36f * breath
        val count = if (mindfulness) 720 else 560
        val golden = PI * (3.0 - kotlin.math.sqrt(5.0))
        repeat(count) { index ->
            val k = index + 0.5
            val phi = acos(1.0 - 2.0 * k / count)
            val theta = golden * index + rotation * 2.0 * PI
            val x3 = cos(theta) * sin(phi)
            val y3 = sin(theta) * sin(phi)
            val z3 = cos(phi)
            val perspective = 0.72 + 0.28 * ((z3 + 1.0) / 2.0)
            val x = center.x + (x3 * radius * perspective).toFloat()
            val y = center.y + (y3 * radius * perspective).toFloat()
            val alpha = (0.10 + 0.46 * ((z3 + 1.0) / 2.0)).toFloat()
            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = (0.85f + 1.15f * perspective.toFloat()) * if (mindfulness) 1.06f else 1f,
                center = Offset(x, y)
            )
        }
        repeat(3) { ring ->
            drawCircle(
                color = baseColor.copy(alpha = 0.10f - ring * 0.025f),
                radius = radius * (0.92f + ring * 0.18f),
                center = center,
                style = Stroke(width = 1.2f + ring)
            )
        }
    }
}

@Composable
private fun CircleIconButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MindfulnessIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.Transparent, size.minDimension * 0.44f, c, style = Stroke(3.5f, cap = StrokeCap.Round))
            drawCircle(Color.Black, size.minDimension * 0.11f, c, style = Stroke(3f, cap = StrokeCap.Round))
            drawArc(Color.Black, 12f, 130f, false, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), style = Stroke(3.2f, cap = StrokeCap.Round))
            drawArc(Color.Black, 198f, 128f, false, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), style = Stroke(3.2f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun ChatBubble(fromUser: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 286.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (fromUser) NeuroColors.Blue else Color.White)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                color = if (fromUser) Color.White else NeuroColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = NeuroColors.TextPrimary,
            fontSize = 17.sp
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(18.dp, RoundedCornerShape(29.dp), clip = false)
            .clip(RoundedCornerShape(29.dp))
            .background(Color.White),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text("说说现在的感觉", color = NeuroColors.TextMuted, fontSize = 17.sp)
                    }
                    innerTextField()
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeuroColors.Blue)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↵", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun SettingsTab(
    realtime: RealtimeUiState,
    settings: GuardianSettings,
    careMode: CareMode,
    policy: CareModePolicy,
    wearConnectionStatus: String,
    feedbackRecords: List<FeedbackRecordEntity>,
    emotionEvaluations: List<EmotionEvaluationRecordEntity>,
    thresholdProfiles: List<ThresholdProfileEntity>,
    sessions: List<TherapySessionEntity>,
    careLoopRecords: List<CareLoopRecord>,
    profile: GuardianSettingsSnapshot,
    onCareModeChange: (CareMode) -> Unit,
    onSettingsChange: (GuardianSettings) -> Unit,
    onProfileChange: (GuardianSettingsSnapshot) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onConnectWear: () -> Unit,
    onSendWearBreathPattern: () -> Unit,
    onContinueMock: () -> Unit,
    onClearHabitMemory: () -> Unit,
    onSeedDemoMode: (String) -> Unit,
    onDebugLog: () -> Unit,
    personalInfo: PersonalInfoDraft,
    onOpenPersonalInfo: () -> Unit,
    onOpenPermissionPage: () -> Unit
) {
    val context = LocalContext.current
    val typingStatus = AccessibilitySignalStore.status(context.applicationContext)
    val overlayEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        Settings.canDrawOverlays(context.applicationContext)
    var showDebug by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 42.dp, end = 22.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", color = NeuroColors.TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        NeuroCard(modifier = Modifier.fillMaxWidth().clickable { onOpenPersonalInfo() }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFE6F4FF), Color(0xFFFFE8EF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(personalInfo.nickname.take(1).ifBlank { "N" }, color = NeuroColors.Blue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(personalInfo.nickname.ifBlank { personalInfo.name.ifBlank { "未填写昵称" } }, color = NeuroColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${personalInfo.name.ifBlank { "未填写姓名" }} · ${personalInfo.phone.ifBlank { "未填写手机号" }}", color = NeuroColors.TextSecondary)
                    Text(wearConnectionStatus, color = if (wearConnectionStatus.contains("连接")) NeuroColors.Mint else NeuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("编辑", color = NeuroColors.Blue, style = MaterialTheme.typography.labelMedium)
            }
        }

        NeuroCard(modifier = Modifier.fillMaxWidth()) {
            Text("使用模式", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CareMode.entries.forEach { mode ->
                    FilterChip(
                        selected = careMode == mode,
                        onClick = { onCareModeChange(mode) },
                        label = { Text(mode.toModeLabel()) }
                    )
                }
            }
            Text(careModeDescription(careMode), color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("策略：敏感度 ${"%.0f".format(policy.riskSensitivity * 100)}%，每日提醒上限 ${policy.maxDailyGuardianAlerts}", color = NeuroColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        }

        NeuroListRow(
            title = "数据与权限",
            subtitle = "输入节奏 ${if (typingStatus.accessibilityEnabled) "已开启" else "未开启"} · 悬浮窗 ${if (overlayEnabled) "已开启" else "未开启"}",
            leading = { SmallStatusDot(NeuroColors.Blue) },
            trailing = { Text("进入", color = NeuroColors.Blue) },
            modifier = Modifier.clickable { onOpenPermissionPage() }
        )
        NeuroListRow(
            title = "开发者调试",
            subtitle = "模拟输入、无障碍、蓝牙和演示数据",
            leading = { SmallStatusDot(NeuroColors.Lavender) },
            trailing = {
                Text(if (showDebug) "收起" else "›", color = NeuroColors.TextMuted)
            },
            modifier = Modifier.clickable { showDebug = !showDebug }
        )

        if (showDebug) {
            NeuroCard(modifier = Modifier.fillMaxWidth(), containerColor = NeuroColors.CardSoft) {
                Text("开发阶段工具", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("实时：心率 ${realtime.packet.heartRate}，呼吸 ${realtime.packet.breathRate}，样本 ${typingStatus.todaySampleCount}", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("记录：反馈 ${feedbackRecords.size}，情绪评估 ${emotionEvaluations.size}，阈值 ${thresholdProfiles.size}，疗愈 ${sessions.size}，闭环 ${careLoopRecords.size}", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NeuroSecondaryButton("切换模拟", onContinueMock, Modifier.weight(1f))
                    NeuroSecondaryButton("导入演示", { onSeedDemoMode("acceptance_week") }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NeuroSecondaryButton("无障碍", onOpenAccessibilitySettings, Modifier.weight(1f))
                    NeuroSecondaryButton("蓝牙", onOpenBluetoothSettings, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NeuroSecondaryButton("悬浮窗", onOpenOverlaySettings, Modifier.weight(1f))
                    NeuroSecondaryButton("Debug 日志", onDebugLog, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NeuroSecondaryButton("连接手表", onConnectWear, Modifier.weight(1f))
                    NeuroSecondaryButton("呼吸引导", onSendWearBreathPattern, Modifier.weight(1f))
                }
                FilterChip(
                    selected = settings.watchSimulationEnabled,
                    onClick = { onSettingsChange(settings.copy(watchSimulationEnabled = !settings.watchSimulationEnabled)) },
                    label = { Text(if (settings.watchSimulationEnabled) "模拟手表已开启" else "开启模拟手表") }
                )
                Slider(
                    value = settings.simulatedHeartRate.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(simulatedHeartRate = it.roundToInt())) },
                    valueRange = 60f..130f
                )
                Text("模拟心率 ${settings.simulatedHeartRate} BPM", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.simulatedBreathRate.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(simulatedBreathRate = it.roundToInt())) },
                    valueRange = 8f..32f
                )
                Text("模拟呼吸 ${settings.simulatedBreathRate} 次/分", color = NeuroColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                NeuroSecondaryButton("清除本地习惯记忆", onClearHabitMemory, Modifier.fillMaxWidth())
            }
        }

        NeuroCard(modifier = Modifier.fillMaxWidth()) {
            Text("守护资料", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("紧急联系人：${profile.guardianName} / ${profile.relationship}", color = NeuroColors.TextSecondary)
            Text("通知状态：${if (profile.notificationEnabled) "已开启" else "未开启"}", color = NeuroColors.TextSecondary)
            NeuroPrimaryButton(
                text = if (profile.notificationEnabled) "关闭守护通知" else "开启守护通知",
                onClick = { onProfileChange(profile.copy(notificationEnabled = !profile.notificationEnabled)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HealthInfoEditPage(
    info: HealthInfoDraft,
    onInfoChange: (HealthInfoDraft) -> Unit,
    onSave: (HealthInfoDraft) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember(info) { mutableStateOf(info) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 42.dp, end = 22.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("个人健康信息", color = NeuroColors.TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        NeuroCard(modifier = Modifier.fillMaxWidth()) {
            EditField("姓名", draft.name) { draft = draft.copy(name = it); onInfoChange(draft) }
            EditField("手机号码", draft.phone) { draft = draft.copy(phone = it); onInfoChange(draft) }
            EditField("年龄", draft.age) { draft = draft.copy(age = it); onInfoChange(draft) }
            EditField("身高", draft.height) { draft = draft.copy(height = it); onInfoChange(draft) }
            EditField("血型", draft.bloodType) { draft = draft.copy(bloodType = it); onInfoChange(draft) }
            EditField("身体状况", draft.bodyStatus) { draft = draft.copy(bodyStatus = it); onInfoChange(draft) }
            EditField("既往病史", draft.medicalHistory) { draft = draft.copy(medicalHistory = it); onInfoChange(draft) }
            EditField("紧急联系人姓名", draft.emergencyName) { draft = draft.copy(emergencyName = it); onInfoChange(draft) }
            EditField("紧急联系人电话", draft.emergencyPhone) { draft = draft.copy(emergencyPhone = it); onInfoChange(draft) }
            EditField("备注", draft.note) { draft = draft.copy(note = it); onInfoChange(draft) }
        }
        NeuroPrimaryButton("保存", { onSave(draft) }, Modifier.fillMaxWidth())
        NeuroSecondaryButton("返回", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PersonalInfoEditPage(
    info: PersonalInfoDraft,
    onInfoChange: (PersonalInfoDraft) -> Unit,
    onSave: (PersonalInfoDraft) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember(info) { mutableStateOf(info) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 42.dp, end = 22.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("个人信息", color = NeuroColors.TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        NeuroCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE6F4FF), Color(0xFFFFE8EF)))),
                contentAlignment = Alignment.Center
            ) {
                Text(draft.nickname.take(1).ifBlank { "N" }, color = NeuroColors.Blue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            EditField("昵称", draft.nickname) { draft = draft.copy(nickname = it); onInfoChange(draft) }
            EditField("姓名", draft.name) { draft = draft.copy(name = it); onInfoChange(draft) }
            EditField("手机号", draft.phone) { draft = draft.copy(phone = it); onInfoChange(draft) }
            EditField("年龄", draft.age) { draft = draft.copy(age = it); onInfoChange(draft) }
            EditField("性别", draft.gender) { draft = draft.copy(gender = it); onInfoChange(draft) }
            EditField("身高", draft.height) { draft = draft.copy(height = it); onInfoChange(draft) }
            EditField("体重", draft.weight) { draft = draft.copy(weight = it); onInfoChange(draft) }
            EditField("血型", draft.bloodType) { draft = draft.copy(bloodType = it); onInfoChange(draft) }
            EditField("备注", draft.note) { draft = draft.copy(note = it); onInfoChange(draft) }
        }
        NeuroPrimaryButton("保存", { onSave(draft) }, Modifier.fillMaxWidth())
        NeuroSecondaryButton("返回", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DataPermissionPage(
    typingEnabled: Boolean,
    overlayEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 42.dp, end = 22.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("数据与权限", color = NeuroColors.TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        PermissionEntryCard(
            title = "无障碍权限",
            status = if (typingEnabled) "已开启" else "未开启",
            description = "用于统计打字速度、删除频率和停顿时长，不读取输入原文。",
            button = "去开启",
            onClick = onOpenAccessibilitySettings
        )
        PermissionEntryCard(
            title = "显示在其他应用上",
            status = if (overlayEnabled) "已开启" else "未开启",
            description = "用于展示悬浮提醒、守护提示或紧急状态信息。",
            button = "去开启",
            onClick = onOpenOverlaySettings
        )
        Text("NeuroGarden 仅保存结构化统计特征，不保存输入原文，也不提供医学诊断。", color = NeuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        NeuroSecondaryButton("返回", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionEntryCard(
    title: String,
    status: String,
    description: String,
    button: String,
    onClick: () -> Unit
) {
    NeuroCard(modifier = Modifier.fillMaxWidth()) {
        Text("$title：$status", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(description, color = NeuroColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        NeuroPrimaryButton(button, onClick, Modifier.fillMaxWidth())
    }
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = label !in listOf("身体状况", "既往病史", "备注")
    )
}

@Composable
private fun GentleRiskAlertDialog(
    event: RiskEventEntity,
    careMode: CareMode,
    onDismiss: () -> Unit,
    onOpenEvent: () -> Unit,
    onOpenChat: () -> Unit,
    onSafe: () -> Unit,
    onNeedCompanion: () -> Unit
) {
    NeuroAlertDialog(
        title = "检测到状态波动",
        confirmText = "和我聊聊",
        dismissText = "先知道了",
        onConfirm = onOpenChat,
        onDismiss = onDismiss
    ) {
        Text("我注意到你的节奏和日常相比有些不一样。你不用解释原因，我们可以先轻轻确认一下。", color = NeuroColors.TextSecondary)
        Text("当前：${event.riskLevel.toRiskLabel(careMode)} / 风险 ${"%.0f".format(event.riskScore * 100)}%", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(event.reasonList().take(2).joinToString("；").ifBlank { "结构化信号出现短暂偏离" }, color = NeuroColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSafe) { Text("我还好", color = NeuroColors.Blue) }
            TextButton(onClick = onNeedCompanion) { Text("想有人陪", color = NeuroColors.Blue) }
            TextButton(onClick = onOpenEvent) { Text("详情", color = NeuroColors.Blue) }
        }
    }
}

@Composable
private fun EventDetailDialog(
    event: RiskEventEntity,
    careMode: CareMode,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onSendGuardianSms: () -> Unit
) {
    NeuroAlertDialog(
        title = "预警详情",
        confirmText = "知道了",
        onConfirm = onDismiss,
        onDismiss = onDismiss
    ) {
        Text("${event.timeRangeText()} · ${event.riskLevel.toRiskLabel(careMode)}", color = NeuroColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(event.reasonList().joinToString("；").ifBlank { "暂无结构化原因。" }, color = NeuroColors.TextSecondary)
        Text("建议：${event.suggestedAction.ifBlank { "先慢下来，观察一下当前感受。" }}", color = NeuroColors.TextSecondary)
        Text("Agent：${event.agentAnalysis.ifBlank { "暂无模型补充说明。" }}", color = NeuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onOpenChat) {
            Text("去话聊", color = NeuroColors.Blue)
        }
        TextButton(onClick = onSendGuardianSms) {
            Text("短信通知监护人", color = NeuroColors.Blue)
        }
    }
}

private fun moodVisual(score: Int): MoodVisual = when {
    score >= 85 -> MoodVisual(
        title = "积极",
        line = "今天节律不错，继续保持。",
        faceColor = Color(0xFFFFC75D),
        accentColor = Color(0xFFFF9F43),
        textColor = Color(0xFFFF8A00)
    )
    score >= 70 -> MoodVisual(
        title = "温和",
        line = "整体还算平稳，适合慢慢推进今天。",
        faceColor = Color(0xFFDDF4E6),
        accentColor = Color(0xFF67C18C),
        textColor = Color(0xFF4E986A)
    )
    score >= 55 -> MoodVisual(
        title = "有点疲惫",
        line = "今天有点累，可以把节奏放慢一些。",
        faceColor = Color(0xFFD9E7FF),
        accentColor = Color(0xFF7BA0D9),
        textColor = Color(0xFF4772B1)
    )
    score >= 40 -> MoodVisual(
        title = "低落",
        line = "今天有点低落，要不要留一点空间给自己？",
        faceColor = Color(0xFFD8E8F8),
        accentColor = Color(0xFF6F95BF),
        textColor = Color(0xFF4F77A3)
    )
    score >= 20 -> MoodVisual(
        title = "吃力",
        line = "状态有些吃力，需要我陪你缓一会儿吗？",
        faceColor = Color(0xFFDCE9B9),
        accentColor = Color(0xFF99B95B),
        textColor = Color(0xFF658640)
    )
    else -> MoodVisual(
        title = "高压",
        line = "现在可能很难受，我们先把呼吸放慢一点。",
        faceColor = Color(0xFFF4A7A2),
        accentColor = Color(0xFFE35D56),
        textColor = Color(0xFFC44741)
    )
}

private fun latestRiskScore(events: List<RiskEventEntity>, state: RealtimeUiState): Float =
    (events.firstOrNull()?.riskScore ?: state.personalizedRisk.riskScore).coerceIn(0f, 1f)

private fun riskToMoodScore(risk: Float): Int =
    (100 - risk.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)

private fun historyMoodScores(
    summaries: List<DailyMonitoringSummary>,
    chartData: DashboardChartData
): List<Int> {
    val fromSummary = summaries.takeLast(7).map {
        riskToMoodScore(it.averageRiskScore)
    }
    if (fromSummary.any { it < 100 }) return fromSummary
    val fromChart = chartData.riskScores.takeLast(7).map(::riskToMoodScore)
    return fromChart.ifEmpty { listOf(68, 72, 78, 84, 86, 81, 86) }
}

private fun estimatedTemperature(state: RealtimeUiState, risk: Float): Float =
    (36.4f + state.packet.motionLevel.coerceIn(0f, 1f) * 0.20f + risk.coerceIn(0f, 1f) * 0.18f)

private fun heartTrendLabel(state: RealtimeUiState): String {
    val delta = state.packet.heartRate - state.packet.baselineHeartRate
    return when {
        delta > 8 -> "较平时偏高"
        delta < -8 -> "较平时偏低"
        else -> "接近基线"
    }
}

private fun breathStateLabel(rate: Int): String = when {
    rate <= 0 -> "暂无数据"
    rate < 12 -> "偏慢"
    rate <= 20 -> "平稳"
    else -> "偏快"
}

private fun deviceValue(status: String): String =
    if (status.contains("连接") && !status.contains("未连接") && !status.contains("失败")) "已连接" else "未连接"

private fun deviceSubtitle(status: String): String =
    when {
        status.contains("未连接") -> "连接手表"
        status.contains("失败") -> "检查蓝牙"
        else -> status.take(18)
    }

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "暂无数据"
    val minutes = max(0L, (System.currentTimeMillis() - timestamp) / 60000L)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        else -> "${minutes / 60} 小时前"
    }
}

private fun sphereColor(score: Int): Color = when {
    score >= 70 -> NeuroColors.Teal
    score >= 55 -> Color(0xFF7CB8F0)
    score >= 40 -> Color(0xFF8DA7D4)
    score >= 20 -> Color(0xFFD88581)
    else -> Color(0xFFD94E4B)
}

private fun sphereSpeed(score: Int): Int = when {
    score >= 70 -> 13000
    score >= 55 -> 16000
    score >= 40 -> 19000
    score >= 20 -> 9500
    else -> 7600
}

private fun breathSpeed(score: Int): Int = when {
    score >= 70 -> 5200
    score >= 55 -> 6600
    score >= 40 -> 7600
    score >= 20 -> 3300
    else -> 2600
}

private fun eventColor(score: Float): Color = when {
    score >= 0.80f -> NeuroColors.Danger
    score >= 0.55f -> NeuroColors.Amber
    else -> NeuroColors.Blue
}

private fun todayNotificationCount(records: List<GuardianNotificationRecord>): Int {
    val start = System.currentTimeMillis().startOfDay()
    return records.count { it.sentAt >= start }
}

private fun guardianEvaluation(
    realtime: RealtimeUiState,
    summary: DailyMonitoringSummary,
    latestEvent: RiskEventEntity?
): String {
    val agentReason = realtime.guardianRuntimeStatus.lastAgentReason
    if (agentReason.isNotBlank() && agentReason != "等待结构化数据") {
        return agentReason
    }
    latestEvent?.let {
        return "今天出现过一次 ${it.riskLevel.toRiskLabel()}，主要来自 ${it.reasonList().take(2).joinToString("、")}。当前建议继续观察输入节奏和身体信号的同步变化。"
    }
    return summary.summaryText.ifBlank { "今天整体处于可观察范围内，建议保持温和提醒，并继续积累个人基线。" }
}

private fun careModeDescription(mode: CareMode): String = when (mode) {
    CareMode.SELF_MONITORING -> "提醒更克制，数据主要用于自己查看。"
    CareMode.FAMILY_GUARDIAN -> "在明显波动时可通知守护联系人。"
    CareMode.SPECIAL_CARE -> "提醒更敏感，但每日打扰次数受限。"
}

private fun CareMode.toModeLabel(): String = when (this) {
    CareMode.SELF_MONITORING -> "自我检测"
    CareMode.FAMILY_GUARDIAN -> "家庭守护"
    CareMode.SPECIAL_CARE -> "特殊关怀"
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

private fun Long.toReadableTime(): String =
    if (this <= 0L) {
        "暂无"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))
    }

private fun Long.startOfDay(): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = this
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}
