package com.neurogarden.app.guardian

enum class GuardianAuthorizationStatus(val displayName: String) {
    NOT_AUTHORIZED("未授权"),
    PENDING("待确认"),
    AUTHORIZED("已授权"),
    REVOKED("已撤销")
}

enum class GuardianNotificationChannel(val displayName: String) {
    APP("App"),
    SMS("短信"),
    WECHAT("微信"),
    EMAIL("邮箱")
}

enum class GuardianNotificationStatus(val displayName: String) {
    PENDING("待发送"),
    SENT("已模拟发送"),
    FAILED("发送失败"),
    SKIPPED("已跳过")
}

enum class GuardianPriority(val displayName: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    URGENT("重点")
}

enum class GuardianFeedbackAction(
    val displayName: String,
    val sensitivityAdjustment: String
) {
    CONTACTED_USER("已联系本人", "unchanged"),
    KEEP_WATCHING("继续观察", "unchanged"),
    FALSE_POSITIVE("标记误报", "decrease"),
    INCREASE_PRIORITY("提高优先级", "increase"),
    DECREASE_PRIORITY("降低优先级", "decrease"),
    REMIND_LATER("需要稍后再次提醒", "unchanged"),
    MUTE_THIS_EVENT("不再提醒本次事件", "decrease")
}

enum class SpecialCareDeviationLevel(val displayName: String) {
    SLIGHT_DEVIATION("轻微偏离"),
    OBSERVE_NEEDED("需要观察"),
    CARE_CONFIRMATION_SUGGESTED("建议照护确认"),
    FOCUS_ATTENTION("重点关注")
}

enum class CareLoopStatus(val displayName: String) {
    OPEN("待观察"),
    NOTIFIED("已提醒"),
    ACKNOWLEDGED("已确认"),
    WATCHING("继续观察"),
    RESOLVED("已处理"),
    FALSE_POSITIVE("误报")
}

data class GuardianSettingsSnapshot(
    val guardianName: String,
    val relationship: String,
    val phone: String,
    val wechat: String,
    val email: String,
    val notificationEnabled: Boolean,
    val notificationChannels: List<GuardianNotificationChannel>,
    val notificationStart: String,
    val notificationEnd: String,
    val allowNightEmergency: Boolean,
    val emergencyNote: String,
    val authorizationStatus: GuardianAuthorizationStatus,
    val specialCareEnabled: Boolean,
    val notifyThreshold: Float
)

data class GuardianNotificationStrategyResult(
    val shouldNotify: Boolean,
    val reason: String,
    val priority: GuardianPriority,
    val channels: List<GuardianNotificationChannel>,
    val cooldownApplied: Boolean,
    val strategyTags: List<String>
)

data class GuardianNotificationRecord(
    val notificationId: String,
    val eventId: Long,
    val guardianName: String,
    val relationship: String,
    val channels: List<GuardianNotificationChannel>,
    val status: GuardianNotificationStatus,
    val reason: String,
    val riskLevel: String,
    val deviationLevel: SpecialCareDeviationLevel?,
    val sentAt: Long,
    val cooldownApplied: Boolean,
    val strategyTags: List<String>,
    val messagePreview: String
)

data class GuardianFeedbackRecord(
    val feedbackId: String,
    val eventId: Long,
    val guardianName: String,
    val action: GuardianFeedbackAction,
    val note: String,
    val createdAt: Long,
    val source: String,
    val sensitivityAdjustment: String,
    val nextReminderAt: Long? = null
)

data class SpecialCareDeviationResult(
    val deviationLevel: SpecialCareDeviationLevel,
    val displayText: String,
    val reasons: List<String>,
    val recommendedAction: String,
    val notifyGuardianRecommended: Boolean
)

data class CareLoopRecord(
    val eventId: Long,
    val enabled: Boolean,
    val deviationLevel: SpecialCareDeviationLevel,
    val recommendedAction: String,
    val notifyGuardianRecommended: Boolean,
    val notificationId: String?,
    val feedbackId: String?,
    val status: CareLoopStatus,
    val createdAt: Long,
    val updatedAt: Long
)
