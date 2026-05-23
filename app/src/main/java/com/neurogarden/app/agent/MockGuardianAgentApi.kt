package com.neurogarden.app.agent

class MockGuardianAgentApi : GuardianAgentApi {
    override suspend fun analyzeSignals(request: AgentSignalRequest): AgentSignalResponse {
        val shouldNotify = request.latestRiskScore >= request.currentThresholds.guardianNotifyThreshold
        val riskLevel = when {
            request.latestRiskScore >= 0.86f -> "urgent_support"
            request.latestRiskScore >= 0.72f -> "guardian_check"
            request.latestRiskScore >= 0.55f -> "support"
            request.latestRiskScore >= 0.35f -> "observe"
            else -> "stable"
        }
        return AgentSignalResponse(
            riskLevel = riskLevel,
            suggestedAction = actionFor(riskLevel),
            careMessage = messageFor(riskLevel),
            shouldNotifyGuardian = shouldNotify,
            thresholdAdjustments = thresholdAdjustmentsFor(request),
            confidence = if (request.currentBaseline.confidenceLevel == "high") 0.86f else 0.68f,
            reason = "Mock Agent 基于近期特征、个人基线和当前阈值给出本地建议"
        )
    }

    override suspend fun tuneThresholds(request: ThresholdTuningRequest): ThresholdTuningResponse {
        val multiplier = when {
            request.falseAlarmCount >= 3 -> 1.12f
            request.helpfulFeedbackCount >= 3 -> 0.94f
            else -> 1f
        }
        return ThresholdTuningResponse(
            adjustedThresholds = mapOf(
                "heartRateDeltaWarning" to request.currentThresholds.heartRateDeltaWarning * multiplier,
                "deleteRateWarning" to request.currentThresholds.deleteRateWarning * multiplier,
                "pauseDurationWarning" to request.currentThresholds.pauseDurationWarning * multiplier
            ),
            reason = "根据近期误报/有帮助反馈微调提醒敏感度",
            confidence = 0.72f
        )
    }

    override suspend fun generateCareMessage(request: CareMessageRequest): CareMessageResponse {
        val message = messageFor(request.riskLevel)
        return CareMessageResponse(message = message, suggestedAction = actionFor(request.riskLevel))
    }

    override suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse {
        val text = request.latestUserMessage
        val riskLevel = when {
            text.contains("不安全") || text.contains("撑不住") || text.contains("崩溃") -> "guardian_check"
            text.contains("还好") || text.contains("没事") || text.contains("安全") -> "observe"
            text.contains("难受") || text.contains("累") || text.contains("低落") -> "support"
            else -> request.currentRiskLevel
        }
        return SupportConversationResponse(
            reply = gentleQuestionFor(riskLevel, request),
            riskLevel = riskLevel,
            suggestedAction = actionFor(riskLevel),
            shouldNotifyGuardian = riskLevel == "guardian_check" || riskLevel == "urgent_support",
            confidence = 0.68f,
            reason = "Mock Agent 根据用户主动回复进行温和追问和风险复核"
        )
    }

    private fun thresholdAdjustmentsFor(request: AgentSignalRequest): Map<String, Float> {
        if (request.userFeedback == "误报了") {
            return mapOf("guardianNotifyThreshold" to (request.currentThresholds.guardianNotifyThreshold + 0.03f))
        }
        if (request.userFeedback == "我需要陪伴") {
            return mapOf("guardianNotifyThreshold" to (request.currentThresholds.guardianNotifyThreshold - 0.04f))
        }
        return emptyMap()
    }

    private fun actionFor(riskLevel: String): String = when (riskLevel) {
        "urgent_support" -> "立即确认安全，并建议联系守护人"
        "guardian_check" -> "进入关怀页面，并询问是否通知守护人"
        "support" -> "开始呼吸引导和 AI 陪伴"
        "observe" -> "温和提醒用户暂停一下"
        else -> "保持当前节奏"
    }

    private fun messageFor(riskLevel: String): String = when (riskLevel) {
        "urgent_support" -> "我注意到你的状态偏离比较明显。先确认一下：你现在安全吗？我们可以一起慢慢把呼吸放下来。"
        "guardian_check" -> "你的身体节奏有些绷紧。先不用解释原因，跟着我做三次慢呼吸。"
        "support" -> "你可能正在承受一些压力。把注意力放到下一次呼气上，我会陪你把节奏放慢。"
        "observe" -> "检测到轻微状态偏离。可以停十秒，看看肩膀和呼吸有没有变紧。"
        else -> "当前状态较稳定，继续保持舒服的节奏。"
    }

    private fun gentleQuestionFor(riskLevel: String, request: SupportConversationRequest): String {
        val context = request.recentActivity?.takeIf { it.isNotBlank() }
            ?: request.recentRiskContext?.takeIf { it.isNotBlank() }
        val trigger = context
            ?.substringAfter("线索：", context)
            ?.substringBefore("。这些线索")
            ?.take(72)
            ?.takeIf { it.isNotBlank() }
        val prefix = trigger?.let { "刚才的提醒大概和${it}有关，但这不代表我知道你具体经历了什么。" }.orEmpty()
        val response = when (riskLevel) {
            "guardian_check", "urgent_support" -> "谢谢你告诉我。我们先不急着讲完整发生了什么，你现在身边有可以联系的人吗？"
            "support" -> "听起来这一阵确实不轻松。你可以只选一个词：更像累、烦、慌，还是空落落？"
            "observe" -> "收到。那我们先把范围缩小一点：现在身体哪里最明显，肩膀、胸口，还是胃部？"
            else -> "我在。你可以简单说说此刻的感觉，或者只发一个词。"
        }
        return listOf(prefix, response).filter { it.isNotBlank() }.joinToString("")
    }
}
