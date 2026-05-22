package com.neurogarden.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neurogarden.app.agent.AgentSignalRequest
import com.neurogarden.app.agent.GuardianAgentApi
import com.neurogarden.app.agent.SupportConversationMessageDto
import com.neurogarden.app.agent.SupportConversationRequest
import com.neurogarden.app.agent.ThresholdTuningRequest
import com.neurogarden.app.agent.toDto
import com.neurogarden.app.algorithm.EmotionalStateEstimate
import com.neurogarden.app.algorithm.EmotionCalibrationEngine
import com.neurogarden.app.algorithm.EmotionalStateEstimator
import com.neurogarden.app.algorithm.HabitLearningEngine
import com.neurogarden.app.algorithm.HabitLearningWindow
import com.neurogarden.app.algorithm.PersonalizedRiskCalculator
import com.neurogarden.app.algorithm.PersonalizedRiskResult
import com.neurogarden.app.algorithm.RiskLevel
import com.neurogarden.app.algorithm.StressCalculator
import com.neurogarden.app.algorithm.TrendAnalyzer
import com.neurogarden.app.algorithm.TrendAssessment
import com.neurogarden.app.data.local.FeedbackRecordEntity
import com.neurogarden.app.data.local.ConversationSummaryEntity
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.repository.HabitRepository
import com.neurogarden.app.sensor.MockScenario
import com.neurogarden.shared.model.SensorPacket
import com.neurogarden.shared.model.StressResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RealtimeUiState(
    val scenario: MockScenario = MockScenario.ANXIOUS,
    val packet: SensorPacket = MockScenario.ANXIOUS.packet,
    val bodyState: String = MockScenario.ANXIOUS.bodyState,
    val result: StressResult = StressCalculator.calculate(MockScenario.ANXIOUS.packet),
    val habitSampleCount: Int = 0,
    val habitConfidenceLevel: String = "low",
    val habitLearningStatus: String = "系统正在学习你的日常节奏",
    val thresholdStatus: String = "当前仍使用默认阈值",
    val personalizedRisk: PersonalizedRiskResult = PersonalizedRiskResult(
        riskScore = 0f,
        riskLevel = RiskLevel.STABLE,
        confidence = 0.54f,
        suggestedAction = "等待更多样本",
        careMessage = "系统正在学习你的日常节奏。",
        guardianTriggerReason = null
    ),
    val emotionalState: EmotionalStateEstimate = EmotionalStateEstimate.learning(),
    val trend: TrendAssessment = TrendAssessment.empty(),
    val feedbackSummaryText: String = "还没有足够反馈来评估提醒准确性",
    val lastUserEmotionLabel: String? = null,
    val supportMessages: List<SupportMessage> = emptyList()
)

data class SupportMessage(
    val fromUser: Boolean,
    val text: String
)

