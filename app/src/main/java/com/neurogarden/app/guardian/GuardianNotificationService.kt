package com.neurogarden.app.guardian

import com.neurogarden.app.algorithm.CareMode
import com.neurogarden.app.data.local.RiskEventEntity
import java.util.Locale

object GuardianNotificationService {
    private const val CONTACTED_SUPPRESS_MS = 60L * 60L * 1000L
    private const val COOLDOWN_MS = 30L * 60L * 1000L

    fun shouldNotifyGuardian(
        event: RiskEventEntity,
        contextMode: CareMode,
        guardianSettings: GuardianSettingsSnapshot,
        notificationHistory: List<GuardianNotificationRecord>,
        feedbackHistory: List<GuardianFeedbackRecord>,
        recentEvents: List<RiskEventEntity>
    ): GuardianNotificationStrategyResult {
        val now = System.currentTimeMillis()
        val tags = mutableListOf<String>()
        val riskHighEnough = event.riskScore >= guardianSettings.notifyThreshold ||
            event.riskLevel in setOf("support", "guardian_check", "urgent_support")
        val continuousDeviation = recentEvents.count {
            it.id != event.id && now - it.startTime <= 6L * 60L * 60L * 1000L && it.riskLevel != "stable"
        } >= 1
        val night = event.timeSegment.contains("night", ignoreCase = true) || event.timeSegment.contains("late", ignoreCase = true)
        val highHeartLowMotion = event.heartRateDeviationPercent >= 22f && event.motionLevel < 0.35f
        val multiMetric = listOf(
            event.heartRateDeviationPercent,
            event.breathRateDeviationPercent,
            event.typingSpeedDeviationPercent,
            event.deleteRateDeviationPercent,
            event.pauseDurationDeviationPercent
        ).count { kotlin.math.abs(it) >= 25f } >= 2

        if (continuousDeviation) tags += "continuous_deviation"
        if (night) tags += "night_priority"
        if (highHeartLowMotion) tags += "high_hr_low_motion"
        if (multiMetric) tags += "multi_metric_deviation"
        if (event.motionLevel >= 0.60f) tags += "motion_downgraded"

        val contactedRecently = feedbackHistory.any {
            it.action == GuardianFeedbackAction.CONTACTED_USER && now - it.createdAt <= CONTACTED_SUPPRESS_MS
        }
        if (contactedRecently) tags += "contacted_recently_suppressed"

        val falsePositiveCount = feedbackHistory.count { it.action == GuardianFeedbackAction.FALSE_POSITIVE }
        if (falsePositiveCount >= 2) tags += "false_positive_frequency_limit"

        val latestNotification = notificationHistory.firstOrNull()
        val cooldownApplied = latestNotification != null && now - latestNotification.sentAt < COOLDOWN_MS
        if (cooldownApplied) tags += "cooldown_applied"

        val dailyCount = notificationHistory.count { now - it.sentAt <= 24L * 60L * 60L * 1000L && it.status == GuardianNotificationStatus.SENT }
        val dailyLimit = when (contextMode) {
            CareMode.SELF_MONITORING -> 0
            CareMode.FAMILY_GUARDIAN -> 4
            CareMode.SPECIAL_CARE -> 3
        }
        if (dailyCount >= dailyLimit) tags += "daily_limit_reached"

        val allowedByMode = contextMode != CareMode.SELF_MONITORING
        val allowedByAuthorization = guardianSettings.authorizationStatus == GuardianAuthorizationStatus.AUTHORIZED
        val meaningful = riskHighEnough || continuousDeviation || night || highHeartLowMotion || multiMetric
        val shouldNotify = allowedByMode &&
            guardianSettings.notificationEnabled &&
            allowedByAuthorization &&
            meaningful &&
            event.motionLevel < 0.60f &&
            !contactedRecently &&
            !cooldownApplied &&
            dailyCount < dailyLimit &&
            falsePositiveCount < 3

        val priority = when {
            contextMode == CareMode.SPECIAL_CARE && (highHeartLowMotion || multiMetric) -> GuardianPriority.HIGH
            night && meaningful -> GuardianPriority.HIGH
            event.riskScore >= 0.78f || event.riskLevel == "guardian_check" -> GuardianPriority.HIGH
            continuousDeviation || multiMetric -> GuardianPriority.MEDIUM
            else -> GuardianPriority.LOW
        }

        val reason = when {
            !allowedByMode -> "自我监测模式默认不通知监护人。"
            !guardianSettings.notificationEnabled -> "监护提醒未开启。"
            !allowedByAuthorization -> "尚未授权真实守护通知，仅允许本地模拟体验。"
            event.motionLevel >= 0.60f -> "当前运动干扰较高，已降低提醒等级。"
            contactedRecently -> "监护人近期已联系本人，冷却期内不重复提醒。"
            cooldownApplied -> "提醒冷却期内，避免短时间重复打扰。"
            dailyCount >= dailyLimit -> "已达到今日守护提醒上限。"
            falsePositiveCount >= 3 -> "近期误报较多，系统自动降频。"
            !meaningful -> "当前为轻微单次波动，仅本地记录。"
            else -> "满足守护提醒策略：${tags.joinToString("、").ifBlank { "状态偏离" }}。"
        }

        return GuardianNotificationStrategyResult(
            shouldNotify = shouldNotify,
            reason = reason,
            priority = priority,
            channels = guardianSettings.notificationChannels.ifEmpty { listOf(GuardianNotificationChannel.APP) },
            cooldownApplied = cooldownApplied,
            strategyTags = tags.ifEmpty { listOf("local_review") }
        )
    }

    fun createMockNotification(
        event: RiskEventEntity,
        settings: GuardianSettingsSnapshot,
        strategy: GuardianNotificationStrategyResult,
        deviationLevel: SpecialCareDeviationLevel?
    ): GuardianNotificationRecord {
        val now = System.currentTimeMillis()
        val status = if (strategy.shouldNotify) GuardianNotificationStatus.SENT else GuardianNotificationStatus.SKIPPED
        val message = "NeuroGarden 守护提醒：检测到用户状态出现连续偏离，当前建议继续观察。系统未上传输入原文，仅共享结构化状态摘要。请根据实际情况确认是否联系本人。"
        return GuardianNotificationRecord(
            notificationId = "notify_${now}_${event.id}",
            eventId = event.id,
            guardianName = settings.guardianName.ifBlank { "监护人" },
            relationship = settings.relationship.ifBlank { "未设置" },
            channels = strategy.channels,
            status = status,
            reason = strategy.reason,
            riskLevel = event.riskLevel.lowercase(Locale.getDefault()),
            deviationLevel = deviationLevel,
            sentAt = now,
            cooldownApplied = strategy.cooldownApplied,
            strategyTags = strategy.strategyTags,
            messagePreview = if (status == GuardianNotificationStatus.SENT) message else "本地模拟记录：${strategy.reason}"
        )
    }
}
