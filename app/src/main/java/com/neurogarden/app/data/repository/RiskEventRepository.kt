package com.neurogarden.app.data.repository

import com.neurogarden.app.agent.AgentSignalResponse
import com.neurogarden.app.algorithm.PersonalizedRiskResult
import com.neurogarden.app.algorithm.RiskLevel
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.RiskEventDao
import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

class RiskEventRepository(private val dao: RiskEventDao) {
    fun observeTodayEvents(now: Long = System.currentTimeMillis()) =
        dao.observeTodayRiskEvents(now.startOfDay(), now.startOfNextDay())

    fun observeRecent7DayEvents(now: Long = System.currentTimeMillis()) =
        dao.observeRecentRiskEvents(now - SEVEN_DAYS_MS)

    fun observeEventById(id: Long) = dao.observeRiskEventById(id)

    suspend fun getEventById(id: Long): RiskEventEntity? = dao.getRiskEventById(id)

    suspend fun insertEvent(event: RiskEventEntity): Long = dao.insertRiskEvent(event)

    suspend fun getTodayEvents(now: Long = System.currentTimeMillis()): List<RiskEventEntity> =
        dao.getRiskEventsBetween(now.startOfDay(), now.startOfNextDay())

    suspend fun recordIfNeeded(
        sample: HabitSampleEntity,
        baseline: UserHabitBaselineEntity,
        risk: PersonalizedRiskResult,
        agentResponse: AgentSignalResponse?
    ) {
        if (risk.riskLevel < RiskLevel.OBSERVE || risk.riskScore < OBSERVE_SCORE) return

        val now = sample.timestamp
        val deviations = buildDeviations(sample, baseline)
        val reasons = buildReasons(sample, deviations, risk, agentResponse)
        val event = RiskEventEntity(
            startTime = now,
            endTime = now,
            riskScore = risk.riskScore,
            riskLevel = risk.riskLevel.name.lowercase(),
            confidence = risk.confidence,
            mainReasons = reasons.joinToString("|"),
            metricDeviationPercent = deviations.toMetricString(),
            heartRateDeviationPercent = deviations.getValue("heartRate"),
            breathRateDeviationPercent = deviations.getValue("breathRate"),
            typingSpeedDeviationPercent = deviations.getValue("typingSpeed"),
            deleteRateDeviationPercent = deviations.getValue("deleteRate"),
            pauseDurationDeviationPercent = deviations.getValue("pauseDuration"),
            motionLevel = sample.motionLevel,
            weather = "unknown",
            timeSegment = now.timeSegment(),
            agentAnalysis = buildAgentAnalysis(risk, agentResponse, reasons),
            suggestedAction = risk.suggestedAction,
            guardianNotified = risk.guardianTriggerReason != null,
            guardianFeedback = null,
            isFalseAlarm = false,
            createdAt = System.currentTimeMillis()
        )

        val candidate = dao.getLatestSimilarCandidate(
            since = now - DEDUPE_WINDOW_MS,
            riskLevel = event.riskLevel
        )
        if (candidate != null && hasSimilarReasons(candidate.mainReasons, event.mainReasons)) {
            if (candidate.guardianFeedback?.isContactedFeedback() == true) return
            dao.mergeRiskEvent(
                id = candidate.id,
                endTime = now,
                riskScore = max(candidate.riskScore, event.riskScore),
                confidence = max(candidate.confidence, event.confidence),
                mainReasons = mergeReasonText(candidate.mainReasons, event.mainReasons),
                metricDeviationPercent = event.metricDeviationPercent,
                agentAnalysis = event.agentAnalysis,
                suggestedAction = event.suggestedAction,
                guardianNotified = candidate.guardianNotified || event.guardianNotified
            )
        } else {
            dao.insertRiskEvent(event)
        }
    }

