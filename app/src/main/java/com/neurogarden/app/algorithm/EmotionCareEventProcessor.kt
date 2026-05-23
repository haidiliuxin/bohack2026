package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.guardian.*

/**
 * 统一情绪事件处理结果
 */
data class EmotionCareProcessResult(
    val mode: CareMode,
    val riskLevel: String,
    val riskScore: Float,
    val deviationLevel: SpecialCareDeviationLevel?,
    val localInterventionRecommended: Boolean,
    val localInterventionText: String?,
    val guardianNotificationRecommended: Boolean,
    val guardianNotificationCreated: GuardianNotificationRecord?,
    val careLoopStatus: CareLoopStatus?,
    val normalLoopStatus: NormalCareLoopStatus?,
    val strategyTags: List<String>,
    val reasons: List<String>,
    val nextActionText: String,
    val isMockNotification: Boolean = true
)

/**
 * 普通模式闭环状态
 */
enum class NormalCareLoopStatus(val displayName: String) {
    DETECTED("已检测"),
    SUGGESTED("已建议"),
    FEEDBACK_RECEIVED("已反馈"),
    DISMISSED("已忽略"),
    COMPLETED("已完成")
}

/**
 * 普通模式用户反馈动作
 */
enum class NormalFeedbackAction(val displayName: String, val adjustment: String) {
    ACCURATE("感觉准确", "unchanged"),
    FALSE_POSITIVE("这是误报", "decrease"),
    FINE("我现在没事", "unchanged"),
    RELAX("需要放松", "unchanged"),
    REMIND_LATER("稍后提醒", "unchanged")
}

/**
 * 普通模式反馈记录
 */
data class LocalInterventionFeedbackRecord(
    val feedbackId: String,
    val eventId: Long,
    val action: NormalFeedbackAction,
    val note: String,
    val createdAt: Long,
    val sensitivityAdjustment: String,
    val nextReminderAt: Long? = null
)

/**
 * 统一情绪事件处理器
 * 处理三种模式的事件闭环：普通模式、家庭守护模式、特殊关怀模式
 */
object EmotionCareEventProcessor {

    /**
     * 处理情绪事件
     * @param event 风险事件
     * @param currentMode 当前模式
     * @param guardianSettings 监护人设置
     * @param notificationHistory 通知历史
     * @param feedbackHistory 反馈历史
     * @param recentEvents 最近事件
     * @param userBaseline 用户基线
     */
    fun processEmotionEvent(
        event: RiskEventEntity,
        currentMode: CareMode,
        guardianSettings: GuardianSettingsSnapshot?,
        notificationHistory: List<GuardianNotificationRecord>,
        feedbackHistory: List<GuardianFeedbackRecord>,
        recentEvents: List<RiskEventEntity>,
        userBaseline: com.neurogarden.app.data.local.UserHabitBaselineEntity? = null
    ): EmotionCareProcessResult {
        val now = System.currentTimeMillis()
        val strategyTags = mutableListOf<String>()
        val reasons = mutableListOf<String>()

        // 1. 根据模式决定处理逻辑
        when (currentMode) {
            CareMode.SELF_MONITORING -> {
                // 普通模式：本地处理，不通知监护人
                return processNormalMode(event, strategyTags, reasons)
            }

            CareMode.FAMILY_GUARDIAN -> {
                // 家庭守护模式：需要检查监护人设置和授权
                return processFamilyGuardianMode(
                    event = event,
                    guardianSettings = guardianSettings,
                    notificationHistory = notificationHistory,
                    feedbackHistory = feedbackHistory,
                    recentEvents = recentEvents,
                    strategyTags = strategyTags,
                    reasons = reasons
                )
            }

            CareMode.SPECIAL_CARE -> {
                // 特殊关怀模式：更早关注连续偏离
                return processSpecialCareMode(
                    event = event,
                    guardianSettings = guardianSettings,
                    notificationHistory = notificationHistory,
                    feedbackHistory = feedbackHistory,
                    recentEvents = recentEvents,
                    userBaseline = userBaseline,
                    strategyTags = strategyTags,
                    reasons = reasons
                )
            }
        }
    }

