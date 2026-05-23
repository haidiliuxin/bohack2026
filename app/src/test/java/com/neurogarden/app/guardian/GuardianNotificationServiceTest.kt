package com.neurogarden.app.guardian

import com.google.common.truth.Truth.assertThat
import com.neurogarden.app.algorithm.CareMode
import com.neurogarden.app.data.local.RiskEventEntity
import org.junit.Before
import org.junit.Test

class GuardianNotificationServiceTest {

    private lateinit var defaultSettings: GuardianSettingsSnapshot
    private lateinit var defaultEvent: RiskEventEntity

    @Before
    fun setup() {
        defaultSettings = GuardianSettingsSnapshot(
            guardianName = "张叔叔",
            relationship = "父亲",
            phone = "13800138000",
            wechat = "",
            email = "",
            notificationEnabled = true,
            notificationChannels = listOf(GuardianNotificationChannel.APP),
            notificationStart = "08:00",
            notificationEnd = "22:00",
            allowNightEmergency = true,
            emergencyNote = "夜间紧急联系",
            authorizationStatus = GuardianAuthorizationStatus.AUTHORIZED,
            specialCareEnabled = false,
            notifyThreshold = 0.5f
        )

        defaultEvent = createEvent(
            riskScore = 0.6f,
            riskLevel = "medium",
            heartRateDeviation = 15f,
            motionLevel = 0.3f,
            timeSegment = "morning"
        )
    }

    private fun createEvent(
        id: Long = 1,
        riskScore: Float = 0.5f,
        riskLevel: String = "medium",
        heartRateDeviation: Float = 0f,
        breathRateDeviation: Float = 0f,
        typingSpeedDeviation: Float = 0f,
        deleteRateDeviation: Float = 0f,
        pauseDurationDeviation: Float = 0f,
        motionLevel: Float = 0.3f,
        timeSegment: String = "morning"
    ): RiskEventEntity {
        return RiskEventEntity(
            id = id,
            startTime = System.currentTimeMillis() - 3600000,
            endTime = System.currentTimeMillis(),
            riskScore = riskScore,
            riskLevel = riskLevel,
            confidence = 0.8f,
            mainReasons = "测试事件",
            metricDeviationPercent = "{}",
            heartRateDeviationPercent = heartRateDeviation,
            breathRateDeviationPercent = breathRateDeviation,
            typingSpeedDeviationPercent = typingSpeedDeviation,
            deleteRateDeviationPercent = deleteRateDeviation,
            pauseDurationDeviationPercent = pauseDurationDeviation,
            motionLevel = motionLevel,
            weather = "sunny",
            timeSegment = timeSegment,
            agentAnalysis = "{}",
            suggestedAction = "观察",
            guardianNotified = false,
            guardianFeedback = null,
            isFalseAlarm = false,
            createdAt = System.currentTimeMillis()
        )
    }