    suspend fun updateGuardianFeedback(id: Long, feedback: String) {
        val isFalseAlarm = feedback.contains("误报") || feedback.contains("璇姤")
        if (isFalseAlarm) {
            dao.markFalseAlarm(id, feedback)
        } else {
            dao.updateGuardianFeedback(id, feedback, isFalseAlarm = false)
        }
    }

    suspend fun clearAll() = dao.deleteAllRiskEvents()

    private fun buildDeviations(
        sample: HabitSampleEntity,
        baseline: UserHabitBaselineEntity
    ): Map<String, Float> = mapOf(
        "heartRate" to percent(sample.heartRate.toFloat(), baseline.avgRestingHeartRate),
        "breathRate" to percent(sample.breathRate.toFloat(), baseline.avgBreathRate),
        "typingSpeed" to percent(sample.typingSpeed, baseline.avgTypingSpeed),
        "deleteRate" to percent(sample.deleteRate, baseline.avgDeleteRate, minBase = 0.01f),
        "pauseDuration" to percent(sample.pauseDuration, baseline.avgPauseDuration, minBase = 0.1f)
    )

    private fun buildReasons(
        sample: HabitSampleEntity,
        deviations: Map<String, Float>,
        risk: PersonalizedRiskResult,
        agentResponse: AgentSignalResponse?
    ): List<String> {
        val agentReasons = agentResponse?.mainReasons.orEmpty().filter { it.isNotBlank() }
        if (agentReasons.isNotEmpty()) return agentReasons.take(5)

        val reasons = buildList {
            if (deviations.getValue("heartRate") > 15f) add("心率高于个人基线")
            if (deviations.getValue("breathRate") > 20f) add("呼吸频率高于个人基线")
            if (abs(deviations.getValue("typingSpeed")) > 25f) add("打字速度偏离个人习惯")
            if (deviations.getValue("deleteRate") > 30f) add("删除频率升高")
            if (deviations.getValue("pauseDuration") > 35f) add("停顿时长增加")
            if (sample.motionLevel > 0.6f) add("运动干扰导致置信度下降")
            risk.guardianTriggerReason?.let { add("触发原因:$it") }
        }
        return reasons.ifEmpty { listOf("风险评分达到观察阈值") }.take(5)
    }

    private fun buildAgentAnalysis(
        risk: PersonalizedRiskResult,
        agentResponse: AgentSignalResponse?,
        reasons: List<String>
    ): String {
        val reasonText = reasons.joinToString(";")
        val agentReason = agentResponse?.reason?.takeIf { it.isNotBlank() } ?: "local_rule"
        return "riskLevel=${risk.riskLevel.name.lowercase()};confidence=${"%.2f".format(risk.confidence)};source=$agentReason;reasons=$reasonText"
    }

    private fun hasSimilarReasons(left: String, right: String): Boolean {
        val leftSet = left.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val rightSet = right.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return leftSet.intersect(rightSet).isNotEmpty()
    }

    private fun mergeReasonText(left: String, right: String): String =
        (left.split("|") + right.split("|"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(6)
            .joinToString("|")

    private fun percent(current: Float, baseline: Float, minBase: Float = 1f): Float {
        val base = max(abs(baseline), minBase)
        return ((current - baseline) / base) * 100f
    }

    private fun Map<String, Float>.toMetricString(): String =
        entries.joinToString(";") { (key, value) -> "$key=${"%.1f".format(value)}" }

    private fun String.isContactedFeedback(): Boolean =
        contains("已联系") || contains("宸茶仈绯")

    private fun Long.startOfDay(): Long = Calendar.getInstance().apply {
        timeInMillis = this@startOfDay
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun Long.startOfNextDay(): Long = startOfDay() + DAY_MS

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

    private companion object {
        const val OBSERVE_SCORE = 0.35f
        const val DEDUPE_WINDOW_MS = 15L * 60L * 1000L
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val SEVEN_DAYS_MS = 7L * DAY_MS
    }
}
