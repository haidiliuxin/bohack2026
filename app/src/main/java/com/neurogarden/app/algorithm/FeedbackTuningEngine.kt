package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import kotlin.math.abs

data class ThresholdAdjustmentResult(
    val updatedProfile: ThresholdProfileEntity,
    val message: String,
    val changed: Boolean
)

object FeedbackTuningEngine {
    fun tune(
        event: RiskEventEntity,
        guardianFeedback: String,
        current: ThresholdProfileEntity,
        now: Long = System.currentTimeMillis()
    ): ThresholdAdjustmentResult {
        val related = relatedMetrics(event)
        val type = guardianFeedback.toFeedbackType()
        val profile = when (type) {
            FeedbackType.CONFIRMED -> current.adjustSensitivity(
                related = related,
                metricFactor = 0.94f,
                notifyDelta = -0.03f,
                durationDelta = -15,
                reason = "guardian_confirmed:${event.id}",
                now = now
            )

            FeedbackType.FALSE_ALARM -> current.adjustSensitivity(
                related = related,
                metricFactor = 1.08f,
                notifyDelta = 0.04f,
                durationDelta = 30,
                reason = "guardian_false_alarm:${event.id}",
                now = now
            )

            FeedbackType.CONTACTED -> current.copy(
                id = 0,
                updatedBy = "guardian_feedback",
                updatedReason = "guardian_contacted:${event.id}",
                updatedAt = now
            )

            FeedbackType.OBSERVE -> current.copy(
                id = 0,
                riskTriggerDuration = (current.riskTriggerDuration + 60).coerceIn(60, 900),
                guardianNotifyThreshold = (current.guardianNotifyThreshold + 0.01f).coerceIn(0.45f, 0.95f),
                updatedBy = "guardian_feedback",
                updatedReason = "guardian_observe:${event.id}",
                updatedAt = now
            )

            FeedbackType.PRIORITY_UP -> current.adjustSensitivity(
                related = related,
                metricFactor = 0.92f,
                notifyDelta = -0.04f,
                durationDelta = -30,
                reason = "guardian_priority_up:${event.id}",
                now = now
            )

            FeedbackType.PRIORITY_DOWN -> current.adjustSensitivity(
                related = related,
                metricFactor = 1.06f,
                notifyDelta = 0.04f,
                durationDelta = 30,
                reason = "guardian_priority_down:${event.id}",
                now = now
            )

            FeedbackType.OTHER -> current.copy(
                id = 0,
                updatedBy = "guardian_feedback",
                updatedReason = "guardian_feedback:${event.id}:$guardianFeedback",
                updatedAt = now
            )
        }

        return ThresholdAdjustmentResult(
            updatedProfile = profile,
            message = messageFor(type),
            changed = profile != current
        )
    }

    private fun ThresholdProfileEntity.adjustSensitivity(
        related: Set<Metric>,
        metricFactor: Float,
        notifyDelta: Float,
        durationDelta: Int,
        reason: String,
        now: Long
    ): ThresholdProfileEntity = copy(
        id = 0,
        heartRateDeltaWarning = if (Metric.HEART in related) {
            (heartRateDeltaWarning * metricFactor).coerceIn(8f, 40f)
        } else {
            heartRateDeltaWarning
        },
        breathRateWarning = if (Metric.BREATH in related) {
            (breathRateWarning * metricFactor).coerceIn(12f, 32f)
        } else {
            breathRateWarning
        },
        typingSpeedDeltaWarning = if (Metric.TYPING in related) {
            (typingSpeedDeltaWarning * metricFactor).coerceIn(0.12f, 0.80f)
        } else {
            typingSpeedDeltaWarning
        },
        deleteRateWarning = if (Metric.DELETE in related) {
            (deleteRateWarning * metricFactor).coerceIn(0.04f, 0.60f)
        } else {
            deleteRateWarning
        },
        pauseDurationWarning = if (Metric.PAUSE in related) {
            (pauseDurationWarning * metricFactor).coerceIn(1f, 12f)
        } else {
            pauseDurationWarning
        },
        riskTriggerDuration = (riskTriggerDuration + durationDelta).coerceIn(60, 900),
        guardianNotifyThreshold = (guardianNotifyThreshold + notifyDelta).coerceIn(0.45f, 0.95f),
        updatedBy = "guardian_feedback",
        updatedReason = reason,
        updatedAt = now
    )

    private fun relatedMetrics(event: RiskEventEntity): Set<Metric> = buildSet {
        if (event.heartRateDeviationPercent > 15f) add(Metric.HEART)
        if (event.breathRateDeviationPercent > 20f) add(Metric.BREATH)
        if (abs(event.typingSpeedDeviationPercent) > 25f) add(Metric.TYPING)
        if (event.deleteRateDeviationPercent > 30f) add(Metric.DELETE)
        if (event.pauseDurationDeviationPercent > 35f) add(Metric.PAUSE)
        if (isEmpty()) addAll(Metric.entries)
    }

    private fun messageFor(type: FeedbackType): String = when (type) {
        FeedbackType.CONFIRMED -> "已确认异常，系统将提高类似模式的敏感度。"
        FeedbackType.FALSE_ALARM -> "已记录为误报，系统将降低类似模式的提醒优先级。"
        FeedbackType.CONTACTED -> "已记录为已联系本人，系统会避免对同一事件重复提醒。"
        FeedbackType.OBSERVE -> "已进入继续观察状态，系统将在后续时间窗口内持续监测。"
        FeedbackType.PRIORITY_UP -> "已提高该类事件组合的提醒优先级。"
        FeedbackType.PRIORITY_DOWN -> "已降低该类事件组合的提醒优先级。"
        FeedbackType.OTHER -> "已记录反馈，系统会用于后续个体化判断。"
    }

    private fun String.toFeedbackType(): FeedbackType = when {
        contains("确认异常") || contains("纭") -> FeedbackType.CONFIRMED
        contains("误报") || contains("璇姤") -> FeedbackType.FALSE_ALARM
        contains("已联系") || contains("宸茶仈绯") -> FeedbackType.CONTACTED
        contains("继续观察") || contains("缁х画瑙傚療") -> FeedbackType.OBSERVE
        contains("提高") || contains("鎻愰珮") -> FeedbackType.PRIORITY_UP
        contains("降低") || contains("闄嶄綆") -> FeedbackType.PRIORITY_DOWN
        else -> FeedbackType.OTHER
    }

    private enum class Metric {
        HEART,
        BREATH,
        TYPING,
        DELETE,
        PAUSE
    }

    private enum class FeedbackType {
        CONFIRMED,
        FALSE_ALARM,
        CONTACTED,
        OBSERVE,
        PRIORITY_UP,
        PRIORITY_DOWN,
        OTHER
    }
}
