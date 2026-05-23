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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var heartRateClient: HeartRateClient
    private val mockHeartRateClient = MockHeartRateClient()
    private lateinit var phoneDataSender: PhoneDataSender
    private lateinit var hapticController: BreathHapticController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phoneDataSender = PhoneDataSender(this)
        hapticController = BreathHapticController(this)
        heartRateClient = createHeartRateClient()

        setContent {
            var activeHeartRateClient by remember { mutableStateOf(heartRateClient) }
            var showingBreathing by remember { mutableStateOf(false) }
            var manualRefreshTick by remember { mutableStateOf(0) }
            var vitalState by remember { mutableStateOf(WatchVitalUiState()) }
            val heartRate by activeHeartRateClient.heartRateFlow.collectAsState(initial = 86)
            val status by activeHeartRateClient.statusFlow.collectAsState(initial = "Mock")

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

            BackHandler(enabled = showingBreathing) {
                showingBreathing = false
            }

            if (showingBreathing) {
                DisposableEffect(Unit) {
                    hapticController.startPattern(inhaleSeconds = 4, exhaleSeconds = 6)
                    onDispose { hapticController.stop() }
                }
                WearBreathingScreen(
                    inhaleSeconds = 4,
                    exhaleSeconds = 6,
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
                                vitalState = buildVitalState(
                                    heartRate = heartRate,
                                    tick = manualRefreshTick,
                                    last = vitalState,
                                    realSource = activeHeartRateClient !is MockHeartRateClient,
                                    status = status
                                )
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
                                dataQuality = if (sent) WatchDataQuality.HIGH else vitalState.dataQuality
                            )
                        }
                    },
                    onBreathing = { showingBreathing = true }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch { heartRateClient.stop() }
        hapticController.stop()
    }

    private fun createHeartRateClient(): HeartRateClient {
        return if (hasBodySensorPermission()) {
            RealHeartRateClient(this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), BODY_SENSOR_REQUEST)
            mockHeartRateClient
        }
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
            emotionLabel = emotionLabelFor(riskState, safeHeartRate, breathRate, motionLevel, tick),
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

    private fun emotionLabelFor(
        state: WatchRiskState,
        heartRate: Int,
        breathRate: Int,
        motionLevel: Float,
        tick: Int
    ): String =
        when (state) {
            WatchRiskState.STABLE -> if (motionLevel < 0.18f) "稳定" else "专注"
            WatchRiskState.OBSERVE -> if (tick % 2 == 0) "轻度波动" else "疲惫"
            WatchRiskState.ALERT -> if (heartRate >= 108 || breathRate >= 22) "压力偏高" else "紧张"
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

    companion object {
        private const val BODY_SENSOR_REQUEST = 4101
    }
}
