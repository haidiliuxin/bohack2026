package com.neurogarden.app.algorithm

import com.neurogarden.app.agent.AgentSignalResponse
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class RiskLevel(val displayName: String) {
    STABLE("Stable"),
    OBSERVE("Observe"),
    SUPPORT("Self support"),
    GUARDIAN_CHECK("Guardian check"),
    URGENT_SUPPORT("Urgent support")
}

data class PersonalizedRiskResult(
    val riskScore: Float,
    val riskLevel: RiskLevel,
    val confidence: Float,
    val suggestedAction: String,
    val careMessage: String,
    val guardianTriggerReason: String?
)

object PersonalizedRiskCalculator {
    fun calculate(
        sample: HabitSampleEntity,
        baseline: UserHabitBaselineEntity,
        thresholds: ThresholdProfileEntity,
        agentResponse: AgentSignalResponse? = null,
        recentSamples: List<HabitSampleEntity> = emptyList()
    ): PersonalizedRiskResult {
        val quality = SignalQualityEvaluator.assess(sample, baseline, thresholds)
        val heartDeviation = if (quality.validHeartRate) {
            ((sample.heartRate - baseline.avgRestingHeartRate) / thresholds.heartRateDeltaWarning).positiveClamp()
        } else {
            0f
        }
        val breathDeviation = if (quality.validBreathRate) {
            ((sample.breathRate - baseline.avgBreathRate) / max(1f, thresholds.breathRateWarning - baseline.avgBreathRate)).positiveClamp()
        } else {
            0f
        }
        val typingDeviation = if (quality.hasInteractionSignal) {
            (abs(sample.typingSpeed - baseline.avgTypingSpeed) / max(1f, baseline.avgTypingSpeed) / thresholds.typingSpeedDeltaWarning).clamp()
        } else {
            0f
        }
        val deleteDeviation = if (quality.hasInteractionSignal) {
            (sample.deleteRate / thresholds.deleteRateWarning).positiveClamp()
        } else {
            0f
        }
        val pauseDeviation = if (quality.hasInteractionSignal) {
            (sample.pauseDuration / thresholds.pauseDurationWarning).positiveClamp()
        } else {
            0f
        }
        val motionPenalty = if (quality.motionInterference) -0.18f else 0f
        val feedbackAdjustment = when (sample.userFeedback) {
            "need_support" -> 0.12f
            "false_alarm" -> -0.10f
            "helpful" -> 0.04f
            else -> 0f
        }
        val agentAdjustment = agentResponse?.thresholdAdjustments?.let { adjustments ->
            when {
                adjustments.containsKey("guardianNotifyThreshold") &&
                    adjustments.getValue("guardianNotifyThreshold") < thresholds.guardianNotifyThreshold -> 0.05f
                adjustments.containsKey("guardianNotifyThreshold") -> -0.03f
                else -> 0f
            }
        } ?: 0f

        val rawScore = (
            heartDeviation * 0.30f +
                breathDeviation * 0.22f +
                typingDeviation * 0.16f +
                deleteDeviation * 0.14f +
                pauseDeviation * 0.18f +
                motionPenalty +
                feedbackAdjustment +
                agentAdjustment
            ).clamp()
        val modelScore = agentResponse?.riskScore?.coerceIn(0f, 1f)
        val blendedScore = if (modelScore != null && agentResponse.confidence >= 0.55f) {
            rawScore * 0.58f + modelScore * 0.42f
        } else {
            rawScore
        }
        val score = SignalQualityEvaluator.qualityCap(quality, blendedScore)
        val sustained = hasSustainedDeviation(recentSamples, baseline, thresholds)
        val localRiskLevel = localLevelFor(score, thresholds.guardianNotifyThreshold, quality, sustained)
        val riskLevel = chooseRiskLevel(
            local = localRiskLevel,
            agent = agentResponse?.riskLevel?.toRiskLevel(),
            quality = quality,
            sustained = sustained
        )
        val confidence = min(
            confidenceFor(baseline.confidenceLevel, sample.motionLevel, agentResponse?.confidence),
            quality.qualityScore
        )
        val guardianReason = when {
            riskLevel == RiskLevel.URGENT_SUPPORT -> "sustained_high_deviation"
            riskLevel == RiskLevel.GUARDIAN_CHECK -> "sustained_guardian_threshold"
            quality.reason != null -> "quality_gate:${quality.reason}"
            score >= thresholds.guardianNotifyThreshold -> "guardian_threshold"
            else -> null
        }

        return PersonalizedRiskResult(
            riskScore = score,
            riskLevel = riskLevel,
            confidence = confidence,
            suggestedAction = agentResponse?.suggestedAction ?: actionFor(riskLevel, quality),
            careMessage = agentResponse?.careMessage ?: messageFor(riskLevel, quality),
            guardianTriggerReason = guardianReason
        )
    }

