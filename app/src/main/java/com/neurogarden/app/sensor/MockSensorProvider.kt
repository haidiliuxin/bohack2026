package com.neurogarden.app.sensor

import com.neurogarden.shared.model.SensorPacket

enum class MockScenario(val title: String, val packet: SensorPacket, val bodyState: String) {
    CALM("平静", SensorPacket(72, breathRate = 10, heartRateWave = 2f, motionLevel = 0.1f), "静止"),
    TENSE("紧张", SensorPacket(88, breathRate = 15, heartRateWave = 5f, motionLevel = 0.2f), "轻微活动"),
    ANXIOUS("焦虑", SensorPacket(102, breathRate = 21, heartRateWave = 8f, motionLevel = 0.1f), "静止"),
    MOVING("运动干扰", SensorPacket(112, breathRate = 22, heartRateWave = 7f, motionLevel = 0.8f), "运动中");

    fun next(): MockScenario {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
