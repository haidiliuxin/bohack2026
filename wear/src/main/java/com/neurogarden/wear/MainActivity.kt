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
import com.neurogarden.wear.health.MotionSource
import com.neurogarden.wear.health.MotionState
import com.neurogarden.wear.health.MotionStateDetector
import com.neurogarden.wear.health.RealHeartRateClient
import com.neurogarden.wear.ui.MeasurementSource
import com.neurogarden.wear.ui.WatchDataQuality
import com.neurogarden.wear.ui.WatchRiskState
import com.neurogarden.wear.ui.WatchVitalUiState
import com.neurogarden.wear.ui.WearBreathingScreen
import com.neurogarden.wear.ui.WearHomeScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {
    private lateinit var heartRateClient: HeartRateClient
    private val mockHeartRateClient = MockHeartRateClient()
    private lateinit var motionStateDetector: MotionStateDetector
    private lateinit var phoneDataSender: PhoneDataSender
    private lateinit var hapticController: BreathHapticController
    private val commandEvents = MutableSharedFlow<WearCommand>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phoneDataSender = PhoneDataSender(this)
        hapticController = BreathHapticController(this)
        motionStateDetector = MotionStateDetector(this)
        heartRateClient = createHeartRateClient()

        setContent {
            var activeHeartRateClient by remember { mutableStateOf(heartRateClient) }
            var showingBreathing by remember { mutableStateOf(false) }
            var manualRefreshTick by remember { mutableIntStateOf(0) }
            var inhaleSeconds by remember { mutableIntStateOf(4) }
            var exhaleSeconds by remember { mutableIntStateOf(6) }
            var vitalState by remember { mutableStateOf(WatchVitalUiState()) }
            val heartRate by activeHeartRateClient.heartRateFlow.collectAsState(initial = 86)
            val heartStatus by activeHeartRateClient.statusFlow.collectAsState(initial = "模拟心率")
            val motionState by motionStateDetector.stateFlow.collectAsState()

            LaunchedEffect(Unit) {
                motionStateDetector.start()
            }
            LaunchedEffect(activeHeartRateClient) {
                activeHeartRateClient.start()
            }
            LaunchedEffect(heartRate, manualRefreshTick, heartStatus, activeHeartRateClient, motionState) {
                vitalState = buildVitalState(
                    heartRate = heartRate,
                    tick = manualRefreshTick,
                    last = vitalState,
                    realHeartClient = activeHeartRateClient !is MockHeartRateClient,
                    heartStatus = heartStatus,
                    motionState = motionState
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
                                vitalState = vitalState.copy(lastCommandText = "已切换真实心率监听")
                            } else {
                                manualRefreshTick += 1
                                vitalState = vitalState.copy(lastCommandText = "已刷新体征数据")
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
        motionStateDetector.stop()
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
        realHeartClient: Boolean,
        heartStatus: String,
        motionState: MotionState
    ): WatchVitalUiState {
        val hasRealHeartSample = realHeartClient && heartRate > 0
        val safeHeartRate = if (heartRate > 0) heartRate else 86
        val breathRate = estimatedBreathRate(safeHeartRate)
        val motionLevel = motionState.motionLevel
        val riskState = when {
            motionLevel >= 0.60f -> WatchRiskState.STABLE
            safeHeartRate >= 104 || breathRate >= 21 -> WatchRiskState.ALERT
            safeHeartRate >= 92 || breathRate >= 18 -> WatchRiskState.OBSERVE
            else -> WatchRiskState.STABLE
        }
        val heartSource = when {
            hasRealHeartSample -> MeasurementSource.REAL
            realHeartClient -> MeasurementSource.UNAVAILABLE
            else -> MeasurementSource.MOCK
        }
        val motionSource = when (motionState.source) {
            MotionSource.REAL -> MeasurementSource.REAL
            MotionSource.MOCK -> MeasurementSource.MOCK
            MotionSource.UNAVAILABLE -> MeasurementSource.UNAVAILABLE
        }

        return last.copy(
            heartRate = safeHeartRate,
            breathRate = breathRate,
            motionLevel = motionLevel,
            motionLabel = motionState.label,
            riskState = riskState,
            statusLabel = statusLabelFor(riskState, safeHeartRate, breathRate, motionLevel, tick),
            confidence = confidenceFor(riskState, motionLevel, heartSource, motionSource),
            dataQuality = dataQualityFor(tick, last.phoneConnected, heartSource, motionSource),
            heartRateSource = heartSource,
            breathRateSource = MeasurementSource.ESTIMATED,
            motionSource = motionSource,
            observedClues = reasonsFor(riskState, safeHeartRate, breathRate, motionLevel),
            counterEvidence = counterEvidenceFor(motionLevel, tick, heartStatus, heartSource, motionState),
            uncertainty = uncertaintyFor(riskState, heartSource, motionSource)
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
                if (breathRate >= 21) add("估算呼吸较快")
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
            WatchRiskState.STABLE -> if (motionLevel < 0.18f) "稳定" else "活动中"
            WatchRiskState.OBSERVE -> if (tick % 2 == 0) "轻度波动" else "需要观察"
            WatchRiskState.ALERT -> if (heartRate >= 108 || breathRate >= 22) "节律偏高" else "守护提醒"
        }

    private fun confidenceFor(
        state: WatchRiskState,
        motionLevel: Float,
        heartSource: MeasurementSource,
        motionSource: MeasurementSource
    ): Float {
        val sourceBonus =
            (if (heartSource == MeasurementSource.REAL) 0.08f else 0f) +
                (if (motionSource == MeasurementSource.REAL) 0.08f else 0f)
        val sourcePenalty =
            (if (heartSource == MeasurementSource.UNAVAILABLE) 0.14f else 0f) +
                (if (motionSource == MeasurementSource.UNAVAILABLE) 0.10f else 0f)
        val base = when (state) {
            WatchRiskState.STABLE -> 0.68f
            WatchRiskState.OBSERVE -> 0.64f
            WatchRiskState.ALERT -> 0.70f
        }
        val motionPenalty = if (motionLevel > 0.45f) 0.16f else 0f
        return (base + sourceBonus - sourcePenalty - motionPenalty).coerceIn(0f, 1f)
    }

    private fun dataQualityFor(
        tick: Int,
        phoneConnected: Boolean,
        heartSource: MeasurementSource,
        motionSource: MeasurementSource
    ): WatchDataQuality =
        when {
            heartSource == MeasurementSource.REAL && motionSource == MeasurementSource.REAL -> WatchDataQuality.HIGH
            phoneConnected -> WatchDataQuality.HIGH
            heartSource == MeasurementSource.UNAVAILABLE || motionSource == MeasurementSource.UNAVAILABLE -> WatchDataQuality.LOW
            tick % 5 == 0 -> WatchDataQuality.LOW
            else -> WatchDataQuality.MEDIUM
        }

    private fun counterEvidenceFor(
        motionLevel: Float,
        tick: Int,
        heartStatus: String,
        heartSource: MeasurementSource,
        motionState: MotionState
    ): List<String> = buildList {
        if (motionLevel >= 0.30f) add("可能受活动影响")
        if (tick % 5 == 0) add("缺少连续样本")
        if (heartSource != MeasurementSource.REAL) add("当前非真实心率")
        if (motionState.source == MotionSource.UNAVAILABLE) add("运动传感器不可用")
        if (heartStatus.contains("Mock", ignoreCase = true) || heartStatus.contains("模拟")) add("当前为模拟心率")
        add("呼吸为手表端估算")
    }.take(3)

    private fun uncertaintyFor(
        state: WatchRiskState,
        heartSource: MeasurementSource,
        motionSource: MeasurementSource
    ): String =
        when {
            heartSource == MeasurementSource.MOCK -> "模拟数据仅供演示"
            heartSource == MeasurementSource.UNAVAILABLE -> "等待真实心率样本"
            motionSource == MeasurementSource.UNAVAILABLE -> "缺少真实运动样本"
            state == WatchRiskState.STABLE -> "仍需连续样本确认"
            state == WatchRiskState.OBSERVE -> "仅提示状态偏离"
            else -> "非医学诊断"
        }

    private sealed interface WearCommand {
        data object StartMonitoring : WearCommand
        data class BreathPattern(val inhaleSeconds: Int, val exhaleSeconds: Int) : WearCommand
    }

    companion object {
        private const val BODY_SENSOR_REQUEST = 4101
    }
}