    private fun localLevelFor(
        score: Float,
        guardianThreshold: Float,
        quality: SignalQualityAssessment,
        sustained: Boolean
    ): RiskLevel {
        if (!quality.baselineReady || quality.usableSignalCount < 2) {
            return if (score >= 0.35f) RiskLevel.OBSERVE else RiskLevel.STABLE
        }
        if (quality.motionInterference || quality.abnormalSignalCount < 2) {
            return if (score >= 0.35f) RiskLevel.OBSERVE else RiskLevel.STABLE
        }
        return when {
            score >= 0.86f && sustained -> RiskLevel.URGENT_SUPPORT
            score >= guardianThreshold && sustained -> RiskLevel.GUARDIAN_CHECK
            score >= 0.55f -> RiskLevel.SUPPORT
            score >= 0.35f -> RiskLevel.OBSERVE
            else -> RiskLevel.STABLE
        }
    }

    private fun chooseRiskLevel(
        local: RiskLevel,
        agent: RiskLevel?,
        quality: SignalQualityAssessment,
        sustained: Boolean
    ): RiskLevel {
        val proposed = listOfNotNull(local, agent).maxOrNull() ?: local
        val cap = when {
            !quality.baselineReady || quality.usableSignalCount < 2 -> RiskLevel.OBSERVE
            quality.motionInterference -> RiskLevel.OBSERVE
            quality.abnormalSignalCount < 2 -> RiskLevel.OBSERVE
            !sustained && proposed >= RiskLevel.GUARDIAN_CHECK -> RiskLevel.SUPPORT
            else -> proposed
        }
        return if (proposed <= cap) proposed else cap
    }

    private fun hasSustainedDeviation(
        samples: List<HabitSampleEntity>,
        baseline: UserHabitBaselineEntity,
        thresholds: ThresholdProfileEntity
    ): Boolean {
        if (samples.size < 3) return false
        val now = samples.maxOf { it.timestamp }
        return samples
            .filter { it.timestamp >= now - 15L * 60L * 1000L }
            .take(6)
            .count {
                SignalQualityEvaluator.assess(it, baseline, thresholds).let { quality ->
                    quality.baselineReady && !quality.motionInterference && quality.abnormalSignalCount >= 2
                }
            } >= 2
    }

    private fun String.toRiskLevel(): RiskLevel = when (this) {
        "urgent_support" -> RiskLevel.URGENT_SUPPORT
        "guardian_check" -> RiskLevel.GUARDIAN_CHECK
        "support" -> RiskLevel.SUPPORT
        "observe" -> RiskLevel.OBSERVE
        else -> RiskLevel.STABLE
    }

    private fun confidenceFor(baselineConfidence: String, motionLevel: Float, agentConfidence: Float?): Float {
        val baseline = when (baselineConfidence) {
            "high" -> 0.88f
            "medium" -> 0.72f
            else -> 0.54f
        }
        val motionAdjusted = if (motionLevel > 0.6f) baseline - 0.18f else baseline
        return ((motionAdjusted + (agentConfidence ?: motionAdjusted)) / 2f).clamp()
    }

    private fun actionFor(level: RiskLevel, quality: SignalQualityAssessment): String = when {
        quality.reason == "baseline_not_ready" -> "Keep learning baseline before strong alerts"
        quality.reason == "insufficient_signals" -> "Wait for more reliable signals"
        quality.reason == "motion_interference" -> "Treat as motion interference and observe"
        level == RiskLevel.URGENT_SUPPORT -> "Confirm safety and suggest guardian support"
        level == RiskLevel.GUARDIAN_CHECK -> "Start care flow and ask whether to notify guardian"
        level == RiskLevel.SUPPORT -> "Start breathing guidance and gentle support"
        level == RiskLevel.OBSERVE -> "Pause briefly and observe body tension"
        else -> "Keep current rhythm"
    }

    private fun messageFor(level: RiskLevel, quality: SignalQualityAssessment): String = when {
        quality.reason == "baseline_not_ready" -> "I am still learning your usual rhythm, so I will only observe lightly for now."
        quality.reason == "insufficient_signals" -> "There are not enough reliable signals yet, so I will not make a strong judgment."
        quality.reason == "motion_interference" -> "Movement may be affecting the reading, so this is treated as low-confidence."
        quality.reason == "single_signal_deviation" -> "Only one signal is clearly different, so I will keep this as an observation."
        level == RiskLevel.URGENT_SUPPORT -> "Your rhythm has stayed far from baseline for a while. Are you safe right now?"
        level == RiskLevel.GUARDIAN_CHECK -> "Several signals have stayed elevated. Let us slow the rhythm first."
        level == RiskLevel.SUPPORT -> "Your rhythm looks tense. We can slow the next few breaths together."
        level == RiskLevel.OBSERVE -> "There is a light deviation from your usual rhythm. Pause for a few seconds if you can."
        else -> "Your current rhythm looks close to baseline."
    }

    private fun Float.positiveClamp(): Float = max(0f, this).clamp()
    private fun Float.clamp(): Float = max(0f, min(1f, this))
}
