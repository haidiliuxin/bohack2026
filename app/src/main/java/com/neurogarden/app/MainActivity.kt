package com.neurogarden.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.neurogarden.app.passive.PassiveGuardianService
import com.neurogarden.app.passive.PendingPassiveAlertStore
import com.neurogarden.app.passive.PassiveOverlayAlert
import com.neurogarden.app.passive.WatchSignalSettings
import com.neurogarden.app.passive.WatchSignalStore
import com.neurogarden.app.guardian.CareLoopRecord
import com.neurogarden.app.guardian.GuardianAuthorizationStatus
import com.neurogarden.app.guardian.GuardianFeedbackAction
import com.neurogarden.app.guardian.GuardianFeedbackRecord
import com.neurogarden.app.guardian.GuardianNotificationChannel
import com.neurogarden.app.guardian.GuardianNotificationRecord
import com.neurogarden.app.guardian.GuardianNotificationService
import com.neurogarden.app.guardian.GuardianNotificationStatus
import com.neurogarden.app.guardian.GuardianSettingsSnapshot
import com.neurogarden.app.guardian.SpecialCareService
import com.neurogarden.app.ui.screen.DebugLogScreen
import com.neurogarden.app.ui.screen.GuardianSettings
import com.neurogarden.app.ui.screen.MainDashboardScreen
import com.neurogarden.app.ui.component.NeuroAlertDialog
import com.neurogarden.app.ui.component.PermissionRequestDialog
import com.neurogarden.app.ui.component.PermissionChecker
import com.neurogarden.app.ui.theme.NeuroGardenTheme
import com.neurogarden.app.viewmodel.MainViewModel
import com.neurogarden.app.viewmodel.TherapyViewModel
import com.neurogarden.app.wear.WearCommandSender
import com.neurogarden.shared.util.JsonUtil
import com.neurogarden.shared.wear.WearPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val openChatRequests = MutableStateFlow(0)
    private val openMindfulnessRequests = MutableStateFlow(0)

    private val mainViewModel by viewModels<MainViewModel> {
        MainViewModel.Factory(
            (application as NeuroGardenApp).habitRepository,
            (application as NeuroGardenApp).riskEventRepository,
            (application as NeuroGardenApp).repository,
            (application as NeuroGardenApp).weatherRepository,
            (application as NeuroGardenApp).careModeStore,
            (application as NeuroGardenApp).agentAuditLogRepository,
            (application as NeuroGardenApp).guardianAgentApi
        )
    }
    private val therapyViewModel by viewModels<TherapyViewModel> {
        TherapyViewModel.Factory((application as NeuroGardenApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOpenChatIntent(intent)
        setContent {
            val openChatRequest by openChatRequests.collectAsStateWithLifecycle()
            val openMindfulnessRequest by openMindfulnessRequests.collectAsStateWithLifecycle()
            NeuroGardenTheme {
                NeuroGardenRoot(mainViewModel, therapyViewModel, openChatRequest, openMindfulnessRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenChatIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        runCatching { Wearable.getDataClient(this).addListener(this) }
        PassiveOverlayAlert.dismiss(this)
    }

    override fun onPause() {
        runCatching { Wearable.getDataClient(this).removeListener(this) }
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearPaths.SENSOR_PACKET) {
                val payload = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearPaths.PAYLOAD)
                val packet = payload?.let { JsonUtil.parseSensorPacketJson(it) } ?: return@forEach
                WatchSignalStore.saveRealPacket(this, packet)
                lifecycleScope.launch {
                    mainViewModel.ingestWearPacket(packet, bodyState = "Wear OS 实时采集")
                }
            }
        }
    }

    private fun handleOpenChatIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_BREATHING || intent?.getBooleanExtra(EXTRA_OPEN_BREATHING, false) == true) {
            openMindfulnessRequests.value = openMindfulnessRequests.value + 1
        } else if (intent?.action == ACTION_OPEN_CHAT || intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true) {
            openChatRequests.value = openChatRequests.value + 1
        }
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.neurogarden.app.OPEN_CHAT"
        const val ACTION_OPEN_BREATHING = "com.neurogarden.app.OPEN_BREATHING"
        const val EXTRA_OPEN_CHAT = "open_chat"
        const val EXTRA_OPEN_BREATHING = "open_breathing"
    }
}

