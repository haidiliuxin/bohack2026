package com.neurogarden.app.guardian

import com.neurogarden.app.data.local.RiskEventEntity

object SpecialCareService {
    fun getSpecialCareDeviationLevel(
        event: RiskEventEntity,
        history: List<RiskEventEntity>,
        feedbackHistory: List<GuardianFeedbackRecord>
    ): SpecialCareDeviationResult {
        val reasons = buildList {
            if (event.heartRateDeviationPercent >= 25f && event.motionLevel < 0.35f) add("高心率与低运动同时出现")
            if (event.breathRateDeviationPercent >= 25f) add("呼吸节律偏离个人基线")
            if (event.typingSpeedDeviationPercent >= 35f || event.deleteRateDeviationPercent >= 35f) add("输入节奏偏离个人习惯")
            if (event.pauseDurationDeviationPercent >= 35f) add("停顿时长明显变化")
            if (event.timeSegment.contains("night", ignoreCase = true) || event.timeSegment.contains("late", ignoreCase = true)) add("夜间时段需要更温和地确认")
        }
        val recentSimilar = history.count {
            it.id != event.id &&
                it.riskLevel == event.riskLevel &&
                kotlin.math.abs(it.startTime - event.startTime) <= 24L * 60L * 60L * 1000L
        }
        val raisedByGuardian = feedbackHistory.any {
            it.action == GuardianFeedbackAction.INCREASE_PRIORITY && it.eventId == event.id
        }
        val falsePositiveCount = feedbackHistory.count { it.action == GuardianFeedbackAction.FALSE_POSITIVE }
        val multiMetric = reasons.count { it.contains("偏离") || it.contains("高心率") || it.contains("停顿") } >= 2

        val level = when {
            raisedByGuardian || (event.riskScore >= 0.82f && recentSimilar >= 2) -> SpecialCareDeviationLevel.FOCUS_ATTENTION
            event.riskScore >= 0.70f && (multiMetric || recentSimilar >= 1) -> SpecialCareDeviationLevel.CARE_CONFIRMATION_SUGGESTED
            event.riskScore >= 0.52f || recentSimilar >= 1 || reasons.any { it.contains("夜间") } -> SpecialCareDeviationLevel.OBSERVE_NEEDED
            else -> SpecialCareDeviationLevel.SLIGHT_DEVIATION
        }.let { level ->
            if (falsePositiveCount >= 2 && level == SpecialCareDeviationLevel.CARE_CONFIRMATION_SUGGESTED) {
                SpecialCareDeviationLevel.OBSERVE_NEEDED
            } else {
                level
            }
        }

        val recommendedAction = when (level) {
            SpecialCareDeviationLevel.SLIGHT_DEVIATION -> "先本地记录，继续观察后续节律变化。"
            SpecialCareDeviationLevel.OBSERVE_NEEDED -> "建议保持观察，如短时间重复出现再提醒照护者确认。"
            SpecialCareDeviationLevel.CARE_CONFIRMATION_SUGGESTED -> "建议通知照护者进行一次温和确认。"
            SpecialCareDeviationLevel.FOCUS_ATTENTION -> "建议优先让照护者确认近况，并避免重复打扰本人。"
        }

        return SpecialCareDeviationResult(
            deviationLevel = level,
            displayText = level.displayName,
            reasons = reasons.ifEmpty { listOf("结构化指标轻度波动") }.take(3),
            recommendedAction = recommendedAction,
            notifyGuardianRecommended = level >= SpecialCareDeviationLevel.CARE_CONFIRMATION_SUGGESTED
        )
    }

    fun updateCareLoop(
        current: CareLoopRecord?,
        eventId: Long,
        deviation: SpecialCareDeviationResult,
        notificationId: String?,
        feedback: GuardianFeedbackRecord?
    ): CareLoopRecord {
        val now = System.currentTimeMillis()
        val status = when (feedback?.action) {
            GuardianFeedbackAction.CONTACTED_USER -> CareLoopStatus.RESOLVED
            GuardianFeedbackAction.KEEP_WATCHING, GuardianFeedbackAction.REMIND_LATER -> CareLoopStatus.WATCHING
            GuardianFeedbackAction.FALSE_POSITIVE, GuardianFeedbackAction.MUTE_THIS_EVENT -> CareLoopStatus.FALSE_POSITIVE
            GuardianFeedbackAction.INCREASE_PRIORITY, GuardianFeedbackAction.DECREASE_PRIORITY -> CareLoopStatus.ACKNOWLEDGED
            null -> if (notificationId != null) CareLoopStatus.NOTIFIED else current?.status ?: CareLoopStatus.OPEN
        }
        return CareLoopRecord(
            eventId = eventId,
            enabled = true,
            deviationLevel = deviation.deviationLevel,
            recommendedAction = deviation.recommendedAction,
            notifyGuardianRecommended = deviation.notifyGuardianRecommended,
            notificationId = notificationId ?: current?.notificationId,
            feedbackId = feedback?.feedbackId ?: current?.feedbackId,
            status = status,
            createdAt = current?.createdAt ?: now,
            updatedAt = now
        )
    }
}