    // 测试1: 未授权时不允许真实通知
    @Test
    fun shouldNotifyGuardian_whenNotAuthorized_doesNotAllowRealNotification() {
        val unauthorizedSettings = defaultSettings.copy(
            authorizationStatus = GuardianAuthorizationStatus.NOT_AUTHORIZED
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = defaultEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = unauthorizedSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isFalse()
        assertThat(result.reason).contains("未授权")
    }

    // 测试2: 本地模拟体验可以生成 mock notification
    @Test
    fun createMockNotification_whenNotAuthorized_allowsLocalSimulation() {
        val unauthorizedSettings = defaultSettings.copy(
            authorizationStatus = GuardianAuthorizationStatus.NOT_AUTHORIZED
        )

        val strategyResult = GuardianNotificationStrategyResult(
            shouldNotify = false,
            reason = "本地模拟体验",
            priority = GuardianPriority.LOW,
            channels = listOf(GuardianNotificationChannel.APP),
            cooldownApplied = false,
            strategyTags = listOf("local_simulation")
        )

        val mockNotification = GuardianNotificationService.createMockNotification(
            event = defaultEvent,
            settings = unauthorizedSettings,
            strategy = strategyResult,
            deviationLevel = null
        )

        assertThat(mockNotification.notificationId).isNotEmpty()
        assertThat(mockNotification.eventId).isEqualTo(defaultEvent.id)
        assertThat(mockNotification.status).isEqualTo(GuardianNotificationStatus.SKIPPED)
        assertThat(mockNotification.messagePreview).contains("本地模拟")
    }

    // 测试3: 低风险事件不通知监护人
    @Test
    fun shouldNotifyGuardian_whenLowRisk_noNotification() {
        val lowRiskEvent = createEvent(
            riskScore = 0.2f,
            riskLevel = "low",
            heartRateDeviation = 5f,
            motionLevel = 0.2f
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = lowRiskEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isFalse()
        assertThat(result.reason).contains("轻微")
    }

    // 测试4: 中高风险事件满足条件时通知
    @Test
    fun shouldNotifyGuardian_whenMediumRiskAndAuthorized_notifies() {
        val mediumRiskEvent = createEvent(
            riskScore = 0.8f,
            riskLevel = "high",
            heartRateDeviation = 30f,
            breathRateDeviation = 28f,
            motionLevel = 0.2f
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = mediumRiskEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isTrue()
        assertThat(result.priority).isAtLeast(GuardianPriority.HIGH)
    }

    // 测试5: 连续多次异常才升级通知
    @Test
    fun shouldNotifyGuardian_whenContinuousDeviation_escalatesNotification() {
        val currentEvent = createEvent(
            riskScore = 0.55f,
            riskLevel = "medium",
            heartRateDeviation = 25f
        )

        val recentEvents = listOf(
            createEvent(
                id = 2,
                riskScore = 0.6f,
                riskLevel = "medium",
                startTimeOffset = -1800000 // 30分钟前
            )
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = currentEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = recentEvents
        )

        assertThat(result.shouldNotify).isTrue()
        assertThat(result.strategyTags).contains("continuous_deviation")
    }

    // 测试6: 夜间异常优先通知
    @Test
    fun shouldNotifyGuardian_whenNightEmergency_setsHighPriority() {
        val nightEvent = createEvent(
            riskScore = 0.6f,
            riskLevel = "medium",
            timeSegment = "night"
        )

        val nightSettings = defaultSettings.copy(allowNightEmergency = true)

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = nightEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = nightSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isTrue()
        assertThat(result.priority).isAtLeast(GuardianPriority.MEDIUM)
        assertThat(result.strategyTags).contains("night_priority")
    }

    // 测试7: 运动状态下降级
    @Test
    fun shouldNotifyGuardian_whenHighMotion_downgradesPriority() {
        val highMotionEvent = createEvent(
            riskScore = 0.75f,
            riskLevel = "high",
            motionLevel = 0.75f
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = highMotionEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isFalse()
        assertThat(result.strategyTags).contains("motion_downgraded")
    }

    // 测试8: 高心率 + 低运动优先提醒
    @Test
    fun shouldNotifyGuardian_whenHighHeartRateAndLowMotion_notifies() {
        val highHREvent = createEvent(
            riskScore = 0.65f,
            riskLevel = "medium",
            heartRateDeviation = 30f,
            motionLevel = 0.2f
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = highHREvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = emptyList(),
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isTrue()
        assertThat(result.strategyTags).contains("high_hr_low_motion")
    }

    // 测试9: 已联系本人后冷却期内不重复提醒
    @Test
    fun shouldNotifyGuardian_whenContactedRecently_suppressesNotification() {
        val now = System.currentTimeMillis()
        val recentFeedback = listOf(
            GuardianFeedbackRecord(
                feedbackId = "fb_1",
                eventId = 1,
                guardianName = "张叔叔",
                action = GuardianFeedbackAction.CONTACTED_USER,
                note = "已联系",
                createdAt = now - 1800000, // 30分钟前
                source = "app",
                sensitivityAdjustment = "unchanged"
            )
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = defaultEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = recentFeedback,
            recentEvents = emptyList()
        )

        assertThat(result.shouldNotify).isFalse()
        assertThat(result.strategyTags).contains("contacted_recently_suppressed")
    }

    // 测试10: 标记误报后同类通知降频
    @Test
    fun shouldNotifyGuardian_whenMultipleFalsePositives_reducesFrequency() {
        val now = System.currentTimeMillis()
        val falsePositiveHistory = listOf(
            GuardianFeedbackRecord(
                feedbackId = "fb_1",
                eventId = 1,
                guardianName = "张叔叔",
                action = GuardianFeedbackAction.FALSE_POSITIVE,
                note = "误报",
                createdAt = now - 3600000,
                source = "app",
                sensitivityAdjustment = "decrease"
            ),
            GuardianFeedbackRecord(
                feedbackId = "fb_2",
                eventId = 2,
                guardianName = "张叔叔",
                action = GuardianFeedbackAction.FALSE_POSITIVE,
                note = "误报",
                createdAt = now - 7200000,
                source = "app",
                sensitivityAdjustment = "decrease"
            )
        )

        val result = GuardianNotificationService.shouldNotifyGuardian(
            event = defaultEvent,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = defaultSettings,
            notificationHistory = emptyList(),
            feedbackHistory = falsePositiveHistory,
            recentEvents = emptyList()
        )

        assertThat(result.strategyTags).contains("false_positive_frequency_limit")
    }

    // 测试11: 特殊关怀模式可以生成状态偏离等级
    @Test
    fun specialCareService_returnsDeviationLevel() {
        val event = createEvent(riskScore = 0.6f)

        val result = SpecialCareService.getSpecialCareDeviationLevel(
            event = event,
            history = emptyList(),
            feedbackHistory = emptyList()
        )

        assertThat(result.deviationLevel).isNotNull()
        assertThat(result.displayText).isNotEmpty()
    }

    // 测试12: 多指标连续偏离时进入建议照护确认
    @Test
    fun specialCareService_whenMultiMetricDeviation_returnsCareConfirmation() {
        val currentEvent = createEvent(
            riskScore = 0.75f,
            heartRateDeviation = 30f,
            breathRateDeviation = 28f,
            typingSpeedDeviation = 40f
        )

        val history = listOf(
            createEvent(
                id = 2,
                riskScore = 0.65f,
                startTimeOffset = -7200000
            )
        )

        val result = SpecialCareService.getSpecialCareDeviationLevel(
            event = currentEvent,
            history = history,
            feedbackHistory = emptyList()
        )

        assertThat(result.deviationLevel).isAtLeast(SpecialCareDeviationLevel.OBSERVE_NEEDED)
    }

    // 测试13: 特殊关怀模式下运动状态自动降级
    @Test
    fun specialCareService_whenHighMotion_doesNotEscalateToFocus() {
        val currentEvent = createEvent(
            riskScore = 0.75f,
            heartRateDeviation = 30f,
            motionLevel = 0.7f
        )

        val result = SpecialCareService.getSpecialCareDeviationLevel(
            event = currentEvent,
            history = emptyList(),
            feedbackHistory = emptyList()
        )

        assertThat(result.deviationLevel).isNotEqualTo(SpecialCareDeviationLevel.FOCUS_ATTENTION)
    }

    // 测试14: 照护闭环状态可以正确更新
    @Test
    fun specialCareService_updateCareLoop_transitionsCorrectly() {
        val event = createEvent()
        val deviationResult = SpecialCareDeviationResult(
            deviationLevel = SpecialCareDeviationLevel.OBSERVE_NEEDED,
            displayText = "需要观察",
            reasons = listOf("测试原因"),
            recommendedAction = "继续观察",
            notifyGuardianRecommended = false
        )

        // Test OPEN -> NOTIFIED
        val notifiedRecord = SpecialCareService.updateCareLoop(
            current = null,
            eventId = event.id,
            deviation = deviationResult,
            notificationId = "notif_123",
            feedback = null
        )
        assertThat(notifiedRecord.status).isEqualTo(CareLoopStatus.NOTIFIED)

        // Test NOTIFIED -> ACKNOWLEDGED
        val feedback = GuardianFeedbackRecord(
            feedbackId = "fb_1",
            eventId = event.id,
            guardianName = "张叔叔",
            action = GuardianFeedbackAction.INCREASE_PRIORITY,
            note = "确认",
            createdAt = System.currentTimeMillis(),
            source = "app",
            sensitivityAdjustment = "increase"
        )
        val acknowledgedRecord = SpecialCareService.updateCareLoop(
            current = notifiedRecord,
            eventId = event.id,
            deviation = deviationResult,
            notificationId = "notif_123",
            feedback = feedback
        )
        assertThat(acknowledgedRecord.status).isEqualTo(CareLoopStatus.ACKNOWLEDGED)

        // Test NOTIFIED -> WATCHING
        val watchingFeedback = GuardianFeedbackRecord(
            feedbackId = "fb_2",
            eventId = event.id,
            guardianName = "张叔叔",
            action = GuardianFeedbackAction.KEEP_WATCHING,
            note = "继续观察",
            createdAt = System.currentTimeMillis(),
            source = "app",
            sensitivityAdjustment = "unchanged"
        )
        val watchingRecord = SpecialCareService.updateCareLoop(
            current = notifiedRecord,
            eventId = event.id,
            deviation = deviationResult,
            notificationId = "notif_123",
            feedback = watchingFeedback
        )
        assertThat(watchingRecord.status).isEqualTo(CareLoopStatus.WATCHING)

        // Test NOTIFIED -> FALSE_POSITIVE
        val falsePositiveFeedback = GuardianFeedbackRecord(
            feedbackId = "fb_3",
            eventId = event.id,
            guardianName = "张叔叔",
            action = GuardianFeedbackAction.FALSE_POSITIVE,
            note = "误报",
            createdAt = System.currentTimeMillis(),
            source = "app",
            sensitivityAdjustment = "decrease"
        )
        val falsePositiveRecord = SpecialCareService.updateCareLoop(
            current = notifiedRecord,
            eventId = event.id,
            deviation = deviationResult,
            notificationId = "notif_123",
            feedback = falsePositiveFeedback
        )
        assertThat(falsePositiveRecord.status).isEqualTo(CareLoopStatus.FALSE_POSITIVE)

        // Test WATCHING -> RESOLVED
        val resolvedFeedback = GuardianFeedbackRecord(
            feedbackId = "fb_4",
            eventId = event.id,
            guardianName = "张叔叔",
            action = GuardianFeedbackAction.CONTACTED_USER,
            note = "已联系",
            createdAt = System.currentTimeMillis(),
            source = "app",
            sensitivityAdjustment = "unchanged"
        )
        val resolvedRecord = SpecialCareService.updateCareLoop(
            current = watchingRecord,
            eventId = event.id,
            deviation = deviationResult,
            notificationId = "notif_123",
            feedback = resolvedFeedback
        )
        assertThat(resolvedRecord.status).isEqualTo(CareLoopStatus.RESOLVED)
    }

    private fun createEvent(
        id: Long,
        riskScore: Float,
        riskLevel: String = "medium",
        heartRateDeviation: Float = 0f,
        breathRateDeviation: Float = 0f,
        typingSpeedDeviation: Float = 0f,
        deleteRateDeviation: Float = 0f,
        pauseDurationDeviation: Float = 0f,
        motionLevel: Float = 0.3f,
        timeSegment: String = "morning",
        startTimeOffset: Long = -3600000
    ): RiskEventEntity {
        return RiskEventEntity(
            id = id,
            startTime = System.currentTimeMillis() + startTimeOffset,
            endTime = System.currentTimeMillis() + startTimeOffset + 1800000,
            riskScore = riskScore,
            riskLevel = riskLevel,
            confidence = 0.8f,
            mainReasons = "测试事件",
            metricDeviationPercent = "{}",
            heartRateDeviationPercent = heartRateDeviation,
            breathRateDeviationPercent = breathRateDeviation,
            typingSpeedDeviationPercent = typingSpeedDeviation,
            deleteRateDeviationPercent = deleteRateDeviation,
            pauseDurationDeviationPercent = pauseDurationDeviation,
            motionLevel = motionLevel,
            weather = "sunny",
            timeSegment = timeSegment,
            agentAnalysis = "{}",
            suggestedAction = "观察",
            guardianNotified = false,
            guardianFeedback = null,
            isFalseAlarm = false,
            createdAt = System.currentTimeMillis() + startTimeOffset
        )
    }
}