    private fun processNormalMode(
        event: RiskEventEntity,
        strategyTags: MutableList<String>,
        reasons: MutableList<String>
    ): EmotionCareProcessResult {
        val riskScore = event.riskScore
        val riskLevel = event.riskLevel

        // 普通模式根据风险程度给出温和建议
        val (interventionText, nextAction) = when {
            riskScore >= 0.78f -> {
                reasons.add("检测到明显状态偏离")
                "建议暂时放下手头工作，进行几次深呼吸放松。" to "查看详情"
            }
            riskScore >= 0.52f -> {
                reasons.add("检测到中等状态变化")
                "可以尝试简短休息或活动一下。" to "我知道了"
            }
            riskScore >= 0.30f -> {
                reasons.add("检测到轻微波动")
                "继续保持当前状态即可。" to "好的"
            }
            else -> {
                reasons.add("状态平稳")
                "当前状态良好，继续保持。" to "确定"
            }
        }

        strategyTags.add("normal_mode")

        return EmotionCareProcessResult(
            mode = CareMode.SELF_MONITORING,
            riskLevel = riskLevel,
            riskScore = riskScore,
            deviationLevel = null,
            localInterventionRecommended = riskScore >= 0.30f,
            localInterventionText = interventionText,
            guardianNotificationRecommended = false,
            guardianNotificationCreated = null,
            careLoopStatus = null,
            normalLoopStatus = NormalCareLoopStatus.DETECTED,
            strategyTags = strategyTags.toList(),
            reasons = reasons.toList(),
            nextActionText = nextAction,
            isMockNotification = false
        )
    }

    private fun processFamilyGuardianMode(
        event: RiskEventEntity,
        guardianSettings: GuardianSettingsSnapshot?,
        notificationHistory: List<GuardianNotificationRecord>,
        feedbackHistory: List<GuardianFeedbackRecord>,
        recentEvents: List<RiskEventEntity>,
        strategyTags: MutableList<String>,
        reasons: MutableList<String>
    ): EmotionCareProcessResult {
        // 检查监护人设置是否完整
        val settingsComplete = guardianSettings != null &&
            guardianSettings.guardianName.isNotBlank() &&
            guardianSettings.notificationEnabled

        if (!settingsComplete) {
            reasons.add(if (guardianSettings == null) "未配置监护人" else "监护人通知未开启")
            strategyTags.add("guardian_not_configured")

            return EmotionCareProcessResult(
                mode = CareMode.FAMILY_GUARDIAN,
                riskLevel = event.riskLevel,
                riskScore = event.riskScore,
                deviationLevel = null,
                localInterventionRecommended = true,
                localInterventionText = "请先在家庭守护设置中配置监护人信息",
                guardianNotificationRecommended = false,
                guardianNotificationCreated = null,
                careLoopStatus = CareLoopStatus.OPEN,
                normalLoopStatus = null,
                strategyTags = strategyTags.toList(),
                reasons = reasons.toList(),
                nextActionText = "去设置",
                isMockNotification = true
            )
        }

        // 使用已有的 GuardianNotificationService 判断策略
        val strategyResult = GuardianNotificationService.shouldNotifyGuardian(
            event = event,
            contextMode = CareMode.FAMILY_GUARDIAN,
            guardianSettings = guardianSettings,
            notificationHistory = notificationHistory,
            feedbackHistory = feedbackHistory,
            recentEvents = recentEvents
        )

        strategyTags.addAll(strategyResult.strategyTags)

        // 生成模拟通知
        val notification = if (strategyResult.shouldNotify) {
            GuardianNotificationService.createMockNotification(
                event = event,
                settings = guardianSettings,
                strategy = strategyResult,
                deviationLevel = null
            )
        } else {
            null
        }

        reasons.add(strategyResult.reason)

        return EmotionCareProcessResult(
            mode = CareMode.FAMILY_GUARDIAN,
            riskLevel = event.riskLevel,
            riskScore = event.riskScore,
            deviationLevel = null,
            localInterventionRecommended = true,
            localInterventionText = if (strategyResult.shouldNotify) "已通知监护人" else "本地记录",
            guardianNotificationRecommended = strategyResult.shouldNotify,
            guardianNotificationCreated = notification,
            careLoopStatus = if (notification != null) CareLoopStatus.NOTIFIED else CareLoopStatus.OPEN,
            normalLoopStatus = null,
            strategyTags = strategyTags.toList(),
            reasons = reasons.toList(),
            nextActionText = if (strategyResult.shouldNotify) "查看通知记录" else "我知道了",
            isMockNotification = true
        )
    }

