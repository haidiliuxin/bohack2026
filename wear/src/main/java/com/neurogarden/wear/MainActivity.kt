package com.neurogarden.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.neurogarden.shared.wear.WearPaths
import com.neurogarden.wear.data.PhoneCommandReceiver
import com.neurogarden.wear.data.PhoneDataSender
import com.neurogarden.wear.haptic.BreathHapticController
import com.neurogarden.wear.health.HeartRateClient
import com.neurogarden.wear.health.MockHeartRateClient
import com.neurogarden.wear.health.RealHeartRateClient
import com.neurogarden.wear.ui.WatchDataQuality
import com.neurogarden.wear.ui.WatchDataSource
import com.neurogarden.wear.ui.WatchRiskState
import com.neurogarden.wear.ui.WatchVitalUiState
import com.neurogarden.wear.ui.WearBreathingScreen
import com.neurogarden.wear.ui.WearHomeScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {
    private lateinit var heartRateClient: HeartRateClient
    private val mockHeartRateClient = MockHeartRateClient()
    private lateinit var phoneDataSender: PhoneDataSender
    private lateinit var hapticController: BreathHapticController
    private val commandEvents = MutableSharedFlow<WearCommand>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phoneDataSender = PhoneDataSender(this)
        hapticController = BreathHapticController(this)
        heartRateClient = createHeartRateClient()

        setContent {
            var activeHeartRateClient by remember { mutableStateOf(heartRateClient) }
            var showingBreathing by remember { mutableStateOf(false) }
            var manualRefreshTick by remember { mutableIntStateOf(0) }
            var inhaleSeconds by remember { mutableIntStateOf(4) }
            var exhaleSeconds by remember { mutableIntStateOf(6) }
            var vitalState by remember { mutableStateOf(WatchVitalUiState()) }
            val heartRate by activeHeartRateClient.heartRateFlow.collectAsState(initial = 86)
            val status by activeHeartRateClient.statusFlow.collectAsState(initial = "Mock 心率")

            LaunchedEffect(activeHeartRateClient) {
                activeHeartRateClient.start()
            }
            LaunchedEffect(heartRate, manualRefreshTick, status, activeHeartRateClient) {
                vitalState = buildVitalState(
                    heartRate = heartRate,
                    tick = manualRefreshTick,
                    last = vitalState,
                    realSource = activeHeartRateClient !is MockHeartRateClient,
                    status = status
                )
            }
            LaunchedEffect(Unit) {
                commandEvents.collect { command ->
                    when (command) {
                        WearCommand.StartMonitoring -> {
                            manualRefreshTick += 1
                            vitalState = vitalState.copy(lastCommandText = "手机请求开始监测")
                        }
                        is WearCommand.BreathPattern -> {
                            inhaleSeconds = command.inhaleSeconds
                            exhaleSeconds = command.exhaleSeconds
                            vitalState = vitalState.copy(
                                lastCommandText = "收到手机呼吸节奏 ${command.inhaleSeconds}s/${command.exhaleSeconds}s"
                            )
                            showingBreathing = true
                        }
                    }
                }
            }

            BackHandler(enabled = showingBreathing) {
                showingBreathing = false
            }

            if (showingBreathing) {
                DisposableEffect(inhaleSeconds, exhaleSeconds) {
                    hapticController.startPattern(inhaleSeconds, exhaleSeconds)
                    onDispose { hapticController.stop() }
                }
                WearBreathingScreen(
                    inhaleSeconds = inhaleSeconds,
                    exhaleSeconds = exhaleSeconds,
                    onBack = { showingBreathing = false }
                )
            } else {
                WearHomeScreen(
                    state = vitalState,
                    onRefresh = {
                        lifecycleScope.launch {
                            if (activeHeartRateClient is MockHeartRateClient && hasBodySensorPermission()) {
                                activeHeartRateClient.stop()
                                activeHeartRateClient = RealHeartRateClient(this@MainActivity)
                                heartRateClient = activeHeartRateClient
                            } else {
                                manualRefreshTick += 1
                            }
                        }
                    },
                    onSyncPhone = {
                        lifecycleScope.launch {
                            val sent = phoneDataSender.sendPassivePayload(
                                heartRate = vitalState.heartRate,
                                breathRate = vitalState.breathRate,
                                motionLevel = vitalState.motionLevel
                            )
                            vitalState = vitalState.copy(
                                phoneConnected = sent,
                                lastSyncTime = if (sent) System.currentTimeMillis() else vitalState.lastSyncTime,
                                dataQuality = if (sent) WatchDataQuality.HIGH else vitalState.dataQuality,
                                lastCommandText = if (sent) "已同步手机" else "同步失败，请检查配对"
                            )
                        }
                    },
                    onBreathing = { showingBreathing = true }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        Wearable.getMessageClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch { heartRateClient.stop() }
        hapticController.stop()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.START_MONITORING -> commandEvents.tryEmit(WearCommand.StartMonitoring)
            WearPaths.BREATH_PATTERN -> {
                val command = PhoneCommandReceiver.parseBreathPattern(event.data) ?: return
                commandEvents.tryEmit(
                    WearCommand.BreathPattern(
                        inhaleSeconds = command.inhaleSeconds,
                        exhaleSeconds = command.exhaleSeconds
                    )
                )
            }
        }
    }

    private fun createHeartRateClient(): HeartRateClient =
        if (hasBodySensorPermission()) {
            RealHeartRateClient(this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), BODY_SENSOR_REQUEST)
            mockHeartRateClient
        }

    private fun hasBodySensorPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildVitalState(
        heartRate: Int,
        tick: Int,
        last: WatchVitalUiState,
        realSource: Boolean,
        status: String
    ): WatchVitalUiState {
        val safeHeartRate = if (heartRate > 0) heartRate else 86
        val breathRate = estimatedBreathRate(safeHeartRate)
        val motionLevel = listOf(0.08f, 0.16f, 0.32f, 0.52f)[tick % 4]
        val riskState = when {
            motionLevel >= 0.60f -> WatchRiskState.STABLE
            safeHeartRate >= 104 || breathRate >= 21 -> WatchRiskState.ALERT
            safeHeartRate >= 92 || breathRate >= 18 -> WatchRiskState.OBSERVE
            else -> WatchRiskState.STABLE
        }
        return last.copy(
            heartRate = safeHeartRate,
            breathRate = breathRate,
            motionLevel = motionLevel,
            riskState = riskState,
            statusLabel = statusLabelFor(riskState, safeHeartRate, breathRate, motionLevel, tick),
            confidence = confidenceFor(riskState, motionLevel, realSource),
            dataQuality = dataQualityFor(tick, last.phoneConnected, realSource),
            dataSource = if (realSource) WatchDataSource.REAL else WatchDataSource.MOCK,
            observedClues = reasonsFor(riskState, safeHeartRate, breathRate, motionLevel),
            counterEvidence = counterEvidenceFor(motionLevel, tick, status),
            uncertainty = uncertaintyFor(riskState, realSource)
        )
    }

    private fun reasonsFor(
        state: WatchRiskState,
        heartRate: Int,
        breathRate: Int,
        motionLevel: Float
    ): List<String> =
        when (state) {
            WatchRiskState.STABLE -> listOf("节律平稳", "运动干扰低")
            WatchRiskState.OBSERVE -> buildList {
                if (heartRate >= 92) add("心率略高")
                if (breathRate >= 18) add("呼吸偏快")
                if (motionLevel >= 0.30f) add("活动中等")
            }
            WatchRiskState.ALERT -> buildList {
                if (heartRate >= 104) add("心率偏高")
                if (breathRate >= 21) add("呼吸较快")
                add("建议同步手机")
            }
        }.take(3)

    private fun estimatedBreathRate(heartRate: Int): Int =
        12 + ((heartRate - 72).coerceAtLeast(0) / 5)

    private fun statusLabelFor(
        state: WatchRiskState,
        heartRate: Int,
        breathRate: Int,
        motionLevel: Float,
        tick: Int
    ): String =
        when (state) {
            WatchRiskState.STABLE -> if (motionLevel < 0.18f) "稳定" else "专注"
            WatchRiskState.OBSERVE -> if (tick % 2 == 0) "轻度波动" else "疲惫"
            WatchRiskState.ALERT -> if (heartRate >= 108 || breathRate >= 22) "节律偏高" else "紧张"
        }

    private fun confidenceFor(state: WatchRiskState, motionLevel: Float, realSource: Boolean): Float {
        val sourceBonus = if (realSource) 0.08f else 0f
        val base = when (state) {
            WatchRiskState.STABLE -> 0.68f
            WatchRiskState.OBSERVE -> 0.64f
            WatchRiskState.ALERT -> 0.70f
        }
        val motionPenalty = if (motionLevel > 0.45f) 0.16f else 0f
        return (base + sourceBonus - motionPenalty).coerceIn(0f, 1f)
    }

    private fun dataQualityFor(tick: Int, phoneConnected: Boolean, realSource: Boolean): WatchDataQuality =
        when {
            realSource || phoneConnected -> WatchDataQuality.HIGH
            tick % 5 == 0 -> WatchDataQuality.LOW
            else -> WatchDataQuality.MEDIUM
        }

    private fun counterEvidenceFor(motionLevel: Float, tick: Int, status: String): List<String> = buildList {
        if (motionLevel >= 0.30f) add("可能受活动影响")
        if (tick % 5 == 0) add("缺少连续样本")
        if (status.contains("Mock", ignoreCase = true)) add("当前为模拟心率")
        add("未含 HRV/睡眠")
    }.take(2)

    private fun uncertaintyFor(state: WatchRiskState, realSource: Boolean): String =
        when {
            !realSource -> "模拟数据仅供演示"
            state == WatchRiskState.STABLE -> "仍需连续样本确认"
            state == WatchRiskState.OBSERVE -> "只提示状态偏离"
            else -> "不是医学诊断"
        }

    private sealed interface WearCommand {
        data object StartMonitoring : WearCommand
        data class BreathPattern(val inhaleSeconds: Int, val exhaleSeconds: Int) : WearCommand
    }

    companion object {
        private const val BODY_SENSOR_REQUEST = 4101
    }
}
