package com.neurogarden.app.algorithm

import com.neurogarden.shared.model.EmotionState
import com.neurogarden.shared.model.SensorPacket
import com.neurogarden.shared.model.StressResult
import kotlin.math.max
import kotlin.math.min

object StressCalculator {
    fun calculate(packet: SensorPacket): StressResult {
        val heartStress = ((packet.heartRate - packet.baselineHeartRate) / 35f).clamp()
        val breathStress = ((packet.breathRate - 12) / 12f).clamp()
        val waveStress = (packet.heartRateWave / 12f).clamp()
        val score = (
            heartStress * 0.40f +
                breathStress * 0.30f +
                waveStress * 0.20f +
                packet.interactionStress * 0.10f
            ).clamp()
        val confidence = if (packet.motionLevel > 0.6f) 0.5f else 1f
        val state = EmotionStateMapper.map(score)
        val warning = if (packet.motionLevel > 0.6f) "检测到明显运动，当前压力判断置信度降低" else null
        return StressResult(score, confidence, state, state.terrainName, warning)
    }

    private fun Float.clamp(): Float = max(0f, min(1f, this))
}
