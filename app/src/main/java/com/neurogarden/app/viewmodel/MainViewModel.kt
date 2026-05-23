package com.neurogarden.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neurogarden.app.BuildConfig
import com.neurogarden.app.agent.AgentSignalRequest
import com.neurogarden.app.agent.AgentSignalResponse
import com.neurogarden.app.agent.AgentPromptVersions
import com.neurogarden.app.agent.ChatTextSanitizer
import com.neurogarden.app.agent.CompanionContextBuilder
import com.neurogarden.app.agent.GuardianAgentApi
import com.neurogarden.app.agent.SupportConversationMessageDto
import com.neurogarden.app.agent.SupportConversationRequest
import com.neurogarden.app.agent.ThresholdTuningRequest
import com.neurogarden.app.agent.toDto
import com.neurogarden.app.algorithm.CareMode
import com.neurogarden.app.algorithm.CareModePolicies
import com.neurogarden.app.algorithm.CareModePolicy
import com.neurogarden.app.algorithm.DailyMonitoringSummary
import com.neurogarden.app.algorithm.DataQualityEvaluator
import com.neurogarden.app.algorithm.DiscomfortBoundaryCalculator
import com.neurogarden.app.algorithm.EmotionalStateEstimate
import com.neurogarden.app.algorithm.EmotionCalibrationEngine
import com.neurogarden.app.algorithm.EmotionalStateEstimator
import com.neurogarden.app.algorithm.FeedbackTuningEngine
import com.neurogarden.app.algorithm.HabitLearningEngine
import com.neurogarden.app.algorithm.HabitLearningWindow
import com.neurogarden.app.algorithm.PersonalizedRiskCalculator
import com.neurogarden.app.algorithm.PersonalizedRiskResult
import com.neurogarden.app.algorithm.RiskLevel
import com.neurogarden.app.algorithm.SignalPreprocessor
import com.neurogarden.app.algorithm.StressCalculator
import com.neurogarden.app.algorithm.TrendAnalyzer
import com.neurogarden.app.algorithm.TrendAssessment
import com.neurogarden.app.data.local.FeedbackRecordEntity
import com.neurogarden.app.data.local.ConversationSummaryEntity
import com.neurogarden.app.data.local.EmotionEvaluationRecordEntity
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.data.local.SensorRecordEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import com.neurogarden.app.data.datastore.CareModeStore
import com.neurogarden.app.data.repository.AgentAuditLogRepository
import com.neurogarden.app.data.repository.HabitRepository
import com.neurogarden.app.data.repository.RiskEventRepository
import com.neurogarden.app.data.repository.TherapyRepository
import com.neurogarden.app.data.repository.WeatherRepository
import com.neurogarden.app.data.repository.WeatherSnapshot
import com.neurogarden.app.sensor.MockScenario
import com.neurogarden.shared.model.SensorPacket
import com.neurogarden.shared.model.StressResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val guardianFeedbackTuningMessage: String? = null,
    val weather: WeatherSnapshot = WeatherSnapshot.mock(),
    val agentConfigured: Boolean = BuildConfig.GUARDIAN_API_KEY.isNotBlank() && BuildConfig.GUARDIAN_API_URL.isNotBlank(),
    val agentModel: String = BuildConfig.GUARDIAN_MODEL,
    val agentApiMode: String = if (BuildConfig.GUARDIAN_API_URL.contains("/anthropic", ignoreCase = true)) {
        "Anthropic compatible"
    } else {
        "OpenAI compatible"
    },
    val guardianRuntimeStatus: GuardianRuntimeStatus = GuardianRuntimeStatus(),
    val integrationDemoAlert: String? = null,
    val supportMessages: List<SupportMessage> = emptyList()
)

data class SupportMessage(
    val fromUser: Boolean,
    val text: String
)

data class DashboardChartData(
    val riskScores: List<Float> = emptyList(),
    val heartRates: List<Float> = emptyList(),
    val breathRates: List<Float> = emptyList(),
    val typingSpeeds: List<Float> = emptyList(),
    val deleteRates: List<Float> = emptyList(),
    val pauseDurations: List<Float> = emptyList()
)

data class GuardianRuntimeStatus(
    val notificationCountToday: Int = 0,
    val notificationMaxDaily: Int = 0,
    val cooldownRemainingMinutes: Int = 0,
    val canNotifyNow: Boolean = false,
    val dataQualityAllowsStrongAlert: Boolean = false,
    val lastAgentStatus: String = "未请求",
    val lastAgentReason: String = "等待结构化数据"
)

