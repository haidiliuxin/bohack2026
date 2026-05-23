package com.neurogarden.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
            NeuroGardenTheme {
                NeuroGardenRoot(mainViewModel, therapyViewModel, openChatRequest)
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
        if (intent?.action == ACTION_OPEN_CHAT || intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true) {
            openChatRequests.value = openChatRequests.value + 1
        }
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.neurogarden.app.OPEN_CHAT"
        const val EXTRA_OPEN_CHAT = "open_chat"
    }
}

@Composable
private fun NeuroGardenRoot(
    mainViewModel: MainViewModel,
    therapyViewModel: TherapyViewModel,
    externalOpenChatRequest: Int
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
                onRemoteGuardianFeedback = submitRemoteGuardianFeedback,
                observeRiskEventById = mainViewModel::observeRiskEvent,
                onClearHabitMemory = mainViewModel::clearHabitMemory,
                onSeedDemoMode = mainViewModel::seedDemoMode,
                onCareModeChange = mainViewModel::setCareMode,
                onDismissIntegrationDemoAlert = mainViewModel::dismissIntegrationDemoAlert,
                onDebugLog = { showDebugLog = true },
                openChatRequest = externalOpenChatRequest + localOpenChatRequest
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