@Composable
private fun NeuroGardenRoot(
    mainViewModel: MainViewModel,
    therapyViewModel: TherapyViewModel,
    externalOpenChatRequest: Int,
    externalOpenMindfulnessRequest: Int
) {
    var guardianSettings by remember { mutableStateOf(GuardianSettings()) }
    var guardianProfile by remember {
        mutableStateOf(
            GuardianSettingsSnapshot(
                guardianName = "妈妈",
                relationship = "家人",
                phone = "",
                wechat = "",
                email = "",
                notificationEnabled = false,
                notificationChannels = listOf(GuardianNotificationChannel.APP),
                notificationStart = "08:00",
                notificationEnd = "22:30",
                allowNightEmergency = true,
                emergencyNote = "",
                authorizationStatus = GuardianAuthorizationStatus.NOT_AUTHORIZED,
                specialCareEnabled = false,
                notifyThreshold = guardianSettings.notifyThreshold
            )
        )
    }
    var guardianNotificationRecords by remember { mutableStateOf(emptyList<GuardianNotificationRecord>()) }
    var guardianRemoteFeedbackRecords by remember { mutableStateOf(emptyList<GuardianFeedbackRecord>()) }
    var careLoopRecords by remember { mutableStateOf(emptyList<CareLoopRecord>()) }
    var showDebugLog by remember { mutableStateOf(false) }
    var wearConnectionStatus by remember { mutableStateOf("未连接") }
    var pendingPassiveAlert by remember { mutableStateOf<com.neurogarden.app.passive.PendingPassiveAlert?>(null) }
    var localOpenChatRequest by remember { mutableStateOf(0) }
    val realtime by mainViewModel.uiState.collectAsStateWithLifecycle()
    val todayRiskEvents by mainViewModel.todayRiskEvents.collectAsState(initial = emptyList())
    val recentRiskEvents by mainViewModel.recentRiskEvents.collectAsState(initial = emptyList())
    val todaySummary by mainViewModel.todaySummary.collectAsState()
    val todayChartData by mainViewModel.todayChartData.collectAsState()
    val sevenDaySummaries by mainViewModel.sevenDaySummaries.collectAsState()
    val careMode by mainViewModel.careMode.collectAsState()
    val careModePolicy by mainViewModel.careModePolicy.collectAsState()
    val agentAuditLogs by mainViewModel.agentAuditLogs.collectAsState(initial = emptyList())
    val feedbackRecords by mainViewModel.feedbackRecords.collectAsState(initial = emptyList())
    val emotionEvaluations by mainViewModel.emotionEvaluations.collectAsState(initial = emptyList())
    val thresholdProfiles by mainViewModel.thresholdProfiles.collectAsState(initial = emptyList())
    val sessions by therapyViewModel.sessions.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val wearCommandSender = remember(context) { WearCommandSender(context.applicationContext) }

    LaunchedEffect(guardianSettings) {
        WatchSignalStore.saveSettings(
            context.applicationContext,
            WatchSignalSettings(
                simulationEnabled = guardianSettings.watchSimulationEnabled,
                simulatedHeartRate = guardianSettings.simulatedHeartRate,
                simulatedBreathRate = guardianSettings.simulatedBreathRate,
                simulatedMotionLevel = guardianSettings.simulatedMotionLevel
            )
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            pendingPassiveAlert = PendingPassiveAlertStore.read(context.applicationContext)
            kotlinx.coroutines.delay(1000L)
        }
    }

    val simulateGuardianNotification: (com.neurogarden.app.data.local.RiskEventEntity) -> Unit = { event ->
        val syncedProfile = guardianProfile.copy(notifyThreshold = guardianSettings.notifyThreshold)
        val deviation = SpecialCareService.getSpecialCareDeviationLevel(event, recentRiskEvents, guardianRemoteFeedbackRecords)
        val strategy = GuardianNotificationService.shouldNotifyGuardian(
            event = event,
            contextMode = careMode,
            guardianSettings = syncedProfile,
            notificationHistory = guardianNotificationRecords,
            feedbackHistory = guardianRemoteFeedbackRecords,
            recentEvents = recentRiskEvents
        )
        val record = GuardianNotificationService.createMockNotification(
            event = event,
            settings = syncedProfile,
            strategy = strategy,
            deviationLevel = deviation.deviationLevel
        )
        guardianNotificationRecords = listOf(record) + guardianNotificationRecords
        val currentLoop = careLoopRecords.firstOrNull { it.eventId == event.id }
        val updatedLoop = SpecialCareService.updateCareLoop(
            current = currentLoop,
            eventId = event.id,
            deviation = deviation,
            notificationId = record.notificationId,
            feedback = null
        )
        careLoopRecords = listOf(updatedLoop) + careLoopRecords.filterNot { it.eventId == event.id }
    }

    val submitRemoteGuardianFeedback: (com.neurogarden.app.data.local.RiskEventEntity, GuardianFeedbackAction) -> Unit = { event, action ->
        val now = System.currentTimeMillis()
        val record = GuardianFeedbackRecord(
            feedbackId = "feedback_${now}_${event.id}",
            eventId = event.id,
            guardianName = guardianProfile.guardianName.ifBlank { "监护人" },
            action = action,
            note = "远程反馈模拟：${action.displayName}",
            createdAt = now,
            source = "remote_guardian_mock",
            sensitivityAdjustment = action.sensitivityAdjustment,
            nextReminderAt = if (action == GuardianFeedbackAction.REMIND_LATER) now + 30L * 60L * 1000L else null
        )
        guardianRemoteFeedbackRecords = listOf(record) + guardianRemoteFeedbackRecords
        mainViewModel.submitGuardianFeedback(event.id, action.displayName)
        val deviation = SpecialCareService.getSpecialCareDeviationLevel(event, recentRiskEvents, listOf(record) + guardianRemoteFeedbackRecords)
        val currentLoop = careLoopRecords.firstOrNull { it.eventId == event.id }
        val updatedLoop = SpecialCareService.updateCareLoop(
            current = currentLoop,
            eventId = event.id,
            deviation = deviation,
            notificationId = currentLoop?.notificationId,
            feedback = record
        )
        careLoopRecords = listOf(updatedLoop) + careLoopRecords.filterNot { it.eventId == event.id }
    }

    val sendGuardianSmsDraft: (com.neurogarden.app.data.local.RiskEventEntity) -> Unit = { event ->
        val phone = guardianProfile.phone.trim().ifBlank { guardianSettings.contact.trim() }
        when {
            phone.isBlank() -> {
                Toast.makeText(context, "请先填写监护人手机号", Toast.LENGTH_SHORT).show()
            }

            else -> {
                val message = buildGuardianSmsMessage(event)
                val now = System.currentTimeMillis()
                val draftRecord = GuardianNotificationRecord(
                    notificationId = "sms_draft_${now}_${event.id}",
                    eventId = event.id,
                    guardianName = guardianProfile.guardianName.ifBlank { guardianSettings.name },
                    relationship = guardianProfile.relationship.ifBlank { guardianSettings.relation },
                    channels = listOf(GuardianNotificationChannel.SMS),
                    status = GuardianNotificationStatus.PENDING,
                    reason = "已打开系统短信，等待用户确认发送",
                    riskLevel = event.riskLevel,
                    deviationLevel = null,
                    sentAt = now,
                    cooldownApplied = false,
                    strategyTags = listOf("manual_user_confirmed_sms", "system_sms_app"),
                    messagePreview = message
                )
                guardianNotificationRecords = listOf(draftRecord) + guardianNotificationRecords
                runCatching {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${Uri.encode(phone)}")
                        putExtra("sms_body", message)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    Toast.makeText(context, "未找到可用短信应用", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler(enabled = showDebugLog) {
        showDebugLog = false
    }

    // 首次启动时检查并显示权限弹窗
    var showPermissionDialog by remember { mutableStateOf(false) }

    // 使用 DataStore 或 SharedPreferences 来记录是否首次启动
    val sharedPrefs = remember {
        context.getSharedPreferences("neurogarden_prefs", android.content.Context.MODE_PRIVATE)
    }

    LaunchedEffect(Unit) {
        val isFirstLaunch = sharedPrefs.getBoolean("first_launch", true)
        if (isFirstLaunch) {
            // 首次启动，显示权限弹窗
            showPermissionDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 处理权限结果
        permissions.entries.forEach { (permission, granted) ->
            android.util.Log.d("Permission", "$permission granted: $granted")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showDebugLog) {
            DebugLogScreen(
                logs = agentAuditLogs,
                onBack = { showDebugLog = false }
            )
        } else {
            MainDashboardScreen(
                realtime = realtime,
                sessions = sessions,
                todayRiskEvents = todayRiskEvents,
                recentRiskEvents = recentRiskEvents,
                todaySummary = todaySummary,
                todayChartData = todayChartData,
                sevenDaySummaries = sevenDaySummaries,
                careMode = careMode,
                careModePolicy = careModePolicy,
                wearConnectionStatus = wearConnectionStatus,
                feedbackRecords = feedbackRecords,
                emotionEvaluations = emotionEvaluations,
                thresholdProfiles = thresholdProfiles,
                guardianSettings = guardianSettings,
                guardianProfile = guardianProfile,
                guardianNotificationRecords = guardianNotificationRecords,
                guardianFeedbackRecords = guardianRemoteFeedbackRecords,
                careLoopRecords = careLoopRecords,
                onGuardianSettingsChange = { guardianSettings = it },
                onGuardianProfileChange = { guardianProfile = it },
                onStartPassiveGuardian = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        (context as? ComponentActivity)?.requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            2001
                        )
                    }
                    guardianSettings = guardianSettings.copy(passiveGuardianRunning = true)
                    PassiveGuardianService.start(context)
                },
                onStopPassiveGuardian = {
                    guardianSettings = guardianSettings.copy(passiveGuardianRunning = false)
                    PassiveGuardianService.stop(context)
                },
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                onOpenOverlaySettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                },
                onConnectWear = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (context as? ComponentActivity)?.requestPermissions(
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                            2002
                        )
                    }
                    wearConnectionStatus = "正在查找手表..."
                    coroutineScope.launch {
                        val result = runCatching {
                            wearCommandSender.connectAndStartMonitoring()
                        }.getOrElse {
                            wearConnectionStatus = "连接失败，请确认蓝牙权限和系统配对状态"
                            return@launch
                        }
                        wearConnectionStatus = result.message
                        if (result.connected) {
                            mainViewModel.enterScenario()
                        }
                    }
                },
                onSendWearBreathPattern = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (context as? ComponentActivity)?.requestPermissions(
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                            2002
                        )
                    }
                    coroutineScope.launch {
                        val result = wearCommandSender.sendBreathPattern(
                            inhaleSeconds = 4,
                            exhaleSeconds = 6,
                            pattern = "slow_breath"
                        )
                        wearConnectionStatus = result.message
                    }
                },
                onContinueMock = mainViewModel::nextScenario,
                onFeedback = mainViewModel::submitFeedback,
                onBeginSupportConversation = mainViewModel::beginSupportConversation,
                onSendSupportReply = mainViewModel::sendSupportReply,
                onEventFeedback = mainViewModel::submitGuardianFeedback,
                onSimulateGuardianNotification = simulateGuardianNotification,
                onSendGuardianSms = sendGuardianSmsDraft,
                onRemoteGuardianFeedback = submitRemoteGuardianFeedback,
                observeRiskEventById = mainViewModel::observeRiskEvent,
                onClearHabitMemory = mainViewModel::clearHabitMemory,
                onSeedDemoMode = mainViewModel::seedDemoMode,
                onCareModeChange = mainViewModel::setCareMode,
                onDismissIntegrationDemoAlert = mainViewModel::dismissIntegrationDemoAlert,
                onDebugLog = { showDebugLog = true },
                onShowDeveloperOverlayAlert = {
                    val title = "开发者测试悬浮窗"
                    val message = "这是一条只触发系统悬浮窗的开发者测试提醒，用于验证后台覆盖层和“和我聊聊”跳转。"
                    if (PassiveOverlayAlert.canShow(context)) {
                        Toast.makeText(context, "已安排悬浮窗，约 1 分钟后显示", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(60_000L)
                            PassiveOverlayAlert.show(context.applicationContext, title, message)
                        }
                    } else {
                        Toast.makeText(context, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                    }
                },
                onShowDeveloperInAppAlert = {
                    val now = System.currentTimeMillis()
                    PendingPassiveAlertStore.save(
                        context = context.applicationContext,
                        title = "开发者测试关怀弹窗",
                        message = "这是一条只触发 App 内弹窗的开发者测试提醒，用于验证进入陪伴按钮和待处理提醒链路。",
                        now = now
                    )
                    pendingPassiveAlert = PendingPassiveAlertStore.read(context.applicationContext)
                },
                openChatRequest = externalOpenChatRequest + localOpenChatRequest,
                openMindfulnessRequest = externalOpenMindfulnessRequest
            )
        }
    }

    // 权限申请弹窗
    if (showPermissionDialog) {
        PermissionRequestDialog(
            onDismiss = {
                // 用户选择稍后再说，记录状态不再显示
                sharedPrefs.edit().putBoolean("first_launch", false).apply()
                showPermissionDialog = false
            },
            onRequestPermissions = {
                // 申请系统权限
                val permissions = PermissionChecker.getRequiredPermissions(context)
                if (permissions.isNotEmpty()) {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
                // 记录状态不再显示
                sharedPrefs.edit().putBoolean("first_launch", false).apply()
                showPermissionDialog = false
            }
        )
    }

    pendingPassiveAlert?.let { alert ->
        NeuroAlertDialog(
            title = alert.title,
            confirmText = "进入陪伴",
            dismissText = "知道了",
            onDismiss = {
                PendingPassiveAlertStore.consume(context.applicationContext, alert.id)
                pendingPassiveAlert = null
            },
            onConfirm = {
                PendingPassiveAlertStore.consume(context.applicationContext, alert.id)
                pendingPassiveAlert = null
                localOpenChatRequest += 1
            },
        ) {
            Text(alert.message)
        }
    }

}

