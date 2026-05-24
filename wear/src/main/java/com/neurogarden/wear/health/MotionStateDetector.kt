package com.neurogarden.wear.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class MotionState(
    val motionLevel: Float = 0.10f,
    val label: String = "低",
    val source: MotionSource = MotionSource.MOCK,
    val statusText: String = "等待运动传感器"
)

enum class MotionSource {
    REAL,
    MOCK,
    UNAVAILABLE
}

class MotionStateDetector(context: Context) : SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = FloatArray(3)
    private var smoothedLevel = 0.10f

    private val _stateFlow = MutableStateFlow(
        if (accelerometer == null) {
            MotionState(
                motionLevel = 0.10f,
                label = "低",
                source = MotionSource.UNAVAILABLE,
                statusText = "未检测到加速度计"
            )
        } else {
            MotionState(
                motionLevel = 0.10f,
                label = "低",
                source = MotionSource.REAL,
                statusText = "运动传感器待启动"
            )
        }
    )
    val stateFlow: StateFlow<MotionState> = _stateFlow.asStateFlow()

    fun start() {
        val sensor = accelerometer
        if (sensor == null || sensorManager == null) {
            _stateFlow.value = MotionState(
                motionLevel = 0.10f,
                label = "低",
                source = MotionSource.UNAVAILABLE,
                statusText = "运动传感器不可用"
            )
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        _stateFlow.value = _stateFlow.value.copy(
            source = MotionSource.REAL,
            statusText = "正在估算运动强度"
        )
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val alpha = 0.82f
        gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]

        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]
        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        val rawLevel = ((magnitude - STILL_NOISE_G) / ACTIVE_RANGE_G).coerceIn(0f, 1f)
        smoothedLevel = (smoothedLevel * 0.78f + rawLevel * 0.22f).coerceIn(0f, 1f)

        _stateFlow.value = MotionState(
            motionLevel = smoothedLevel,
            label = motionLabel(smoothedLevel),
            source = MotionSource.REAL,
            statusText = "加速度计实时估算"
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun motionLabel(level: Float): String =
        when {
            level < 0.18f -> "低"
            level < 0.42f -> "中"
            else -> "高"
        }

    companion object {
        private const val STILL_NOISE_G = 0.12f
        private const val ACTIVE_RANGE_G = 4.2f
    }
}