class MainViewModel(
    private val habitRepository: HabitRepository,
    private val guardianAgentApi: GuardianAgentApi
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealtimeUiState())
    val uiState: StateFlow<RealtimeUiState> = _uiState.asStateFlow()

    fun enterScenario(scenario: MockScenario = MockScenario.ANXIOUS) {
        updateScenario(scenario)
    }

    fun nextScenario() {
        updateScenario(_uiState.value.scenario.next())
    }

    fun ingestWearPacket(packet: SensorPacket, bodyState: String = "手表被动采集") {
        val result = StressCalculator.calculate(packet)
        _uiState.value = _uiState.value.copy(
            packet = packet,
            bodyState = bodyState,
            result = result
        )
        recordHabitSample(packet.toHabitSample(result, bodyState), result)
    }

    private fun updateScenario(scenario: MockScenario) {
        val result = StressCalculator.calculate(scenario.packet)
        _uiState.value = RealtimeUiState(
            scenario = scenario,
            packet = scenario.packet,
            bodyState = scenario.bodyState,
            result = result,
            habitSampleCount = _uiState.value.habitSampleCount,
            habitConfidenceLevel = _uiState.value.habitConfidenceLevel,
            habitLearningStatus = _uiState.value.habitLearningStatus,
            thresholdStatus = _uiState.value.thresholdStatus,
            personalizedRisk = _uiState.value.personalizedRisk,
            emotionalState = _uiState.value.emotionalState,
            trend = _uiState.value.trend,
            feedbackSummaryText = _uiState.value.feedbackSummaryText,
            lastUserEmotionLabel = _uiState.value.lastUserEmotionLabel,
            supportMessages = _uiState.value.supportMessages
        )
        recordHabitSample(scenario.toHabitSample(result, System.currentTimeMillis()), result)
    }

    fun beginSupportConversation() {
        val state = _uiState.value
        val opening = when (state.personalizedRisk.riskLevel) {
            RiskLevel.URGENT_SUPPORT,
            RiskLevel.GUARDIAN_CHECK -> "我在这里。你不用解释太多，只想先轻轻确认一下：你现在身边是安全的吗？"
            RiskLevel.SUPPORT -> "我注意到你的节奏有点紧。现在更像是累、烦，还是有点空落落？不想选也没关系。"
            RiskLevel.OBSERVE -> "我们先慢一点。此刻身体哪里最明显，肩膀、胸口，还是胃部？"
            RiskLevel.STABLE -> "我在。你可以简单说一个词，描述现在的感觉。"
        }
        _uiState.value = state.copy(
            supportMessages = if (state.supportMessages.isEmpty()) {
                listOf(SupportMessage(fromUser = false, text = opening))
            } else {
                state.supportMessages
            }
        )
    }

    private fun recordHabitSample(sample: HabitSampleEntity, result: StressResult) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            habitRepository.saveSample(sample)
            val samples = habitRepository.getSamplesSince(
                HabitLearningEngine.windowStart(now, HabitLearningWindow.THIRTY_DAYS)
            )
            val baseline = HabitLearningEngine.buildBaseline(samples, now)
            val thresholds = HabitLearningEngine.buildThresholdProfile(baseline, now)
            habitRepository.saveBaseline(baseline)
            habitRepository.saveThresholdProfile(thresholds)
            val agentResponse = guardianAgentApi.analyzeSignals(
                AgentSignalRequest(
                    userId = "local-demo-user",
                    recentSignals = samples.take(12).map { it.toDto() },
                    currentBaseline = baseline.toDto(),
                    currentThresholds = thresholds.toDto(),
                    latestRiskScore = result.stressScore,
                    latestRiskLevel = result.state.name.lowercase(),
                    userFeedback = sample.userFeedback,
                    userEmotionLabel = _uiState.value.lastUserEmotionLabel
                )
            )
            val personalizedRisk = PersonalizedRiskCalculator.calculate(
                sample = sample,
                baseline = baseline,
                thresholds = thresholds,
                agentResponse = agentResponse,
                recentSamples = samples
            )
            val emotionalState = EmotionCalibrationEngine.calibrate(
                estimate = EmotionalStateEstimator.estimate(sample, baseline, thresholds),
                recentFeedback = habitRepository.getRecentFeedbackRecords(12)
            )
            val trend = TrendAnalyzer.analyze(samples, now)
            val feedbackSummary = habitRepository.getFeedbackAccuracySummary()
            _uiState.value = _uiState.value.copy(
                habitSampleCount = baseline.sampleCount,
                habitConfidenceLevel = baseline.confidenceLevel,
                habitLearningStatus = "已收集 ${baseline.sampleCount} 条样本，个人基线可信度：${baseline.confidenceLevel.toDisplayName()}",
                thresholdStatus = if (HabitLearningEngine.isPersonalizedEnabled(baseline)) {
                    "当前已启用个体化阈值"
                } else {
                    "当前仍使用默认阈值"
                },
                personalizedRisk = personalizedRisk,
                emotionalState = emotionalState,
                trend = trend,
                feedbackSummaryText = feedbackSummary.toDisplayText()
            )
        }
    }

    private fun MockScenario.toHabitSample(result: StressResult, now: Long): HabitSampleEntity {
        val interaction = when (this) {
            MockScenario.CALM -> InteractionFeatures(typingSpeed = 118f, deleteRate = 0.03f, pauseDuration = 1.2f)
            MockScenario.TENSE -> InteractionFeatures(typingSpeed = 92f, deleteRate = 0.09f, pauseDuration = 2.1f)
            MockScenario.ANXIOUS -> InteractionFeatures(typingSpeed = 68f, deleteRate = 0.19f, pauseDuration = 3.8f)
            MockScenario.MOVING -> InteractionFeatures(typingSpeed = 84f, deleteRate = 0.12f, pauseDuration = 2.6f)
        }
        return HabitSampleEntity(
            timestamp = now,
            heartRate = packet.heartRate,
            breathRate = packet.breathRate,
            motionLevel = packet.motionLevel,
            typingSpeed = interaction.typingSpeed,
            deleteRate = interaction.deleteRate,
            pauseDuration = interaction.pauseDuration,
            userFeedback = null,
            contextTag = title,
            riskLevel = result.state.name.lowercase(),
            createdAt = now
        )
    }

    private fun SensorPacket.toHabitSample(result: StressResult, contextTag: String): HabitSampleEntity {
        return HabitSampleEntity(
            timestamp = timestamp,
            heartRate = heartRate,
            breathRate = breathRate,
            motionLevel = motionLevel,
            typingSpeed = 0f,
            deleteRate = 0f,
            pauseDuration = 0f,
            userFeedback = null,
            contextTag = contextTag,
            riskLevel = result.state.name.lowercase(),
            createdAt = System.currentTimeMillis()
        )
    }

    fun submitFeedback(feedback: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val state = _uiState.value
            val sample = state.scenario.toHabitSample(state.result, now).copy(
                userFeedback = feedback,
                riskLevel = state.personalizedRisk.riskLevel.name.lowercase()
            )
            habitRepository.saveSample(sample)
            habitRepository.saveFeedback(
                FeedbackRecordEntity(
                    timestamp = now,
                    predictedRiskLevel = state.personalizedRisk.riskLevel.name.lowercase(),
                    predictedState = state.emotionalState.primaryState,
                    userLabel = feedback.toUserLabel(),
                    timingFeedback = feedback.toTimingFeedback(),
                    helpful = feedback == "这次提醒有帮助" || feedback == "我需要陪伴",
                    source = "care_support",
                    createdAt = now
                )
            )
            val recent = habitRepository.getRecentSamples(30)
            val currentThresholds = habitRepository.getLatestThresholdProfile()
                ?: HabitLearningEngine.defaultThresholdProfile(now)
            val tuning = guardianAgentApi.tuneThresholds(
                ThresholdTuningRequest(
                    currentThresholds = currentThresholds.toDto(),
                    recentSignals = recent.map { it.toDto() },
                    falseAlarmCount = recent.count { it.userFeedback == "误报了" },
                    helpfulFeedbackCount = recent.count { it.userFeedback == "这次提醒有帮助" }
                )
            )
            habitRepository.saveThresholdProfile(currentThresholds.applyAdjustments(tuning.adjustedThresholds, tuning.reason, now))
            val feedbackSummary = habitRepository.getFeedbackAccuracySummary()
            _uiState.value = state.copy(
                personalizedRisk = state.personalizedRisk.copy(
                    careMessage = when (feedback) {
                        "我现在安全" -> "收到，你现在安全最重要。我们继续用慢呼吸把节奏稳住。"
                        "我需要陪伴" -> "我会陪着你，也可以通知你设置的守护联系人。"
                        "误报了" -> "谢谢反馈，我会把这次记入习惯记忆，减少类似误报。"
                        else -> "谢谢反馈，这会帮助我更懂你的节奏。"
                    }
                ),
                feedbackSummaryText = feedbackSummary.toDisplayText()
            )
        }
    }

    fun submitEmotionLabel(label: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val state = _uiState.value
            habitRepository.saveFeedback(
                FeedbackRecordEntity(
                    timestamp = now,
                    predictedRiskLevel = state.personalizedRisk.riskLevel.name.lowercase(),
                    predictedState = state.emotionalState.primaryState,
                    userLabel = label,
                    timingFeedback = "状态标注",
                    helpful = label != "没事",
                    source = "emotion_label",
                    createdAt = now
                )
            )
            val feedbackSummary = habitRepository.getFeedbackAccuracySummary()
            val calibratedState = state.emotionalState.withUserLabel(label)
            _uiState.value = state.copy(
                emotionalState = calibratedState,
                lastUserEmotionLabel = label,
                feedbackSummaryText = feedbackSummary.toDisplayText(),
                personalizedRisk = state.personalizedRisk.copy(
                    careMessage = "收到，你标注的是“$label”。我会把它作为这次判断的校准样本。"
                )
            )
        }
    }

    fun sendSupportReply(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val state = _uiState.value
            val userMessage = SupportMessage(fromUser = true, text = trimmed)
            val pendingMessages = state.supportMessages + userMessage
            _uiState.value = state.copy(supportMessages = pendingMessages)

            val recent = habitRepository.getRecentSamples(12)
            val response = guardianAgentApi.continueSupportConversation(
                SupportConversationRequest(
                    userId = "local-demo-user",
                    currentRiskLevel = state.personalizedRisk.riskLevel.name.lowercase(),
                    currentRiskScore = state.personalizedRisk.riskScore,
                    recentSignals = recent.map { it.toDto() },
                    conversation = pendingMessages.map {
                        SupportConversationMessageDto(
                            role = if (it.fromUser) "user" else "assistant",
                            content = it.text
                        )
                    },
                    latestUserMessage = trimmed,
                    userEmotionLabel = state.lastUserEmotionLabel
                )
            )
            val updatedRisk = state.personalizedRisk.copy(
                riskLevel = response.riskLevel.toRiskLevel(),
                suggestedAction = response.suggestedAction,
                careMessage = response.reply,
                confidence = response.confidence,
                guardianTriggerReason = if (response.shouldNotifyGuardian) response.reason else state.personalizedRisk.guardianTriggerReason
            )
            habitRepository.saveConversationSummary(
                ConversationSummaryEntity(
                    timestamp = System.currentTimeMillis(),
                    riskLevel = response.riskLevel,
                    emotionalLabel = state.lastUserEmotionLabel,
                    summary = summarizeConversation(trimmed, response.reply),
                    suggestedAction = response.suggestedAction,
                    shouldNotifyGuardian = response.shouldNotifyGuardian,
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.value = _uiState.value.copy(
                personalizedRisk = updatedRisk,
                supportMessages = pendingMessages + SupportMessage(fromUser = false, text = response.reply)
            )
        }
    }

    fun clearHabitMemory() {
        viewModelScope.launch {
            habitRepository.clearHabitMemory()
            _uiState.value = RealtimeUiState(
                supportMessages = listOf(
                    SupportMessage(
                        fromUser = false,
                        text = "已清除本地习惯记忆。系统会重新学习你的日常节奏。"
                    )
                )
            )
        }
    }

    private fun ThresholdProfileEntity.applyAdjustments(
        adjustments: Map<String, Float>,
        reason: String,
        now: Long
    ): ThresholdProfileEntity =
        copy(
            id = 0,
            heartRateDeltaWarning = adjustments["heartRateDeltaWarning"] ?: heartRateDeltaWarning,
            deleteRateWarning = adjustments["deleteRateWarning"] ?: deleteRateWarning,
            pauseDurationWarning = adjustments["pauseDurationWarning"] ?: pauseDurationWarning,
            guardianNotifyThreshold = adjustments["guardianNotifyThreshold"] ?: guardianNotifyThreshold,
            updatedBy = "mock_agent",
            updatedReason = reason,
            updatedAt = now
        )

    private data class InteractionFeatures(
        val typingSpeed: Float,
        val deleteRate: Float,
        val pauseDuration: Float
    )

    private fun String.toDisplayName(): String = when (this) {
        "high" -> "高"
        "medium" -> "中"
        else -> "低"
    }

    private fun com.neurogarden.app.data.repository.FeedbackAccuracySummary.toDisplayText(): String =
        if (total == 0) {
            "还没有足够反馈来评估提醒准确性"
        } else {
            "提醒反馈：$helpful/$total 次有帮助，有效率 ${"%.0f".format(helpfulRate * 100)}%"
        }

    private fun String.toUserLabel(): String = when (this) {
        "我现在安全" -> "状态可控"
        "我需要陪伴" -> "需要陪伴"
        "误报了" -> "误报"
        "这次提醒有帮助" -> "提醒有效"
        else -> this
    }

    private fun String.toTimingFeedback(): String = when (this) {
        "误报了" -> "太早"
        "这次提醒有帮助" -> "刚好"
        "我需要陪伴" -> "及时"
        else -> "未标注"
    }

    private fun String.toRiskLevel(): RiskLevel = when (this) {
        "urgent_support" -> RiskLevel.URGENT_SUPPORT
        "guardian_check" -> RiskLevel.GUARDIAN_CHECK
        "support" -> RiskLevel.SUPPORT
        "observe" -> RiskLevel.OBSERVE
        else -> RiskLevel.STABLE
    }

    private fun EmotionalStateEstimate.withUserLabel(label: String): EmotionalStateEstimate {
        val mapped = when (label) {
            "累" -> "疲惫恢复慢"
            "烦" -> "高压紧张"
            "低落" -> "低落疲惫"
            "紧张" -> "焦虑紧绷"
            "没事" -> "相对稳定"
            else -> primaryState
        }
        return copy(
            primaryState = mapped,
            confidence = (confidence + 0.12f).coerceAtMost(0.92f),
            explanation = "用户主动标注为“$label”，系统已将本次判断作为校准样本。"
        )
    }

    private fun summarizeConversation(userMessage: String, assistantReply: String): String {
        val userTone = when {
            userMessage.contains("累") -> "用户表达疲惫"
            userMessage.contains("烦") -> "用户表达烦躁"
            userMessage.contains("低落") || userMessage.contains("难受") -> "用户表达低落"
            userMessage.contains("紧张") || userMessage.contains("慌") -> "用户表达紧张"
            userMessage.contains("没事") || userMessage.contains("还好") -> "用户表示状态可控"
            else -> "用户主动进行了简短回应"
        }
        val action = when {
            assistantReply.contains("守护") || assistantReply.contains("联系") -> "建议确认守护支持"
            assistantReply.contains("呼吸") -> "建议呼吸引导"
            else -> "继续轻柔陪伴"
        }
        return "$userTone；$action。"
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val guardianAgentApi: GuardianAgentApi
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(habitRepository, guardianAgentApi) as T
    }
}
