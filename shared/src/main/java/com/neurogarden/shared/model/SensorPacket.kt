package com.neurogarden.shared.model

data class SensorPacket(
    val heartRate: Int,
    val baselineHeartRate: Int = 72,
    val breathRate: Int,
    val heartRateWave: Float,
    val motionLevel: Float,
    val interactionStress: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