class MainViewModel(
    private val habitRepository: HabitRepository,
    private val riskEventRepository: RiskEventRepository,
    private val therapyRepository: TherapyRepository,
    private val weatherRepository: WeatherRepository,
    private val careModeStore: CareModeStore,
    private val agentAuditLogRepository: AgentAuditLogRepository,
    private val guardianAgentApi: GuardianAgentApi
) : ViewModel() {
    private val _uiState = MutableStateFlow(RealtimeUiState())
    private var integrationDemoTick = 0
    val uiState: StateFlow<RealtimeUiState> = _uiState.asStateFlow()
    val todayRiskEvents = riskEventRepository.observeTodayEvents()
    val recentRiskEvents = riskEventRepository.observeRecent7DayEvents()
    val agentAuditLogs = agentAuditLogRepository.recentLogs
    val feedbackRecords = habitRepository.feedbackRecords
    val emotionEvaluations = habitRepository.emotionEvaluations
    val thresholdProfiles = habitRepository.recentThresholdProfiles
    val careMode: StateFlow<CareMode> = careModeStore.currentMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CareMode.SELF_MONITORING
    )
    val careModePolicy: StateFlow<CareModePolicy> = careMode.map { CareModePolicies.policyFor(it) }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CareModePolicies.policyFor(CareMode.SELF_MONITORING)
    )
    private val recentHabitSamples = habitRepository.observeSamplesSince(System.currentTimeMillis() - SEVEN_DAYS_MS)
    private val recentSensorRecords = therapyRepository.observeSensorRecordsSince(System.currentTimeMillis() - SEVEN_DAYS_MS)
    val todaySummary: StateFlow<DailyMonitoringSummary> = combine(
        todayRiskEvents,
        recentHabitSamples,
        recentSensorRecords,
        habitRepository.latestBaseline
    ) { events, samples, sensors, baseline ->
        val todayStart = System.currentTimeMillis().startOfDay()
        buildDailySummary(
            dayStart = todayStart,
            riskEvents = events,
            habitSamples = samples.filter { it.timestamp >= todayStart },
            sensorRecords = sensors.filter { it.timestamp >= todayStart },
            baseline = baseline
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyDailySummary()
    )
    val todayChartData: StateFlow<DashboardChartData> = combine(
        todayRiskEvents,
        recentHabitSamples,
        recentSensorRecords
    ) { events, samples, sensors ->
        val since = System.currentTimeMillis() - DAY_MS
        val todaySamples = samples.filter { it.timestamp >= since }.sortedBy { it.timestamp }
        val todaySensors = sensors.filter { it.timestamp >= since }.sortedBy { it.timestamp }
        val todayEvents = events.filter { it.startTime >= since }.sortedBy { it.startTime }
        DashboardChartData(
            riskScores = (todayEvents.map { it.riskScore } + todaySensors.map { it.stressScore }).takeLast(12),
            heartRates = todaySensors.map { it.heartRate.toFloat() }.ifEmpty { todaySamples.map { it.heartRate.toFloat() } }.filter { it > 0f }.takeLast(12),
            breathRates = todaySensors.map { it.breathRate.toFloat() }.ifEmpty { todaySamples.map { it.breathRate.toFloat() } }.filter { it > 0f }.takeLast(12),
            typingSpeeds = todaySamples.map { it.typingSpeed }.filter { it > 0f }.takeLast(12),
            deleteRates = todaySamples.map { it.deleteRate }.filter { it > 0f }.takeLast(12),
            pauseDurations = todaySamples.map { it.pauseDuration }.filter { it > 0f }.takeLast(12)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardChartData())
    val sevenDaySummaries: StateFlow<List<DailyMonitoringSummary>> = combine(
        recentRiskEvents,
        recentHabitSamples,
        recentSensorRecords,
        habitRepository.latestBaseline
    ) { events, samples, sensors, baseline ->
        val todayStart = System.currentTimeMillis().startOfDay()
        (6 downTo 0).map { offset ->
            val dayStart = todayStart - offset * DAY_MS
            val dayEnd = dayStart + DAY_MS
            buildDailySummary(
                dayStart = dayStart,
                riskEvents = events.filter { it.startTime in dayStart until dayEnd },
                habitSamples = samples.filter { it.timestamp in dayStart until dayEnd },
                sensorRecords = sensors.filter { it.timestamp in dayStart until dayEnd },
                baseline = baseline
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeRiskEvent(id: Long) = riskEventRepository.observeEventById(id)

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weather = weatherRepository.current())
            _uiState.value = _uiState.value.copy(weather = weatherRepository.refresh())
        }
    }

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
            guardianFeedbackTuningMessage = _uiState.value.guardianFeedbackTuningMessage,
            weather = _uiState.value.weather,
            guardianRuntimeStatus = _uiState.value.guardianRuntimeStatus,
            supportMessages = _uiState.value.supportMessages
        )
        recordHabitSample(scenario.toHabitSample(result, System.currentTimeMillis()), result)
    }

    fun beginSupportConversation() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.supportMessages.isNotEmpty()) return@launch
            val recent = habitRepository.getRecentSamples(20)
            val recentEvents = riskEventRepository.getTodayEvents(System.currentTimeMillis()).take(3)
            val feedbacks = habitRepository.getRecentFeedbackRecords(12)
            val summaries = habitRepository.getRecentConversationSummaries(6)
            val activity = CompanionContextBuilder.buildRecentActivity(recent, recentEvents)
            val personality = CompanionContextBuilder.buildPersonalityModel(
                feedbacks = feedbacks,
                summaries = summaries,
                samples = recent,
                lastEmotionLabel = state.lastUserEmotionLabel
            )
            val opening = CompanionContextBuilder.openingFor(
                currentRiskLevel = state.personalizedRisk.riskLevel.name.lowercase(),
                recentActivity = activity,
                personalityModel = personality
            )
            _uiState.value = state.copy(
                supportMessages = listOf(SupportMessage(fromUser = false, text = opening))
            )
        }
    }

    private fun recordHabitSample(sample: HabitSampleEntity, result: StressResult) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            habitRepository.saveSample(sample)
            therapyRepository.saveSensorRecord(
                SensorRecordEntity(
                    timestamp = sample.timestamp,
                    heartRate = sample.heartRate,
                    breathRate = sample.breathRate,
                    motionLevel = sample.motionLevel,
                    stressScore = result.stressScore,
                    confidence = result.confidence,
                    state = result.state.name.lowercase()
                )
            )
            val samples = habitRepository.getSamplesSince(
                HabitLearningEngine.windowStart(now, HabitLearningWindow.THIRTY_DAYS)
            )
            val baseline = HabitLearningEngine.buildBaseline(samples, now)
            val learnedThresholds = HabitLearningEngine.buildThresholdProfile(baseline, now)
            val latestThresholds = habitRepository.getLatestThresholdProfile()
            val baseThresholds = latestThresholds
                ?.takeIf { it.updatedBy == "guardian_feedback" || it.updatedBy == "mock_agent" }
                ?: learnedThresholds
            val policy = careModePolicy.value
            val thresholds = baseThresholds.applyCareModePolicy(policy, now)
            val weather = weatherRepository.current()
            habitRepository.saveBaseline(baseline)
            if (latestThresholds == null || baseThresholds == learnedThresholds) {
                habitRepository.saveThresholdProfile(baseThresholds)
            }
            val cleaned = SignalPreprocessor.preprocess(sample, baseline, thresholds, samples)
            val cleanSample = cleaned.sample
            val requestSamples = (listOf(cleanSample) + samples.filter { it.timestamp != sample.timestamp })
                .sortedByDescending { it.timestamp }
                .take(12)
            val todayEvents = riskEventRepository.getTodayEvents(now)
            val recentFeedback = habitRepository.getRecentFeedbackRecords(12)
            val conversationSummaries = habitRepository.getRecentConversationSummaries(6)
            val recentActivity = CompanionContextBuilder.buildRecentActivity(samples.take(20), todayEvents.take(3))
            val personalityModel = CompanionContextBuilder.buildPersonalityModel(
                feedbacks = recentFeedback,
                summaries = conversationSummaries,
                samples = samples.take(20),
                lastEmotionLabel = _uiState.value.lastUserEmotionLabel
            )
            val agentRequest = AgentSignalRequest(
                userId = "local-demo-user",
                recentSignals = requestSamples.map { it.toDto() },
                currentBaseline = baseline.toDto(),
                currentThresholds = thresholds.toDto(),
                latestRiskScore = result.stressScore,
                latestRiskLevel = result.state.name.lowercase(),
                userFeedback = sample.userFeedback,
                userEmotionLabel = _uiState.value.lastUserEmotionLabel,
                weather = weather.eventLabel(),
                timeSegment = now.timeSegment(),
                personalityModel = personalityModel,
                recentActivity = recentActivity,
                cleanedSignalSummary = cleaned.requestSummary(),
                baselineDeviationPercent = cleaned.deviations,
                dataQuality = cleaned.qualityLevel,
                dataLimits = cleaned.dataLimits,
                localEmotionGuess = cleaned.localEmotionSummary()
            )
            val agentStartedAt = System.currentTimeMillis()
            val agentResponse = guardianAgentApi.analyzeSignals(agentRequest)
            val agentLatencyMs = System.currentTimeMillis() - agentStartedAt
            agentAuditLogRepository.record(
                triggerReason = if (result.stressScore >= 0.35f) "abnormal" else "scheduled",
                response = agentResponse,
                httpSuccess = !agentResponse.isMockFallback(),
                fallbackUsed = agentResponse.isMockFallback(),
                fallbackReason = agentResponse.reason.takeIf { agentResponse.isMockFallback() },
                requestSummary = "signals=${agentRequest.recentSignals.size};risk=${"%.2f".format(agentRequest.latestRiskScore)};level=${agentRequest.latestRiskLevel};weather=${agentRequest.weather ?: "none"};segment=${agentRequest.timeSegment ?: "none"}",
                promptVersion = AgentPromptVersions.SIGNAL_ANALYSIS,
                latencyMs = agentLatencyMs
            )
            val latestQuality = DataQualityEvaluator.evaluate(
                habitSamples = samples.filter { it.timestamp >= now.startOfDay() },
                sensorRecords = emptyList(),
                riskEvents = todayEvents,
                baseline = baseline
            )
            val personalizedRisk = PersonalizedRiskCalculator.calculate(
                sample = cleanSample,
                baseline = baseline,
                thresholds = thresholds,
                agentResponse = agentResponse,
                recentSamples = samples
            )
            val todayGuardianAlerts = todayEvents.count { it.guardianNotified }
            val modeAdjustedRisk = personalizedRisk.applyCareModeNotificationPolicy(policy, todayGuardianAlerts)
            riskEventRepository.recordIfNeeded(
                sample = cleanSample,
                baseline = baseline,
                risk = modeAdjustedRisk,
                agentResponse = agentResponse,
                weather = _uiState.value.weather.eventLabel()
            )
            val localEmotion = EmotionalStateEstimator.estimate(cleanSample, baseline, thresholds)
            val emotionalState = EmotionCalibrationEngine.calibrate(
                estimate = localEmotion.applyAgentEmotion(agentResponse),
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
                personalizedRisk = modeAdjustedRisk,
                emotionalState = emotionalState,
                trend = trend,
                feedbackSummaryText = feedbackSummary.toDisplayText(),
                weather = weather,
                guardianRuntimeStatus = _uiState.value.guardianRuntimeStatus.copy(
                    dataQualityAllowsStrongAlert = latestQuality.qualityLevel != "low",
                    lastAgentStatus = if (agentResponse.reason.contains("Mock", ignoreCase = true)) "Mock fallback" else "MiniMax 已返回",
                    lastAgentReason = agentResponse.reason
                )
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
            habitRepository.saveConversationSummary(
                ConversationSummaryEntity(
                    timestamp = now,
                    riskLevel = state.personalizedRisk.riskLevel.name.lowercase(),
                    emotionalLabel = state.lastUserEmotionLabel,
                    summary = "用户反馈“${feedback.toUserLabel()}”，用于更新提醒接受度和陪伴偏好。",
                    suggestedAction = tuning.reason,
                    shouldNotifyGuardian = feedback == "我需要陪伴",
                    createdAt = now
                )
            )
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

    fun submitGuardianFeedback(eventId: Long, feedback: String) {
        viewModelScope.launch {
            riskEventRepository.updateGuardianFeedback(eventId, feedback)
            val event = riskEventRepository.getEventById(eventId)
            val currentThresholds = habitRepository.getLatestThresholdProfile()
                ?: HabitLearningEngine.defaultThresholdProfile(System.currentTimeMillis())
            val tuning = event?.let {
                FeedbackTuningEngine.tune(
                    event = it,
                    guardianFeedback = feedback,
                    current = currentThresholds
                )
            }
            if (tuning != null) {
                habitRepository.saveThresholdProfile(tuning.updatedProfile)
            }
            val now = System.currentTimeMillis()
            habitRepository.saveFeedback(
                FeedbackRecordEntity(
                    timestamp = now,
                    predictedRiskLevel = event?.riskLevel ?: _uiState.value.personalizedRisk.riskLevel.name.lowercase(),
                    predictedState = _uiState.value.emotionalState.primaryState,
                    userLabel = feedback.toUserLabel(),
                    timingFeedback = feedback.toTimingFeedback(),
                    helpful = feedback != "标记误报",
                    source = "guardian_dashboard",
                    createdAt = now
                )
            )
            _uiState.value = _uiState.value.copy(
                guardianFeedbackTuningMessage = tuning?.message ?: "已记录监护人反馈。"
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
            habitRepository.saveEmotionEvaluation(
                EmotionEvaluationRecordEntity(
                    createdAt = now,
                    predictedPrimaryEmotion = state.emotionalState.primaryState,
                    predictedSecondaryEmotions = state.emotionalState.secondaryStates.joinToString("|"),
                    userCorrectedEmotion = label,
                    confidence = state.emotionalState.confidence,
                    valence = state.emotionalState.valenceScore,
                    arousal = state.emotionalState.arousalScore,
                    stress = state.emotionalState.stressScore,
                    fatigue = state.emotionalState.fatigueScore,
                    loneliness = state.emotionalState.lonelinessScore,
                    signalSummary = "heart=${state.packet.heartRate};breath=${state.packet.breathRate};motion=${"%.2f".format(state.packet.motionLevel)};risk=${"%.2f".format(state.personalizedRisk.riskScore)}",
                    contextSummary = "mode=${careMode.value.name};weather=${state.weather.eventLabel()};lastLabel=${state.lastUserEmotionLabel ?: "none"}",
                    agentVersion = state.agentModel,
                    wasAccepted = label == state.emotionalState.primaryState ||
                        state.emotionalState.primaryState.contains(label) ||
                        label.contains(state.emotionalState.primaryState),
                    notes = "用户主动校准当前情绪标签"
                )
            )
            val feedbackSummary = habitRepository.getFeedbackAccuracySummary()
            val calibratedState = state.emotionalState.withUserLabel(label)
            habitRepository.saveConversationSummary(
                ConversationSummaryEntity(
                    timestamp = now,
                    riskLevel = state.personalizedRisk.riskLevel.name.lowercase(),
                    emotionalLabel = label,
                    summary = "用户主动标注当前感受为“$label”，作为后续人格心理模型的轻量记忆。",
                    suggestedAction = "后续回应优先匹配该情绪下的陪伴风格",
                    shouldNotifyGuardian = false,
                    createdAt = now
                )
            )
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

            val recent = habitRepository.getRecentSamples(20)
            val recentEvents = riskEventRepository.getTodayEvents(System.currentTimeMillis()).take(3)
            val conversationSummaries = habitRepository.getRecentConversationSummaries(6)
            val feedbacks = habitRepository.getRecentFeedbackRecords(12)
            val recentActivity = CompanionContextBuilder.buildRecentActivity(recent, recentEvents)
            val personalityModel = CompanionContextBuilder.buildPersonalityModel(
                feedbacks = feedbacks,
                summaries = conversationSummaries,
                samples = recent,
                lastEmotionLabel = state.lastUserEmotionLabel
            )
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
                    userEmotionLabel = state.lastUserEmotionLabel,
                    recentRiskContext = recentEvents.toSupportRiskContext(),
                    personalityModel = personalityModel,
                    recentActivity = recentActivity
                )
            )
            val assistantReply = ChatTextSanitizer.cleanAssistantReply(response.reply)
            val suggestedAction = ChatTextSanitizer.cleanShortText(response.suggestedAction, "继续温和陪伴")
            val reason = ChatTextSanitizer.cleanShortText(response.reason, "support_conversation")
            val updatedRisk = state.personalizedRisk.copy(
                riskLevel = response.riskLevel.toRiskLevel(),
                suggestedAction = suggestedAction,
                careMessage = assistantReply,
                confidence = response.confidence,
                guardianTriggerReason = if (response.shouldNotifyGuardian) reason else state.personalizedRisk.guardianTriggerReason
            )
            habitRepository.saveConversationSummary(
                ConversationSummaryEntity(
                    timestamp = System.currentTimeMillis(),
                    riskLevel = response.riskLevel,
                    emotionalLabel = state.lastUserEmotionLabel,
                    summary = CompanionContextBuilder.summarizeConversation(trimmed, assistantReply, recentActivity),
                    suggestedAction = suggestedAction,
                    shouldNotifyGuardian = response.shouldNotifyGuardian,
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.value = _uiState.value.copy(
                personalizedRisk = updatedRisk,
                supportMessages = pendingMessages + SupportMessage(fromUser = false, text = assistantReply)
            )
        }
    }

    fun clearHabitMemory() {
        viewModelScope.launch {
            habitRepository.clearHabitMemory()
            riskEventRepository.clearAll()
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

    fun seedDemoMode(mode: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val todayStart = now.startOfDay()
            if (mode == "acceptance_week") {
                seedAcceptanceWeek(todayStart)
                return@launch
            }
            if (mode == "integration_demo") {
                runIntegrationDemo(now)
                return@launch
            }
            val profile = habitRepository.getLatestThresholdProfile()
                ?: HabitLearningEngine.defaultThresholdProfile(now)
            val samples = demoSamples(mode, todayStart)
            samples.forEach { sample ->
                habitRepository.saveSample(sample)
                therapyRepository.saveSensorRecord(
                    SensorRecordEntity(
                        timestamp = sample.timestamp,
                        heartRate = sample.heartRate,
                        breathRate = sample.breathRate,
                        motionLevel = sample.motionLevel,
                        stressScore = when (mode) {
                            "stable_day" -> 0.18f
                            "mild_wave" -> 0.42f
                            else -> 0.72f
                        },
                        confidence = 0.76f,
                        state = sample.riskLevel
                    )
                )
            }
            val event = demoRiskEvent(mode, todayStart)
            if (event != null) {
                val eventId = riskEventRepository.insertEvent(event)
                if (mode == "guardian_confirmed") {
                    val saved = event.copy(id = eventId, guardianFeedback = "确认异常")
                    val tuning = FeedbackTuningEngine.tune(saved, "确认异常", profile)
                    riskEventRepository.updateGuardianFeedback(eventId, "确认异常")
                    habitRepository.saveThresholdProfile(tuning.updatedProfile)
                    _uiState.value = _uiState.value.copy(guardianFeedbackTuningMessage = tuning.message)
                }
            }
        }
    }

    private suspend fun seedAcceptanceWeek(todayStart: Long) {
        val now = System.currentTimeMillis()
        habitRepository.clearHabitMemory()
        riskEventRepository.clearAll()
        therapyRepository.clearSensorRecords()
        habitRepository.saveBaseline(
            UserHabitBaselineEntity(
                avgRestingHeartRate = 72f,
                avgBreathRate = 12.5f,
                avgTypingSpeed = 108f,
                avgDeleteRate = 0.05f,
                avgPauseDuration = 1.6f,
                commonActiveStartHour = 8,
                commonActiveEndHour = 23,
                avgRecoveryDuration = 14f,
                sampleCount = 42,
                confidenceLevel = "high",
                createdAt = todayStart - 6 * DAY_MS,
                updatedAt = now
            )
        )
        habitRepository.saveThresholdProfile(
            ThresholdProfileEntity(
                heartRateDeltaWarning = 18f,
                breathRateWarning = 7f,
                typingSpeedDeltaWarning = 0.30f,
                deleteRateWarning = 0.14f,
                pauseDurationWarning = 3.5f,
                riskTriggerDuration = 15,
                guardianNotifyThreshold = 0.78f,
                updatedBy = "acceptance_seed",
                updatedReason = "multi_day_manual_acceptance",
                updatedAt = now
            )
        )

        val days = listOf(
            AcceptanceDay(6, "stable", "多云 23C 湿度55% 上海 source=Mock"),
            AcceptanceDay(5, "mild_wave", "小雨 21C 湿度78% 上海 source=Real"),
            AcceptanceDay(4, "recovery", "晴 25C 湿度48% 上海 source=Real"),
            AcceptanceDay(3, "night_event", "阴 22C 湿度69% 上海 source=Real"),
            AcceptanceDay(2, "motion_noise", "晴 27C 湿度45% 上海 source=Real"),
            AcceptanceDay(1, "family_check", "多云 24C 湿度61% 上海 source=Real"),
            AcceptanceDay(0, "special_care", "小雨 20C 湿度82% 上海 source=Real")
        )
        days.forEach { day ->
            val dayStart = todayStart - day.daysAgo * DAY_MS
            acceptanceSamples(dayStart, day.type).forEach { sample ->
                habitRepository.saveSample(sample)
                therapyRepository.saveSensorRecord(sample.toSensorRecord())
            }
            acceptanceRiskEvent(dayStart, day.type, day.weather)?.let { event ->
                riskEventRepository.insertEvent(event)
            }
        }

        listOf(
            AcceptanceFeedback(5, 21, "observe", "轻度波动", "有点累", "刚好", true, "emotion_label"),
            AcceptanceFeedback(3, 23, "guardian_check", "低落疲惫", "需要陪伴", "及时", true, "guardian_dashboard"),
            AcceptanceFeedback(2, 13, "observe", "运动干扰", "误报", "太早", false, "guardian_dashboard"),
            AcceptanceFeedback(1, 18, "guardian_check", "高压紧张", "提醒有效", "刚好", true, "guardian_dashboard")
        ).forEach { feedback ->
            val timestamp = todayStart - feedback.daysAgo * DAY_MS + feedback.hour * 60L * 60L * 1000L
            habitRepository.saveFeedback(
                FeedbackRecordEntity(
                    timestamp = timestamp,
                    predictedRiskLevel = feedback.riskLevel,
                    predictedState = feedback.state,
                    userLabel = feedback.label,
                    timingFeedback = feedback.timing,
                    helpful = feedback.helpful,
                    source = feedback.source,
                    createdAt = timestamp
                )
            )
        }

        _uiState.value = _uiState.value.copy(
            habitSampleCount = 42,
            habitConfidenceLevel = "high",
            habitLearningStatus = "已导入 7 天验收样本，个人基线可信度：高",
            thresholdStatus = "当前已启用验收阈值",
            feedbackSummaryText = "提醒反馈：3/4 次有帮助，有效率 75%",
            personalizedRisk = _uiState.value.personalizedRisk.copy(
                riskScore = 0.82f,
                riskLevel = RiskLevel.GUARDIAN_CHECK,
                confidence = 0.86f,
                suggestedAction = "建议查看今日异常事件，并进行一次温和状态确认。",
                careMessage = "我注意到今天的节奏有些偏离。我们可以先慢一点，只确认你现在是否需要陪伴。"
            )
        )
    }

    fun setCareMode(mode: CareMode) {
        viewModelScope.launch {
            careModeStore.setMode(mode)
            val now = System.currentTimeMillis()
            val current = habitRepository.getLatestThresholdProfile()
                ?: HabitLearningEngine.defaultThresholdProfile(now)
            val policy = CareModePolicies.policyFor(mode)
            habitRepository.saveThresholdProfile(
                current.copy(
                    id = 0,
                    guardianNotifyThreshold = policy.notificationThreshold,
                    updatedBy = "care_mode",
                    updatedReason = "care_mode:${mode.name}",
                    updatedAt = now
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

    fun dismissIntegrationDemoAlert() {
        _uiState.value = _uiState.value.copy(integrationDemoAlert = null)
    }

    private suspend fun runIntegrationDemo(now: Long) {
        integrationDemoTick += 1
        val demoNow = now + integrationDemoTick * 60_000L
        val demoRiskScore = listOf(0.72f, 0.84f, 0.66f, 0.91f, 0.78f)[integrationDemoTick % 5]
        val sample = HabitSampleEntity(
            timestamp = demoNow,
            heartRate = 104 + (integrationDemoTick % 4) * 4,
            breathRate = 21 + (integrationDemoTick % 5),
            motionLevel = 0.08f,
            typingSpeed = 52f + (integrationDemoTick % 3) * 12f,
            deleteRate = 0.22f + (integrationDemoTick % 4) * 0.03f,
            pauseDuration = 5.5f + (integrationDemoTick % 5) * 1.4f,
            userFeedback = null,
            contextTag = "integration_demo",
            riskLevel = "observe",
            createdAt = demoNow
        )
        habitRepository.saveSample(sample)
        therapyRepository.saveSensorRecord(
            SensorRecordEntity(
                timestamp = demoNow,
                heartRate = sample.heartRate,
                breathRate = sample.breathRate,
                motionLevel = sample.motionLevel,
                stressScore = demoRiskScore,
                confidence = 0.84f,
                state = "needs_confirmation"
            )
        )
        val samples = habitRepository.getSamplesSince(HabitLearningEngine.windowStart(demoNow, HabitLearningWindow.THIRTY_DAYS))
        val baseline = HabitLearningEngine.buildBaseline(samples, demoNow)
        val thresholds = (habitRepository.getLatestThresholdProfile() ?: HabitLearningEngine.defaultThresholdProfile(demoNow))
            .applyCareModePolicy(careModePolicy.value, demoNow)
        habitRepository.saveBaseline(baseline)
        val request = AgentSignalRequest(
            userId = "local-demo-user",
            recentSignals = samples.take(12).map { it.toDto() },
            currentBaseline = baseline.toDto(),
            currentThresholds = thresholds.toDto(),
            latestRiskScore = demoRiskScore,
            latestRiskLevel = "guardian_check",
            userFeedback = null,
            weather = weatherRepository.current().eventLabel(),
            timeSegment = demoNow.timeSegment()
        )
        val agentStartedAt = System.currentTimeMillis()
        val response = runCatching { guardianAgentApi.analyzeSignals(request) }
            .getOrElse {
                AgentSignalResponse(
                    riskScore = demoRiskScore,
                    riskLevel = "guardian_check",
                    emotionalState = "needs_confirmation",
                    suggestedAction = "建议确认当前状态并查看异常详情。",
                    careMessage = "检测到节律波动，请查看结构化指标。",
                    shouldNotifyGuardian = true,
                    thresholdAdjustments = emptyMap(),
                    confidence = 0.72f,
                    reason = "integration_demo_local_fallback:${it.message}",
                    mainReasons = listOf("心率和呼吸偏离", "输入节奏异常", "停顿时长增加"),
                    metricDeviationPercent = emptyMap()
                )
            }
        val agentLatencyMs = System.currentTimeMillis() - agentStartedAt
        val fallbackUsed = response.isMockFallback() || response.reason.contains("fallback", ignoreCase = true)
        agentAuditLogRepository.record(
            triggerReason = "demo",
            response = response,
            httpSuccess = !fallbackUsed,
            fallbackUsed = fallbackUsed,
            fallbackReason = response.reason.takeIf { fallbackUsed },
            requestSummary = "signals=${request.recentSignals.size};risk=${"%.2f".format(request.latestRiskScore)};level=${request.latestRiskLevel};weather=${request.weather ?: "none"};segment=${request.timeSegment ?: "none"}",
            promptVersion = AgentPromptVersions.SIGNAL_ANALYSIS,
            latencyMs = agentLatencyMs,
            requestTime = demoNow
        )
        val event = RiskEventEntity(
            startTime = demoNow,
            endTime = demoNow + 5L * 60L * 1000L,
            riskScore = response.riskScore ?: demoRiskScore,
            riskLevel = response.riskLevel,
            confidence = response.confidence,
            mainReasons = response.mainReasons.take(3).ifEmpty {
                listOf("心率和呼吸偏离", "输入节奏异常", "停顿时长增加")
            }.joinToString("|"),
            metricDeviationPercent = "heartRate=52.8;breathRate=108.3;typingSpeed=-48.0;deleteRate=460.0;pauseDuration=333.0",
            heartRateDeviationPercent = 52.8f,
            breathRateDeviationPercent = 108.3f,
            typingSpeedDeviationPercent = -48f,
            deleteRateDeviationPercent = 460f,
            pauseDurationDeviationPercent = 333f,
            motionLevel = sample.motionLevel,
            weather = request.weather ?: "unknown",
            timeSegment = request.timeSegment ?: "unknown",
            agentAnalysis = "trigger=demo;state=${response.emotionalState ?: response.riskLevel};reason=${response.reason.take(80)}",
            suggestedAction = response.suggestedAction,
            guardianNotified = response.shouldNotifyGuardian,
            guardianFeedback = null,
            isFalseAlarm = false,
            createdAt = demoNow
        )
        riskEventRepository.insertEvent(event)
        _uiState.value = _uiState.value.copy(
            integrationDemoAlert = "联调演示已完成：已写入异常样本、请求 Agent、生成风险事件并刷新今日摘要。",
            guardianRuntimeStatus = _uiState.value.guardianRuntimeStatus.copy(
                lastAgentStatus = response.emotionalState ?: response.riskLevel,
                lastAgentReason = response.mainReasons.take(3).joinToString("；").ifBlank { response.reason }
            )
        )
    }

    private data class InteractionFeatures(
        val typingSpeed: Float,
        val deleteRate: Float,
        val pauseDuration: Float
    )

    private fun ThresholdProfileEntity.applyCareModePolicy(
        policy: CareModePolicy,
        now: Long
    ): ThresholdProfileEntity {
        val thresholdFactor = (1f / policy.riskSensitivity).coerceIn(0.75f, 1.25f)
        return copy(
            id = 0,
            heartRateDeltaWarning = (heartRateDeltaWarning * thresholdFactor).coerceIn(8f, 42f),
            breathRateWarning = (breathRateWarning * thresholdFactor).coerceIn(12f, 34f),
            typingSpeedDeltaWarning = (typingSpeedDeltaWarning * thresholdFactor).coerceIn(0.12f, 0.85f),
            deleteRateWarning = (deleteRateWarning * thresholdFactor).coerceIn(0.04f, 0.70f),
            pauseDurationWarning = (pauseDurationWarning * thresholdFactor).coerceIn(1f, 14f),
            guardianNotifyThreshold = policy.notificationThreshold,
            updatedBy = "care_mode_runtime",
            updatedReason = "runtime_policy:${policy.mode.name}",
            updatedAt = now
        )
    }

    private fun PersonalizedRiskResult.applyCareModeNotificationPolicy(
        policy: CareModePolicy,
        todayGuardianAlerts: Int
    ): PersonalizedRiskResult {
        val canNotifyGuardian = policy.guardianEnabledByDefault &&
            todayGuardianAlerts < policy.maxDailyGuardianAlerts &&
            riskScore >= policy.notificationThreshold
        return if (canNotifyGuardian) {
            copy(
                guardianTriggerReason = guardianTriggerReason ?: "care_mode_threshold:${policy.mode.name}"
            )
        } else {
            copy(guardianTriggerReason = null)
        }
    }

    private fun AgentSignalResponse.isMockFallback(): Boolean =
        reason.contains("Mock", ignoreCase = true) ||
            reason.contains("fallback", ignoreCase = true) ||
            reason.contains("本地", ignoreCase = true)

    private fun buildDailySummary(
        dayStart: Long,
        riskEvents: List<RiskEventEntity>,
        habitSamples: List<HabitSampleEntity>,
        sensorRecords: List<SensorRecordEntity>,
        baseline: com.neurogarden.app.data.local.UserHabitBaselineEntity?
    ): DailyMonitoringSummary {
        val quality = DataQualityEvaluator.evaluate(habitSamples, sensorRecords, riskEvents, baseline)
        val maxEvent = riskEvents.maxByOrNull { it.riskScore }
        val scores = riskEvents.map { it.riskScore }
        val topMetrics = topContributingMetrics(riskEvents)
        val confirmed = riskEvents.count { it.guardianFeedback?.contains("确认") == true || it.guardianFeedback?.contains("纭") == true }
        val falseAlarms = riskEvents.count { it.isFalseAlarm }
        val feedbackCount = riskEvents.count { it.guardianFeedback != null }
        val maxRisk = scores.maxOrNull() ?: 0f
        return DailyMonitoringSummary(
            date = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(dayStart)),
            maxRiskScore = maxRisk,
            averageRiskScore = scores.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f,
            riskEventCount = riskEvents.size,
            confirmedAbnormalCount = confirmed,
            falseAlarmCount = falseAlarms,
            guardianFeedbackCount = feedbackCount,
            highestRiskTimeSegment = maxEvent?.timeSegment ?: "none",
            topContributingMetrics = topMetrics,
            dataQualityLevel = quality.qualityLevel,
            weatherContext = weatherContext(riskEvents),
            dataQualityWarning = quality.warningText,
            dataMissingReasons = quality.missingReasons,
            summaryText = summaryText(maxRisk, riskEvents.size, quality)
        )
    }

    private fun weatherContext(events: List<RiskEventEntity>): String =
        events.firstOrNull { it.weather.isNotBlank() && it.weather != "unknown" }?.weather
            ?: _uiState.value.weather.displayText()

    private fun EmotionalStateEstimate.applyAgentEmotion(agent: AgentSignalResponse?): EmotionalStateEstimate {
        if (agent == null || agent.confidence < 0.55f || agent.emotionalState.isNullOrBlank()) return this
        return copy(
            primaryState = agent.emotionalState,
            confidence = ((confidence * 0.45f) + (agent.confidence * 0.55f)).coerceIn(0.25f, 0.92f),
            arousalScore = agent.arousalScore?.coerceIn(0f, 1f) ?: arousalScore,
            valenceScore = agent.valenceScore?.coerceIn(-1f, 1f) ?: valenceScore,
            fatigueScore = agent.fatigueScore?.coerceIn(0f, 1f) ?: fatigueScore,
            lonelinessScore = agent.lonelinessScore?.coerceIn(0f, 1f) ?: lonelinessScore,
            stressScore = agent.stressScore?.coerceIn(0f, 1f) ?: stressScore,
            explanation = "模型综合结构化数据判断为“${agent.primaryEmotion ?: agent.emotionalState}”。${agent.reason}",
            secondaryStates = agent.secondaryEmotions,
            observedClues = agent.observedClues.ifEmpty { agent.mainReasons },
            counterEvidence = agent.counterEvidence,
            uncertainty = agent.uncertainty,
            emotionFamilyOverride = agent.emotionFamily
        )
    }

    private fun topContributingMetrics(events: List<RiskEventEntity>): List<String> {
        if (events.isEmpty()) return emptyList()
        val totals = mapOf(
            "心率" to events.map { kotlin.math.abs(it.heartRateDeviationPercent) }.average().toFloat(),
            "呼吸" to events.map { kotlin.math.abs(it.breathRateDeviationPercent) }.average().toFloat(),
            "打字速度" to events.map { kotlin.math.abs(it.typingSpeedDeviationPercent) }.average().toFloat(),
            "删除频率" to events.map { kotlin.math.abs(it.deleteRateDeviationPercent) }.average().toFloat(),
            "停顿时长" to events.map { kotlin.math.abs(it.pauseDurationDeviationPercent) }.average().toFloat()
        )
        return totals.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    private fun summaryText(maxRisk: Float, eventCount: Int, quality: com.neurogarden.app.algorithm.DataQualityResult): String =
        when {
            quality.qualityLevel == "low" -> "今日数据仍不完整，当前结果仅作观察参考。"
            eventCount == 0 -> "今日未记录明显风险事件，整体状态较稳定。"
            maxRisk >= 0.75f -> "今日出现较高风险波动，建议关注异常事件详情和监护反馈。"
            else -> "今日存在轻度波动，系统已记录结构化指标用于后续个体化判断。"
        }

    private fun demoSamples(mode: String, dayStart: Long): List<HabitSampleEntity> {
        val count = if (mode == "stable_day") 12 else 10
        return (0 until count).map { index ->
            val elevated = mode != "stable_day" && index >= count - 3
            HabitSampleEntity(
                timestamp = dayStart + (9 + index) * 60L * 60L * 1000L,
                heartRate = if (elevated) 98 + index else 70 + index % 4,
                breathRate = if (elevated) 19 + index % 3 else 11 + index % 2,
                motionLevel = if (mode == "mild_wave") 0.22f else 0.10f,
                typingSpeed = if (elevated) 64f else 112f,
                deleteRate = if (elevated) 0.18f else 0.04f,
                pauseDuration = if (elevated) 4.2f else 1.3f,
                userFeedback = null,
                contextTag = "demo_$mode",
                riskLevel = if (elevated) "observe" else "stable",
                createdAt = System.currentTimeMillis()
            )
        }
    }

    private fun demoRiskEvent(mode: String, dayStart: Long): RiskEventEntity? {
        if (mode == "stable_day") return null
        val start = when (mode) {
            "night_event" -> dayStart + 23L * 60L * 60L * 1000L
            else -> dayStart + 15L * 60L * 60L * 1000L
        }
        return RiskEventEntity(
            startTime = start,
            endTime = start + 12L * 60L * 1000L,
            riskScore = if (mode == "mild_wave") 0.48f else 0.76f,
            riskLevel = if (mode == "mild_wave") "observe" else "guardian_check",
            confidence = 0.78f,
            mainReasons = "心率高于个人基线|呼吸频率高于个人基线|停顿时长增加",
            metricDeviationPercent = "heartRate=32.0;breathRate=58.0;typingSpeed=-36.0;deleteRate=180.0;pauseDuration=160.0",
            heartRateDeviationPercent = 32f,
            breathRateDeviationPercent = 58f,
            typingSpeedDeviationPercent = -36f,
            deleteRateDeviationPercent = 180f,
            pauseDurationDeviationPercent = 160f,
            motionLevel = 0.10f,
            weather = "normal",
            timeSegment = if (mode == "night_event") "night" else "afternoon",
            agentAnalysis = "source=demo_rule;reasons=structured_demo_features",
            suggestedAction = "建议查看事件详情并确认是否需要监护人反馈。",
            guardianNotified = mode != "mild_wave",
            guardianFeedback = if (mode == "guardian_confirmed") "确认异常" else null,
            isFalseAlarm = false,
            createdAt = System.currentTimeMillis()
        )
    }

    private data class AcceptanceDay(
        val daysAgo: Int,
        val type: String,
        val weather: String
    )

    private data class AcceptanceFeedback(
        val daysAgo: Int,
        val hour: Int,
        val riskLevel: String,
        val state: String,
        val label: String,
        val timing: String,
        val helpful: Boolean,
        val source: String
    )

    private fun acceptanceSamples(dayStart: Long, type: String): List<HabitSampleEntity> {
        val rows = when (type) {
            "mild_wave" -> listOf(
                SignalRow(9, 72, 12, 0.09f, 108f, 0.05f, 1.5f, "stable"),
                SignalRow(16, 84, 15, 0.16f, 82f, 0.11f, 2.9f, "observe"),
                SignalRow(21, 76, 13, 0.08f, 96f, 0.07f, 2.0f, "stable")
            )
            "night_event" -> listOf(
                SignalRow(9, 71, 12, 0.08f, 113f, 0.04f, 1.3f, "stable"),
                SignalRow(16, 77, 14, 0.11f, 98f, 0.07f, 2.1f, "stable"),
                SignalRow(23, 99, 21, 0.08f, 58f, 0.23f, 6.2f, "guardian_check")
            )
            "motion_noise" -> listOf(
                SignalRow(9, 74, 13, 0.10f, 106f, 0.05f, 1.4f, "stable"),
                SignalRow(16, 104, 22, 0.82f, 88f, 0.09f, 2.4f, "observe"),
                SignalRow(21, 76, 13, 0.10f, 99f, 0.06f, 1.8f, "stable")
            )
            "family_check" -> listOf(
                SignalRow(9, 73, 13, 0.09f, 106f, 0.05f, 1.5f, "stable"),
                SignalRow(16, 93, 18, 0.10f, 69f, 0.18f, 4.9f, "guardian_check"),
                SignalRow(21, 84, 15, 0.08f, 82f, 0.10f, 3.0f, "observe")
            )
            "special_care" -> listOf(
                SignalRow(9, 74, 13, 0.08f, 102f, 0.06f, 1.8f, "stable"),
                SignalRow(16, 97, 19, 0.09f, 62f, 0.21f, 5.8f, "guardian_check"),
                SignalRow(21, 92, 18, 0.07f, 67f, 0.18f, 5.0f, "support")
            )
            else -> listOf(
                SignalRow(9, 70, 12, 0.08f, 112f, 0.04f, 1.2f, "stable"),
                SignalRow(16, 74, 13, 0.12f, 105f, 0.05f, 1.7f, "stable"),
                SignalRow(21, 71, 12, 0.06f, 101f, 0.05f, 1.8f, "stable")
            )
        }
        return rows.map { row ->
            HabitSampleEntity(
                timestamp = dayStart + row.hour * 60L * 60L * 1000L,
                heartRate = row.heartRate,
                breathRate = row.breathRate,
                motionLevel = row.motionLevel,
                typingSpeed = row.typingSpeed,
                deleteRate = row.deleteRate,
                pauseDuration = row.pauseDuration,
                userFeedback = null,
                contextTag = "acceptance_${type}_wear_real",
                riskLevel = row.riskLevel,
                createdAt = dayStart + row.hour * 60L * 60L * 1000L
            )
        }
    }

    private data class SignalRow(
        val hour: Int,
        val heartRate: Int,
        val breathRate: Int,
        val motionLevel: Float,
        val typingSpeed: Float,
        val deleteRate: Float,
        val pauseDuration: Float,
        val riskLevel: String
    )

    private fun HabitSampleEntity.toSensorRecord(): SensorRecordEntity {
        val score = when (riskLevel) {
            "guardian_check" -> 0.78f
            "support" -> 0.62f
            "observe" -> 0.42f
            else -> 0.18f
        }
        return SensorRecordEntity(
            timestamp = timestamp,
            heartRate = heartRate,
            breathRate = breathRate,
            motionLevel = motionLevel,
            stressScore = score,
            confidence = if (motionLevel > 0.6f) 0.48f else 0.82f,
            state = riskLevel
        )
    }

    private fun acceptanceRiskEvent(dayStart: Long, type: String, weather: String): RiskEventEntity? {
        val spec = when (type) {
            "mild_wave" -> EventSpec(16, 18, 0.48f, "observe", 0.72f, "打字速度偏离个人习惯|删除频率升高", 17f, 20f, -24f, 120f, 81f, 0.16f, "afternoon", "建议稍后查看状态摘要。", false, null, false)
            "night_event" -> EventSpec(23, 42, 0.79f, "guardian_check", 0.84f, "心率高于个人基线|呼吸频率高于个人基线|停顿时长增加", 35f, 68f, -43f, 360f, 260f, 0.08f, "night", "建议进行一次温和状态确认。", true, "已联系本人", false)
            "motion_noise" -> EventSpec(16, 20, 0.44f, "observe", 0.48f, "运动干扰导致置信度下降|心率高于个人基线", 42f, 76f, -19f, 100f, 50f, 0.82f, "afternoon", "运动干扰较高，仅作为观察记录。", false, "标记误报", true)
            "family_check" -> EventSpec(16, 28, 0.74f, "guardian_check", 0.80f, "心率高于个人基线|删除频率升高|停顿时长增加", 29f, 44f, -36f, 260f, 206f, 0.10f, "afternoon", "建议守护人进行一次状态确认。", true, "确认异常", false)
            "special_care" -> EventSpec(16, 35, 0.82f, "guardian_check", 0.86f, "呼吸频率高于个人基线|打字速度偏离个人习惯|停顿时长增加", 35f, 52f, -43f, 320f, 263f, 0.09f, "afternoon", "建议照护者进行确认，并保持温和陪伴。", true, "继续观察", false)
            else -> return null
        }
        val start = dayStart + spec.hour * 60L * 60L * 1000L
        return RiskEventEntity(
            startTime = start,
            endTime = start + spec.durationMinutes * 60L * 1000L,
            riskScore = spec.score,
            riskLevel = spec.level,
            confidence = spec.confidence,
            mainReasons = spec.reasons,
            metricDeviationPercent = "heartRate=${spec.hr};breathRate=${spec.br};typingSpeed=${spec.typing};deleteRate=${spec.delete};pauseDuration=${spec.pause}",
            heartRateDeviationPercent = spec.hr,
            breathRateDeviationPercent = spec.br,
            typingSpeedDeviationPercent = spec.typing,
            deleteRateDeviationPercent = spec.delete,
            pauseDurationDeviationPercent = spec.pause,
            motionLevel = spec.motion,
            weather = weather,
            timeSegment = spec.segment,
            agentAnalysis = "source=acceptance_seed;reason=multi_day_manual_acceptance",
            suggestedAction = spec.action,
            guardianNotified = spec.notified,
            guardianFeedback = spec.feedback,
            isFalseAlarm = spec.falseAlarm,
            createdAt = start
        )
    }

    private data class EventSpec(
        val hour: Int,
        val durationMinutes: Int,
        val score: Float,
        val level: String,
        val confidence: Float,
        val reasons: String,
        val hr: Float,
        val br: Float,
        val typing: Float,
        val delete: Float,
        val pause: Float,
        val motion: Float,
        val segment: String,
        val action: String,
        val notified: Boolean,
        val feedback: String?,
        val falseAlarm: Boolean
    )

    private fun Long.startOfDay(): Long = Calendar.getInstance().apply {
        timeInMillis = this@startOfDay
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun Long.timeSegment(): String {
        val hour = Calendar.getInstance().apply { timeInMillis = this@timeSegment }
            .get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "late_night"
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            else -> "night"
        }
    }

    private fun emptyDailySummary(): DailyMonitoringSummary =
        DailyMonitoringSummary(
            date = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date()),
            maxRiskScore = 0f,
            averageRiskScore = 0f,
            riskEventCount = 0,
            confirmedAbnormalCount = 0,
            falseAlarmCount = 0,
            guardianFeedbackCount = 0,
            highestRiskTimeSegment = "none",
            topContributingMetrics = emptyList(),
            weatherContext = _uiState.value.weather.displayText(),
            dataQualityLevel = "low",
            dataQualityWarning = "今日数据正在采集中。",
            dataMissingReasons = listOf("尚未形成今日结构化样本"),
            summaryText = "今日数据正在采集中。"
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

    private fun List<RiskEventEntity>.toSupportRiskContext(): String =
        if (isEmpty()) {
            "今天暂无明确异常事件。"
        } else {
            joinToString("；") { event ->
                "风险${"%.2f".format(event.riskScore)}，等级${event.riskLevel}，原因${event.mainReasons.replace("|", "、")}"
            }
        }

    private fun List<HabitSampleEntity>.toRecentActivityContext(): String {
        val latest = maxByOrNull { it.timestamp } ?: return "暂无近期结构化行为样本。"
        return "刚才输入速度${"%.1f".format(latest.typingSpeed)}字/分，删除率${"%.2f".format(latest.deleteRate)}，停顿${"%.1f".format(latest.pauseDuration)}秒，心率${latest.heartRate}，呼吸${latest.breathRate}，运动干扰${"%.2f".format(latest.motionLevel)}。"
    }

    private fun buildCompanionPersonalityModel(
        state: RealtimeUiState,
        summaries: List<ConversationSummaryEntity>
    ): String {
        val preference = when (state.lastUserEmotionLabel) {
            "累" -> "用户可能更需要省力、低要求、允许休息的回应。"
            "烦" -> "用户可能更需要被接住情绪，少讲道理，先降低刺激。"
            "低落" -> "用户可能更需要稳定陪伴和非常小的行动建议。"
            "紧张" -> "用户可能更需要呼吸、落地感和安全确认。"
            "没事" -> "用户倾向表达状态可控，回应要尊重自主性。"
            else -> "用户偏好未知，采用温和、短句、非评判的陪护风格。"
        }
        val memory = summaries.joinToString("；") { it.summary }.ifBlank { "暂无历史对话摘要。" }
        return "$preference 历史对话摘要：$memory"
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val riskEventRepository: RiskEventRepository,
        private val therapyRepository: TherapyRepository,
        private val weatherRepository: WeatherRepository,
        private val careModeStore: CareModeStore,
        private val agentAuditLogRepository: AgentAuditLogRepository,
        private val guardianAgentApi: GuardianAgentApi
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(
                habitRepository,
                riskEventRepository,
                therapyRepository,
                weatherRepository,
                careModeStore,
                agentAuditLogRepository,
                guardianAgentApi
            ) as T
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val SEVEN_DAYS_MS = 7L * DAY_MS
    }
}