private fun buildGuardianSmsMessage(event: com.neurogarden.app.data.local.RiskEventEntity): String {
    val reasons = event.mainReasons
        .split("|", ";", "、")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("、")
        .ifBlank { "状态节律与平时不太一样" }
    val emotion = event.agentAnalysis.extractAgentValue("emotion")
        .ifBlank { event.agentAnalysis.extractAgentValue("state") }
        .toGuardianEmotionLabel(event.riskLevel)
    val opening = when (event.riskLevel) {
        "urgent_support" -> "刚刚观察到 TA 的状态波动比较明显，建议尽快温和确认一下。"
        "guardian_check" -> "刚刚观察到 TA 的状态节律有些偏离日常，建议你方便时轻轻确认一下。"
        "support" -> "刚刚观察到 TA 可能需要一点陪伴，建议你方便时发一句问候。"
        "observe" -> "刚刚观察到 TA 的状态有轻微波动，可以先留意一下。"
        else -> "刚刚观察到 TA 的状态节律和平时不太一样。"
    }
    val suggestion = when (event.riskLevel) {
        "urgent_support" -> "可以先问：“我在，方便回我一句吗？”"
        "guardian_check", "support" -> "可以先问：“我在，今天还好吗？”"
        else -> "可以先不打扰，稍后再看一次状态。"
    }
    return "【NeuroGarden】$opening 当前推测状态：$emotion。原因：$reasons。$suggestion 本提醒不含聊天原文，仅用于守护确认，不代表医疗诊断。"
        .take(180)
}

