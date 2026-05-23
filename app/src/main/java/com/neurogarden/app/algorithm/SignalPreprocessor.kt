package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import kotlin.math.abs

data class CleanedSignalSnapshot(
    val sample: HabitSampleEntity,
    val deviations: Map<String, Float>,
    val missingFields: List<String>,
    val qualityLevel: String,
    val motionInterference: String,
    val localPrimaryEmotion: String,
    val localCandidates: List<String>,
    val localConfidence: Float,
    val observedClues: List<String>,
    val counterEvidence: List<String>,
    val dataLimits: List<String>
) {
    fun requestSummary(): String =
        "quality=$qualityLevel;motion=$motionInterference;local=$localPrimaryEmotion;missing=${missingFields.joinToString("|")};dev=${deviations.toCompactString()}"

    fun localEmotionSummary(): String =
        "primary=$localPrimaryEmotion;candidates=${localCandidates.joinToString("|")};confidence=${"%.2f".format(localConfidence)};clues=${observedClues.joinToString("|")};limits=${dataLimits.joinToString("|")}"
}

object SignalPreprocessor {
    fun preprocess(
        sample: HabitSampleEntity,
        baseline: UserHabitBaselineEntity,
        thresholds: ThresholdProfileEntity,
        recentSamples: List<HabitSampleEntity> = emptyList()
    ): CleanedSignalSnapshot {
        val missing = buildList {
            if (sample.heartRate <= 0) add("heartRate")
            if (sample.breathRate <= 0) add("breathRate")
            if (sample.typingSpeed <= 0f) add("typingSpeed")
            if (sample.deleteRate <= 0f) add("deleteRate")
            if (sample.pauseDuration <= 0f) add("pauseDuration")
            add("hrv")
            add("sleep")
        }.distinct()
        val cleaned = sample.copy(
            heartRate = sample.heartRate.takeIf { it in 35..220 } ?: 0,
            breathRate = sample.breathRate.takeIf { it in 6..40 } ?: 0,
            motionLevel = sample.motionLevel.coerceIn(0f, 1f),
            typingSpeed = sample.typingSpeed.coerceIn(0f, 240f),
            deleteRate = sample.deleteRate.coerceIn(0f, 0.95f),
            pauseDuration = sample.pauseDuration.coerceIn(0f, 30f)
        )
        val deviations = mapOf(
            "heartRate" to pct(cleaned.heartRate.toFloat(), baseline.avgRestingHeartRate),
            "breathRate" to pct(cleaned.breathRate.toFloat(), baseline.avgBreathRate),
            "typingSpeed" to pct(cleaned.typingSpeed, baseline.avgTypingSpeed),
            "deleteRate" to pct(cleaned.deleteRate, baseline.avgDeleteRate),
            "pauseDuration" to pct(cleaned.pauseDuration, baseline.avgPauseDuration)
        )
        val motionInterference = when {
            cleaned.motionLevel >= 0.70f -> "high"
            cleaned.motionLevel >= 0.45f -> "medium"
            else -> "low"
        }
        val usable = listOf(
            cleaned.heartRate > 0,
            cleaned.breathRate > 0,
            cleaned.typingSpeed > 0f,
            cleaned.deleteRate > 0f,
            cleaned.pauseDuration > 0f
        ).count { it }
        val qualityLevel = when {
            baseline.sampleCount < 5 || usable < 2 -> "low"
            missing.size >= 4 || recentSamples.size < 3 -> "medium"
            else -> "high"
        }

        val heartLift = deviations["heartRate"].orZero().positive()
        val breathLift = deviations["breathRate"].orZero().positive()
        val typingDrop = (-deviations["typingSpeed"].orZero()).positive()
        val typingRush = deviations["typingSpeed"].orZero().positive()
        val deleteLift = (cleaned.deleteRate / thresholds.deleteRateWarning).coerceIn(0f, 2f) / 2f
        val pauseLift = (cleaned.pauseDuration / thresholds.pauseDurationWarning).coerceIn(0f, 2f) / 2f
        val night = nightWeight(cleaned.timestamp)
        val observed = buildList {
            if (heartLift > 0.18f) add("心率相对个人基线升高")
            if (breathLift > 0.16f) add("呼吸相对个人基线偏快")
            if (typingDrop > 0.22f) add("输入速度低于日常")
            if (typingRush > 0.25f) add("输入节奏偏快")
            if (deleteLift > 0.48f) add("删除频率升高")
            if (pauseLift > 0.48f) add("停顿时长增加")
            if (night > 0.5f) add("发生在深夜或凌晨")
        }
        val counter = buildList {
            if (motionInterference != "low") add("运动干扰会降低心率证据权重")
            if (cleaned.heartRate == 0) add("缺少可用心率")
            if (cleaned.breathRate == 0) add("缺少可用呼吸")
            if (qualityLevel == "low") add("数据可信度较低")
            if (observed.isEmpty()) add("当前信号接近个人基线")
        }
        val stress = (heartLift * 0.26f + breathLift * 0.22f + deleteLift * 0.28f + pauseLift * 0.24f)
            .motionAdjusted(cleaned.motionLevel)
            .coerceIn(0f, 1f)
        val fatigue = (typingDrop * 0.38f + pauseLift * 0.32f + night * 0.20f + lowActivity(cleaned) * 0.10f)
            .coerceIn(0f, 1f)
        val arousal = (heartLift * 0.35f + breathLift * 0.25f + typingRush * 0.20f + deleteLift * 0.20f)
            .motionAdjusted(cleaned.motionLevel)
            .coerceIn(0f, 1f)
        val loneliness = (pauseLift * 0.40f + night * 0.32f + typingDrop * 0.28f).coerceIn(0f, 1f)
        val primary = when {
            qualityLevel == "low" -> "不确定"
            motionInterference == "high" -> "运动干扰"
            stress >= 0.60f && deleteLift >= 0.48f -> "烦躁"
            stress >= 0.55f -> "紧张"
            fatigue >= 0.56f && loneliness >= 0.48f -> "空落"
            fatigue >= 0.52f -> "疲惫"
            loneliness >= 0.58f -> "孤独"
            arousal >= 0.45f && stress < 0.36f -> "积极活跃"
            arousal < 0.25f && stress < 0.28f && fatigue < 0.35f -> "轻松"
            else -> "平静"
        }
        val candidates = buildList {
            add(primary)
            if (stress >= 0.42f) addAll(listOf("压力偏高", "紧张"))
            if (fatigue >= 0.42f) add("疲惫")
            if (loneliness >= 0.42f) add("空落")
            if (arousal >= 0.42f && stress < 0.45f) add("专注")
            if (primary in listOf("平静", "轻松")) add("专注")
        }.distinct().take(4)
        val confidence = when (qualityLevel) {
            "high" -> 0.78f
            "medium" -> 0.62f
            else -> 0.38f
        }.let { if (motionInterference == "high") it - 0.18f else it }.coerceIn(0.20f, 0.88f)

        return CleanedSignalSnapshot(
            sample = cleaned,
            deviations = deviations,
            missingFields = missing,
            qualityLevel = qualityLevel,
            motionInterference = motionInterference,
            localPrimaryEmotion = primary,
            localCandidates = candidates,
            localConfidence = confidence,
            observedClues = observed.ifEmpty { listOf("当前结构化信号没有明显偏离") },
            counterEvidence = counter,
            dataLimits = missing.map { "缺少$it" }.take(4)
        )
    }

    private fun pct(value: Float, baseline: Float): Float {
        if (value <= 0f || baseline <= 0f) return 0f
        return ((value - baseline) / baseline).coerceIn(-3f, 3f)
    }

    private fun nightWeight(timestamp: Long): Float {
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(java.util.Calendar.HOUR_OF_DAY)
        return if (hour in 0..5 || hour >= 23) 0.75f else 0.10f
    }

    private fun lowActivity(sample: HabitSampleEntity): Float =
        if (sample.motionLevel < 0.12f && sample.typingSpeed in 1f..60f) 0.75f else 0.10f

    private fun Float?.orZero(): Float = this ?: 0f
    private fun Float.positive(): Float = if (this > 0f) this else 0f
    private fun Float.motionAdjusted(motionLevel: Float): Float =
        if (motionLevel >= 0.45f) this * (1f - (motionLevel - 0.35f).coerceIn(0f, 0.45f)) else this
}

private fun Map<String, Float>.toCompactString(): String =
    entries.joinToString("|") { "${it.key}=${"%.2f".format(it.value)}" }
