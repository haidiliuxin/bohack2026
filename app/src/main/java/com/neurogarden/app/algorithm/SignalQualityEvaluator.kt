package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SignalQualityAssessment(
    val baselineReady: Boolean,
    val validHeartRate: Boolean,
    val validBreathRate: Boolean,
    val hasInteractionSignal: Boolean,
    val motionInterference: Boolean,
    val usableSignalCount: Int,
    val abnormalSignalCount: Int,
    val qualityScore: Float,
    val reason: String?
)

object SignalQualityEvaluator {
    private const val MIN_BASELINE_SAMPLES = 8

    fun assess(
        sample: HabitSampleEntity,
        baseline: UserHabitBaselineEntity,
        thresholds: ThresholdProfileEntity
    ): SignalQualityAssessment {
        val validHeart = sample.heartRate in 45..190
        val validBreath = sample.breathRate in 6..35
        val interaction = sample.typingSpeed > 0f || sample.deleteRate > 0f || sample.pauseDuration > 0f
        val motionInterference = sample.motionLevel > 0.6f
        val baselineReady = baseline.sampleCount >= MIN_BASELINE_SAMPLES

        val usableSignals = listOf(validHeart, validBreath, interaction).count { it }
        val abnormalSignals = listOf(
            validHeart && sample.heartRate - baseline.avgRestingHeartRate >= thresholds.heartRateDeltaWarning * 0.55f,
            validBreath && sample.breathRate - baseline.avgBreathRate >= max(2f, (thresholds.breathRateWarning - baseline.avgBreathRate) * 0.55f),
            interaction && abs(sample.typingSpeed - baseline.avgTypingSpeed) / max(1f, baseline.avgTypingSpeed) >= thresholds.typingSpeedDeltaWarning * 0.70f,
            interaction && sample.deleteRate >= thresholds.deleteRateWarning * 0.70f,
            interaction && sample.pauseDuration >= thresholds.pauseDurationWarning * 0.70f
        ).count { it }

        val baseQuality = when {
            !baselineReady -> 0.42f
            usableSignals >= 3 -> 0.88f
            usableSignals == 2 -> 0.72f
            usableSignals == 1 -> 0.48f
            else -> 0.25f
        }
        val motionPenalty = if (motionInterference) 0.24f else 0f
        val quality = (baseQuality - motionPenalty).coerceIn(0.20f, 0.92f)
        val reason = when {
            !baselineReady -> "baseline_not_ready"
            usableSignals < 2 -> "insufficient_signals"
            motionInterference -> "motion_interference"
            abnormalSignals < 2 -> "single_signal_deviation"
            else -> null
        }

        return SignalQualityAssessment(
            baselineReady = baselineReady,
            validHeartRate = validHeart,
            validBreathRate = validBreath,
            hasInteractionSignal = interaction,
            motionInterference = motionInterference,
            usableSignalCount = usableSignals,
            abnormalSignalCount = abnormalSignals,
            qualityScore = quality,
            reason = reason
        )
    }

    fun isGoodBaselineSample(sample: HabitSampleEntity): Boolean =
        sample.heartRate in 45..150 &&
            sample.breathRate in 6..30 &&
            sample.motionLevel <= 0.55f &&
            sample.contextTag != "passive_guardian_unverified"

    fun qualityCap(quality: SignalQualityAssessment, rawScore: Float): Float = when {
        !quality.baselineReady -> min(rawScore, 0.34f)
        quality.usableSignalCount < 2 -> min(rawScore, 0.34f)
        quality.motionInterference -> min(rawScore, 0.54f)
        quality.abnormalSignalCount < 2 -> min(rawScore, 0.54f)
        else -> rawScore
    }
}