private fun String.extractAgentValue(key: String): String {
    val pattern = Regex("""(?:^|;)$key=([^;]+)""")
    return pattern.find(this)?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

private fun String.toGuardianEmotionLabel(riskLevel: String): String {
    val value = trim().lowercase()
    return when {
        value.isBlank() || value == "unknown" -> when (riskLevel) {
            "urgent_support" -> "高压或明显不适"
            "guardian_check" -> "需要确认的状态波动"
            "support" -> "可能有些吃力"
            "observe" -> "轻微波动"
            else -> "暂未明确"
        }
        value.contains("calm") || value.contains("stable") || value.contains("平静") -> "相对平静"
        value.contains("tired") || value.contains("fatigue") || value.contains("疲") -> "疲惫"
        value.contains("stress") || value.contains("pressure") || value.contains("高压") || value.contains("紧张") -> "紧张或压力偏高"
        value.contains("anxious") || value.contains("anxiety") || value.contains("焦虑") -> "焦虑或不安"
        value.contains("low") || value.contains("sad") || value.contains("down") || value.contains("低落") -> "低落"
        value.contains("irritable") || value.contains("烦") || value.contains("躁") -> "烦躁"
        value.contains("lonely") || value.contains("empty") || value.contains("孤独") || value.contains("空落") -> "孤独或空落"
        value.contains("positive") || value.contains("active") || value.contains("积极") -> "积极"
        value.contains("neutral") || value.contains("中性") -> "中性偏平稳"
        else -> this.trim().take(18)
    }
}
