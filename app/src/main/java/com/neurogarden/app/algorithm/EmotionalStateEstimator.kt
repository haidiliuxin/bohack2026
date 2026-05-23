package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class EmotionalStateEstimate(
    val primaryState: String,
    val confidence: Float,
    val arousalScore: Float,
    val valenceScore: Float,
    val fatigueScore: Float,
    val lonelinessScore: Float,
    val stressScore: Float,
    val interferenceReason: String?,
    val explanation: String
) {
    companion object {
        fun learning(): EmotionalStateEstimate =
            EmotionalStateEstimate(
                primaryState = "学习中",
                confidence = 0.4f,
                arousalScore = 0f,
                valenceScore = 0f,
                fatigueScore = 0f,
                lonelinessScore = 0f,
                stressScore = 0f,
                interferenceReason = null,
                explanation = "正在收集个人习惯样本，暂时只做轻量观察。"
            )
    }
}

object EmotionalStateEstimator {
    fun estimate(
        sample: HabitSampleEntity,
        baseline: UserHabitBaselineEntity,
        thresholds: ThresholdProfileEntity
    ): EmotionalStateEstimate {
        val quality = SignalQualityEvaluator.assess(sample, baseline, thresholds)
        if (!quality.baselineReady) {
            return EmotionalStateEstimate(
                primaryState = "学习日常节奏中",
                confidence = quality.qualityScore,
                arousalScore = 0f,
                valenceScore = 0f,
                fatigueScore = 0f,
                lonelinessScore = 0f,
                stressScore = 0f,
                interferenceReason = "baseline_not_ready",
                explanation = "个人基线样本还不够，暂时不做明确情绪判断。"
            )
        }
        if (quality.usableSignalCount < 2) {
            return EmotionalStateEstimate(
                primaryState = "信号不足",
                confidence = quality.qualityScore,
                arousalScore = 0f,
                valenceScore = 0f,
                fatigueScore = 0f,
                lonelinessScore = 0f,
                stressScore = 0f,
                interferenceReason = "insufficient_signals",
                explanation = "当前可靠信号组少于两个，只能继续观察。"
            )
        }
        val heartLift = ((sample.heartRate - baseline.avgRestingHeartRate) / thresholds.heartRateDeltaWarning).positiveClamp()
        val breathLift = ((sample.breathRate - baseline.avgBreathRate) / max(1f, thresholds.breathRateWarning - baseline.avgBreathRate)).positiveClamp()
        val typingDelta = ((sample.typingSpeed - baseline.avgTypingSpeed) / max(1f, baseline.avgTypingSpeed))
        val typingDrop = (-typingDelta).positiveClamp()
        val typingRush = typingDelta.positiveClamp()
        val deleteLift = (sample.deleteRate / thresholds.deleteRateWarning).positiveClamp()
        val pauseLift = (sample.pauseDuration / thresholds.pauseDurationWarning).positiveClamp()
        val motionInterference = sample.motionLevel > 0.6f

        val arousal = (heartLift * 0.38f + breathLift * 0.34f + typingRush * 0.14f + deleteLift * 0.14f).clamp()
        val fatigue = (typingDrop * 0.34f + pauseLift * 0.36f + lowActivityWeight(sample) * 0.30f).clamp()
        val loneliness = (pauseLift * 0.42f + nightWeight(sample.timestamp) * 0.28f + typingDrop * 0.30f).clamp()
        val stress = (heartLift * 0.30f + breathLift * 0.25f + deleteLift * 0.25f + pauseLift * 0.20f).clamp()
        val calmLift = (1f - stress).coerceIn(0f, 1f) * 0.18f
        val valence = (0.62f + calmLift - fatigue * 0.30f - loneliness * 0.24f - stress * 0.28f).coerceIn(-1f, 1f)

        val primary = when {
            motionInterference -> "运动干扰"
            fatigue >= 0.62f && loneliness >= 0.45f -> "低落疲惫"
            arousal >= 0.64f && deleteLift >= 0.65f -> "高压紧张"
            arousal >= 0.58f && pauseLift >= 0.45f -> "焦虑紧绷"
            fatigue >= 0.55f -> "疲惫恢复慢"
            loneliness >= 0.58f -> "可能需要陪伴"
            stress >= 0.45f -> "轻微压力偏离"
            arousal in 0.34f..0.62f && stress < 0.34f && fatigue < 0.46f && typingRush in 0.08f..0.52f -> "积极活跃"
            arousal in 0.20f..0.52f && stress < 0.30f && fatigue < 0.36f && pauseLift < 0.45f -> "平静专注"
            arousal < 0.26f && stress < 0.28f && fatigue < 0.42f -> "轻松平稳"
            fatigue in 0.34f..0.54f && stress < 0.35f && loneliness < 0.48f -> "安静恢复"
            else -> "相对稳定"
        }
        val rawConfidence = when {
            motionInterference -> 0.42f
            baseline.sampleCount < 8 -> 0.48f
            baseline.sampleCount < 20 -> 0.66f
            else -> 0.80f
        }
        val confidence = min(rawConfidence, quality.qualityScore)

        return EmotionalStateEstimate(
            primaryState = primary,
            confidence = confidence,
            arousalScore = arousal,
            valenceScore = valence,
            fatigueScore = fatigue,
            lonelinessScore = loneliness,
            stressScore = stress,
            interferenceReason = if (motionInterference) "检测到明显运动，情绪判断置信度降低。" else null,
            explanation = explanationFor(primary, heartLift, breathLift, deleteLift, pauseLift, typingDelta)
        )
    }

    private fun explanationFor(
        primary: String,
        heartLift: Float,
        breathLift: Float,
        deleteLift: Float,
        pauseLift: Float,
        typingDelta: Float
    ): String {
        val reasons = buildList {
            if (heartLift > 0.45f) add("心率高于个人基线")
            if (breathLift > 0.45f) add("呼吸节奏偏快")
            if (deleteLift > 0.55f) add("删除频率升高")
            if (pauseLift > 0.55f) add("停顿时长增加")
            if (typingDelta < -0.25f) add("输入速度低于日常")
            if (typingDelta > 0.25f) add("输入节奏偏急")
        }
        return if (reasons.isEmpty()) {
            when (primary) {
                "积极活跃" -> "当前节奏略有活跃，但心率、呼吸和修改频率仍在可接受范围内。"
                "平静专注" -> "当前信号接近日常基线，节奏较平稳，适合继续当前活动。"
                "轻松平稳" -> "当前生理和输入节奏都比较舒缓，系统会保持低打扰。"
                "安静恢复" -> "当前更像低刺激恢复状态，适合保留一点休息空间。"
                else -> "当前信号接近日常基线，暂不主动打扰。"
            }
        } else {
            "更像$primary：${reasons.joinToString("、")}。"
        }
    }

    private fun lowActivityWeight(sample: HabitSampleEntity): Float =
        if (sample.motionLevel < 0.12f && sample.typingSpeed < 60f) 0.8f else 0.2f

    private fun nightWeight(timestamp: Long): Float {
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(java.util.Calendar.HOUR_OF_DAY)
        return if (hour in 0..5 || hour >= 23) 0.75f else 0.15f
    }

    private fun Float.positiveClamp(): Float = max(0f, this).clamp()
    private fun Float.clamp(): Float = max(0f, min(1f, abs(this)))
}
