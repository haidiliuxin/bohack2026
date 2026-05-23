package com.neurogarden.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.neurogarden.wear.data.PhoneDataSender
import com.neurogarden.wear.health.HeartRateClient
import com.neurogarden.wear.health.MockHeartRateClient
import com.neurogarden.wear.health.RealHeartRateClient
import com.neurogarden.wear.ui.WearHomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var heartRateClient: HeartRateClient
    private val mockHeartRateClient = MockHeartRateClient()
    private lateinit var phoneDataSender: PhoneDataSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phoneDataSender = PhoneDataSender(this)
        heartRateClient = createHeartRateClient()
        setContent {
            var activeHeartRateClient by remember { mutableStateOf(heartRateClient) }
            var monitoring by remember { mutableStateOf(false) }
            var sendStatus by remember { mutableStateOf("等待开始") }
            val heartRate by activeHeartRateClient.heartRateFlow.collectAsState(initial = null)
            val status by activeHeartRateClient.statusFlow.collectAsState(initial = "等待启动")

            LaunchedEffect(activeHeartRateClient) { activeHeartRateClient.start() }
            LaunchedEffect(monitoring, heartRate) {
                val currentHeartRate = heartRate
                if (monitoring && currentHeartRate != null) {
                    val sent = phoneDataSender.sendPassivePayload(
                        heartRate = currentHeartRate,
                        breathRate = estimatedBreathRate(currentHeartRate),
                        motionLevel = 0.12f
                    )
                    sendStatus = if (sent) "正在发送到手机" else "发送失败，请确认手机已连接"
                }
            }

            WearHomeScreen(
                heartRate = heartRate,
                status = "$status / $sendStatus",
                onStart = {
                    monitoring = true
                    lifecycleScope.launch {
                        if (activeHeartRateClient is MockHeartRateClient && hasBodySensorPermission()) {
                            activeHeartRateClient.stop()
                            activeHeartRateClient = RealHeartRateClient(this@MainActivity)
                            heartRateClient = activeHeartRateClient
                            sendStatus = "已获得传感器权限，切换到真实心率"
                            return@launch
                        }
                        val currentHeartRate = heartRate ?: 86
                        val sent = phoneDataSender.sendPassivePayload(
                            heartRate = currentHeartRate,
                            breathRate = estimatedBreathRate(currentHeartRate),
                            motionLevel = 0.12f
                        )
                        sendStatus = if (sent) "已连接手机，正在发送" else "未找到手机，请先配对"
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch { heartRateClient.stop() }
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

    private fun estimatedBreathRate(heartRate: Int): Int =
        12 + ((heartRate - 72).coerceAtLeast(0) / 5)

    companion object {
        private const val BODY_SENSOR_REQUEST = 4101
    }
}