    private fun processSpecialCareMode(
        event: RiskEventEntity,
        guardianSettings: GuardianSettingsSnapshot?,
        notificationHistory: List<GuardianNotificationRecord>,
        feedbackHistory: List<GuardianFeedbackRecord>,
        recentEvents: List<RiskEventEntity>,
        userBaseline: com.neurogarden.app.data.local.UserHabitBaselineEntity?,
        strategyTags: MutableList<String>,
        reasons: MutableList<String>
    ): EmotionCareProcessResult {
        // 首先计算特殊关怀偏离等级
        val deviationResult = SpecialCareService.getSpecialCareDeviationLevel(
            event = event,
            history = recentEvents,
            feedbackHistory = feedbackHistory
        )

        strategyTags.add("special_care")
        reasons.addAll(deviationResult.reasons)

        // 检查监护人设置
        val settingsComplete = guardianSettings != null &&
            guardianSettings.guardianName.isNotBlank() &&
            guardianSettings.notificationEnabled

        if (!settingsComplete) {
            strategyTags.add("guardian_not_configured")
            return EmotionCareProcessResult(
                mode = CareMode.SPECIAL_CARE,
                riskLevel = event.riskLevel,
                riskScore = event.riskScore,
                deviationLevel = deviationResult.deviationLevel,
                localInterventionRecommended = true,
                localInterventionText = deviationResult.recommendedAction,
                guardianNotificationRecommended = false,
                guardianNotificationCreated = null,
                careLoopStatus = CareLoopStatus.OPEN,
                normalLoopStatus = null,
                strategyTags = strategyTags.toList(),
                reasons = reasons.toList(),
                nextActionText = "查看详情",
                isMockNotification = true
            )
        }

        // 判断是否需要通知监护人
        val shouldNotify = deviationResult.notifyGuardianRecommended &&
            guardianSettings.notificationEnabled &&
            guardianSettings.authorizationStatus == GuardianAuthorizationStatus.AUTHORIZED

        // 获取家庭守护策略结果
        val guardianStrategy = GuardianNotificationService.shouldNotifyGuardian(
            event = event,
            contextMode = CareMode.SPECIAL_CARE,
            guardianSettings = guardianSettings,
            notificationHistory = notificationHistory,
            feedbackHistory = feedbackHistory,
            recentEvents = recentEvents
        )

        strategyTags.addAll(guardianStrategy.strategyTags)

        // 生成模拟通知
        val notification = if (shouldNotify && guardianStrategy.shouldNotify) {
            GuardianNotificationService.createMockNotification(
                event = event,
                settings = guardianSettings,
                strategy = guardianStrategy,
                deviationLevel = deviationResult.deviationLevel
            )
        } else {
            null
        }

        val careLoopStatus = when {
            notification != null -> CareLoopStatus.NOTIFIED
            deviationResult.deviationLevel == SpecialCareDeviationLevel.FOCUS_ATTENTION -> CareLoopStatus.OPEN
            deviationResult.deviationLevel == SpecialCareDeviationLevel.CARE_CONFIRMATION_SUGGESTED -> CareLoopStatus.OPEN
            else -> CareLoopStatus.OPEN
        }

        return EmotionCareProcessResult(
            mode = CareMode.SPECIAL_CARE,
            riskLevel = event.riskLevel,
            riskScore = event.riskScore,
            deviationLevel = deviationResult.deviationLevel,
            localInterventionRecommended = true,
            localInterventionText = deviationResult.recommendedAction,
            guardianNotificationRecommended = shouldNotify && guardianStrategy.shouldNotify,
            guardianNotificationCreated = notification,
            careLoopStatus = careLoopStatus,
            normalLoopStatus = null,
            strategyTags = strategyTags.toList(),
            reasons = reasons.toList(),
            nextActionText = if (shouldNotify && guardianStrategy.shouldNotify) "查看通知记录" else "继续观察",
            isMockNotification = true
        )
    }

    /**
     * 处理普通模式用户反馈
     */
    fun processNormalModeFeedback(
        eventId: Long,
        action: NormalFeedbackAction,
        note: String = ""
    ): LocalInterventionFeedbackRecord {
        val now = System.currentTimeMillis()
        val nextReminderAt = when (action) {
            NormalFeedbackAction.REMIND_LATER -> now + 30 * 60 * 1000 // 30分钟后
            else -> null
        }

        return LocalInterventionFeedbackRecord(
            feedbackId = "local_${now}_$eventId",
            eventId = eventId,
            action = action,
            note = note,
            createdAt = now,
            sensitivityAdjustment = action.adjustment,
            nextReminderAt = nextReminderAt
        )
    }

    /**
     * 处理家庭守护/特殊关怀模式监护人反馈
     */
    fun processGuardianFeedback(
        eventId: Long,
        currentCareLoop: CareLoopRecord?,
        deviationResult: SpecialCareDeviationResult?,
        notificationId: String?,
        action: GuardianFeedbackAction,
        guardianName: String,
        note: String = ""
    ): CareLoopRecord {
        val feedbackRecord = GuardianFeedbackRecord(
            feedbackId = "guardian_${System.currentTimeMillis()}_$eventId",
            eventId = eventId,
            guardianName = guardianName,
            action = action,
            note = note,
            createdAt = System.currentTimeMillis(),
            source = "app",
            sensitivityAdjustment = action.sensitivityAdjustment
        )

        val deviation = deviationResult ?: SpecialCareDeviationResult(
            deviationLevel = SpecialCareDeviationLevel.OBSERVE_NEEDED,
            displayText = "需要观察",
            reasons = listOf("用户反馈"),
            recommendedAction = "根据反馈处理",
            notifyGuardianRecommended = false
        )

        return SpecialCareService.updateCareLoop(
            current = currentCareLoop,
            eventId = eventId,
            deviation = deviation,
            notificationId = notificationId,
            feedback = feedbackRecord
        )
    }
}
