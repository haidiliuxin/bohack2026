package com.neurogarden.app

import android.Manifest
import android.content.Intent
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
import com.neurogarden.app.passive.WatchSignalSettings
import com.neurogarden.app.passive.WatchSignalStore
import com.neurogarden.app.ui.screen.DebugLogScreen
import com.neurogarden.app.ui.screen.GuardianSettings
import com.neurogarden.app.ui.screen.MainDashboardScreen
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
            (application as NeuroGardenApp).riskEventRepository,
            (application as NeuroGardenApp).repository,
            (application as NeuroGardenApp).weatherRepository,
            (application as NeuroGardenApp).careModeStore,
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

@Composable
private fun NeuroGardenRoot(
    mainViewModel: MainViewModel,
    therapyViewModel: TherapyViewModel
) {
    var guardianSettings by remember { mutableStateOf(GuardianSettings()) }
    var showDebugLog by remember { mutableStateOf(false) }
    var wearConnectionStatus by remember { mutableStateOf("未连接") }
    val realtime by mainViewModel.uiState.collectAsStateWithLifecycle()
    val todayRiskEvents by mainViewModel.todayRiskEvents.collectAsState(initial = emptyList())
    val recentRiskEvents by mainViewModel.recentRiskEvents.collectAsState(initial = emptyList())
    val todaySummary by mainViewModel.todaySummary.collectAsState()
    val todayChartData by mainViewModel.todayChartData.collectAsState()
    val sevenDaySummaries by mainViewModel.sevenDaySummaries.collectAsState()
    val careMode by mainViewModel.careMode.collectAsState()
    val careModePolicy by mainViewModel.careModePolicy.collectAsState()
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

    BackHandler(enabled = showDebugLog) {
        showDebugLog = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showDebugLog) {
            DebugLogScreen(onBack = { showDebugLog = false })
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
                guardianSettings = guardianSettings,
                onGuardianSettingsChange = { guardianSettings = it },
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
                onContinueMock = mainViewModel::nextScenario,
                onFeedback = mainViewModel::submitFeedback,
                onBeginSupportConversation = mainViewModel::beginSupportConversation,
                onSendSupportReply = mainViewModel::sendSupportReply,
                onEventFeedback = mainViewModel::submitGuardianFeedback,
                observeRiskEventById = mainViewModel::observeRiskEvent,
                onClearHabitMemory = mainViewModel::clearHabitMemory,
                onSeedDemoMode = mainViewModel::seedDemoMode,
                onCareModeChange = mainViewModel::setCareMode,
                onDebugLog = { showDebugLog = true }
            )
        }
    }
}
