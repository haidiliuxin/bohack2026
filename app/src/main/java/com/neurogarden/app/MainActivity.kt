package com.neurogarden.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.neurogarden.app.passive.WatchSignalSettings
import com.neurogarden.app.passive.WatchSignalStore
import com.neurogarden.app.ui.screen.CareSupportScreen
import com.neurogarden.app.ui.screen.DebugLogScreen
import com.neurogarden.app.ui.screen.DeviceScreen
import com.neurogarden.app.ui.screen.GuardianScreen
import com.neurogarden.app.ui.screen.GuardianSettings
import com.neurogarden.app.ui.screen.HistoryScreen
import com.neurogarden.app.ui.screen.HomeScreen
import com.neurogarden.app.ui.screen.PrivacyGuideScreen
import com.neurogarden.app.ui.screen.RealtimeScreen
import com.neurogarden.app.ui.screen.ResultScreen
import com.neurogarden.app.ui.screen.TherapyScreen
import com.neurogarden.app.ui.theme.NeuroGardenTheme
import com.neurogarden.app.viewmodel.MainViewModel
import com.neurogarden.app.viewmodel.TherapyViewModel
import com.neurogarden.app.wear.WearCommandSender
import com.neurogarden.shared.util.JsonUtil
import com.neurogarden.shared.wear.WearPaths
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val mainViewModel by viewModels<MainViewModel> {
        MainViewModel.Factory(
            (application as NeuroGardenApp).habitRepository,
            (application as NeuroGardenApp).guardianAgentApi
        )
    }
    private val therapyViewModel by viewModels<TherapyViewModel> {
        TherapyViewModel.Factory((application as NeuroGardenApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroGardenTheme {
                NeuroGardenRoot(mainViewModel, therapyViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { Wearable.getDataClient(this).addListener(this) }
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
}

private enum class Screen { Home, Device, Realtime, Therapy, Result, History, Guardian, CareSupport, PrivacyGuide, DebugLog }

@Composable
private fun NeuroGardenRoot(
    mainViewModel: MainViewModel,
    therapyViewModel: TherapyViewModel
) {
    var screen by remember { mutableStateOf(Screen.Home) }
    var guardianSettings by remember { mutableStateOf(GuardianSettings()) }
    var notificationSent by remember { mutableStateOf(false) }
    var wearConnectionStatus by remember { mutableStateOf("未连接") }
    var clearMemoryMessage by remember { mutableStateOf<String?>(null) }
    val realtime by mainViewModel.uiState.collectAsStateWithLifecycle()
    val therapy by therapyViewModel.uiState.collectAsStateWithLifecycle()
    val sessions by therapyViewModel.sessions.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val wearCommandSender = remember(context) { WearCommandSender(context.applicationContext) }
    androidx.compose.runtime.LaunchedEffect(guardianSettings) {
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
    val goHome = { screen = Screen.Home }
    val goBack = {
        screen = when (screen) {
            Screen.Home -> Screen.Home
            Screen.Device -> Screen.Home
            Screen.Realtime -> Screen.Home
            Screen.Therapy -> Screen.Realtime
            Screen.Result -> Screen.Home
            Screen.History -> Screen.Home
            Screen.Guardian -> Screen.Home
            Screen.CareSupport -> Screen.Realtime
            Screen.PrivacyGuide -> Screen.Guardian
            Screen.DebugLog -> Screen.Home
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        goBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (screen) {
            Screen.Home -> HomeScreen(
                onStartCheck = { screen = Screen.Device },
                onChat = {
                    mainViewModel.beginSupportConversation()
                    notificationSent = false
                    screen = Screen.CareSupport
                },
                onDebugLog = { screen = Screen.DebugLog },
                onMock = {
                    mainViewModel.enterScenario()
                    screen = Screen.Realtime
                },
                onHistory = { screen = Screen.History },
                onGuardian = { screen = Screen.Guardian }
            )

            Screen.Device -> DeviceScreen(
                onBack = goBack,
                connectionStatus = wearConnectionStatus,
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
                            wearConnectionStatus = "连接失败：请确认蓝牙权限已允许，并先在系统蓝牙中完成手表配对。"
                            return@launch
                        }
                        wearConnectionStatus = result.message
                        if (result.connected) {
                            mainViewModel.enterScenario()
                            screen = Screen.Realtime
                        }
                    }
                },
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                onContinueMock = {
                    mainViewModel.enterScenario()
                    screen = Screen.Realtime
                }
            )

            Screen.Realtime -> RealtimeScreen(
                state = realtime,
                onStartTherapy = {
                    therapyViewModel.startFrom(realtime.packet)
                    screen = Screen.Therapy
                },
                onNextScenario = mainViewModel::nextScenario,
                onOpenCare = {
                    notificationSent = false
                    mainViewModel.beginSupportConversation()
                    screen = Screen.CareSupport
                },
                onBack = goBack
            )

            Screen.Therapy -> TherapyScreen(
                state = therapy,
                onStart = therapyViewModel::resume,
                onPause = therapyViewModel::pause,
                onFinish = {
                    therapyViewModel.finish()
                    screen = Screen.Result
                },
                onBack = goBack
            )

            Screen.Result -> ResultScreen(
                state = therapy,
                onSave = therapyViewModel::saveCurrentSession,
                onAgain = {
                    therapyViewModel.startFrom(realtime.packet)
                    screen = Screen.Therapy
                },
                onHistory = { screen = Screen.History },
                onHome = goHome
            )

            Screen.History -> HistoryScreen(sessions, realtime = realtime, onBack = goBack)

            Screen.Guardian -> GuardianScreen(
                settings = guardianSettings,
                onSettingsChange = {
                    guardianSettings = it
                    clearMemoryMessage = null
                },
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
                onCallGuardian = {
                    openDialer(context, guardianSettings.contact)
                },
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onClearHabitMemory = {
                    mainViewModel.clearHabitMemory()
                    clearMemoryMessage = "已清除本地习惯记忆，系统会重新学习你的日常节奏。"
                },
                clearMemoryMessage = clearMemoryMessage,
                onPrivacyGuide = { screen = Screen.PrivacyGuide },
                onBack = goBack
            )

            Screen.PrivacyGuide -> PrivacyGuideScreen(onBack = goBack)

            Screen.DebugLog -> DebugLogScreen(onBack = goBack)

            Screen.CareSupport -> CareSupportScreen(
                risk = realtime.personalizedRisk,
                supportMessages = realtime.supportMessages,
                guardianSettings = guardianSettings,
                notificationSent = notificationSent,
                onSendMessage = mainViewModel::sendSupportReply,
                onEmotionLabel = mainViewModel::submitEmotionLabel,
                onSafe = { mainViewModel.submitFeedback("我现在安全") },
                onNeedCompanion = { mainViewModel.submitFeedback("我需要陪伴") },
                onFalseAlarm = { mainViewModel.submitFeedback("误报了") },
                onHelpful = { mainViewModel.submitFeedback("这次提醒有帮助") },
                onNotifyGuardian = {
                    notificationSent = true
                    mainViewModel.submitFeedback("我需要陪伴")
                    openDialer(context, guardianSettings.contact)
                },
                onStartBreathing = {
                    therapyViewModel.startFrom(realtime.packet)
                    screen = Screen.Therapy
                },
                onBack = goBack
            )
        }
    }
}

private fun openDialer(context: Context, phoneNumber: String) {
    val trimmed = phoneNumber.trim()
    if (trimmed.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(trimmed)}")))
}
