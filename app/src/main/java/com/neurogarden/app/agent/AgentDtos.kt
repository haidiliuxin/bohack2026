package com.neurogarden.app.agent

import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity

data class HabitSampleDto(
    val timestamp: Long,
    val heartRate: Int,
    val breathRate: Int,
    val motionLevel: Float,
    val typingSpeed: Float,
    val deleteRate: Float,
    val pauseDuration: Float,
    val userFeedback: String?,
    val contextTag: String,
    val riskLevel: String
)

data class UserHabitBaselineDto(
    val avgRestingHeartRate: Float,
    val avgBreathRate: Float,
    val avgTypingSpeed: Float,
    val avgDeleteRate: Float,
    val avgPauseDuration: Float,
    val avgRecoveryDuration: Float,
    val sampleCount: Int,
    val confidenceLevel: String
)

data class ThresholdProfileDto(
    val heartRateDeltaWarning: Float,
    val breathRateWarning: Float,
    val typingSpeedDeltaWarning: Float,
    val deleteRateWarning: Float,
    val pauseDurationWarning: Float,
    val riskTriggerDuration: Int,
    val guardianNotifyThreshold: Float
)

data class AgentSignalRequest(
    val userId: String,
    val recentSignals: List<HabitSampleDto>,
    val currentBaseline: UserHabitBaselineDto,
    val currentThresholds: ThresholdProfileDto,
    val latestRiskScore: Float,
    val latestRiskLevel: String,
    val userFeedback: String?,
    val userEmotionLabel: String? = null,
    val weather: String? = null,
    val timeSegment: String? = null,
    val personalityModel: String? = null,
    val recentActivity: String? = null
)

data class AgentSignalResponse(
    val riskScore: Float? = null,
    val riskLevel: String,
    val emotionalState: String? = null,
    val arousalScore: Float? = null,
    val valenceScore: Float? = null,
    val fatigueScore: Float? = null,
    val lonelinessScore: Float? = null,
    val stressScore: Float? = null,
    val suggestedAction: String,
    val careMessage: String,
    val shouldNotifyGuardian: Boolean,
    val thresholdAdjustments: Map<String, Float>,
    val confidence: Float,
    val reason: String,
    val mainReasons: List<String> = emptyList(),
    val metricDeviationPercent: Map<String, Float> = emptyMap()
)

data class ThresholdTuningRequest(
    val currentThresholds: ThresholdProfileDto,
    val recentSignals: List<HabitSampleDto>,
    val falseAlarmCount: Int,
    val helpfulFeedbackCount: Int
)

data class ThresholdTuningResponse(
    val adjustedThresholds: Map<String, Float>,
    val reason: String,
    val confidence: Float
)

data class CareMessageRequest(
    val riskLevel: String,
    val contextTag: String?,
    val preferredTone: String = "calm"
)

data class CareMessageResponse(
    val message: String,
    val suggestedAction: String
)

data class SupportConversationMessageDto(
    val role: String,
    val content: String
)

data class SupportConversationRequest(
    val userId: String,
    val currentRiskLevel: String,
    val currentRiskScore: Float,
    val recentSignals: List<HabitSampleDto>,
    val conversation: List<SupportConversationMessageDto>,
    val latestUserMessage: String,
    val userEmotionLabel: String? = null,
    val recentRiskContext: String? = null,
    val personalityModel: String? = null,
    val recentActivity: String? = null
)

data class SupportConversationResponse(
    val reply: String,
    val riskLevel: String,
    val suggestedAction: String,
    val shouldNotifyGuardian: Boolean,
    val confidence: Float,
    val reason: String
)

fun HabitSampleEntity.toDto(): HabitSampleDto =
    HabitSampleDto(
        timestamp = timestamp,
        heartRate = heartRate,
        breathRate = breathRate,
        motionLevel = motionLevel,
        typingSpeed = typingSpeed,
        deleteRate = deleteRate,
        pauseDuration = pauseDuration,
        userFeedback = userFeedback,
        contextTag = contextTag,
        riskLevel = riskLevel
    )

fun UserHabitBaselineEntity.toDto(): UserHabitBaselineDto =
    UserHabitBaselineDto(
        avgRestingHeartRate = avgRestingHeartRate,
        avgBreathRate = avgBreathRate,
        avgTypingSpeed = avgTypingSpeed,
        avgDeleteRate = avgDeleteRate,
        avgPauseDuration = avgPauseDuration,
        avgRecoveryDuration = avgRecoveryDuration,
        sampleCount = sampleCount,
        confidenceLevel = confidenceLevel
    )

fun ThresholdProfileEntity.toDto(): ThresholdProfileDto =
    ThresholdProfileDto(
        heartRateDeltaWarning = heartRateDeltaWarning,
        breathRateWarning = breathRateWarning,
        typingSpeedDeltaWarning = typingSpeedDeltaWarning,
        deleteRateWarning = deleteRateWarning,
        pauseDurationWarning = pauseDurationWarning,
        riskTriggerDuration = riskTriggerDuration,
        guardianNotifyThreshold = guardianNotifyThreshold
    )
