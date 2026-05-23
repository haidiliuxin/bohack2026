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
    val dataLimits: List<String>,
    val sceneTag: String,
    val contextHypotheses: List<String>
) {
    fun requestSummary(): String =
        "quality=$qualityLevel;motion=$motionInterference;local=$localPrimaryEmotion;missing=${missingFields.joinToString("|")};dev=${deviations.toCompactString()}"

    fun localEmotionSummary(): String =
        "primary=$localPrimaryEmotion;candidates=${localCandidates.joinToString("|")};confidence=${"%.2f".format(localConfidence)};scene=$sceneTag;hypothesis=${contextHypotheses.joinToString("|")};clues=${observedClues.joinToString("|")};limits=${dataLimits.joinToString("|")}"
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
        val scene = sceneFromContext(cleaned.contextTag)
        val recent15m = recentSamples.filter { cleaned.timestamp - it.timestamp in 0..FIFTEEN_MINUTES_MS }
        val appSwitchApprox = recent15m.map { sceneFromContext(it.contextTag) }.distinct().size
        val sustainedUse = recent15m.size >= 8
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
        val hour = hourOfDay(cleaned.timestamp)
        val night = nightWeight(hour)
        val evening = eveningWeight(hour)
        val observed = buildList {
            if (heartLift > 0.18f) add("心率相对个人基线升高")
            if (breathLift > 0.16f) add("呼吸相对个人基线偏快")
            if (typingDrop > 0.22f) add("输入速度低于日常")
            if (typingRush > 0.25f) add("输入节奏偏快")
            if (deleteLift > 0.48f) add("删除频率升高")
            if (pauseLift > 0.48f) add("停顿时长增加")
            if (night > 0.5f) add("发生在深夜或凌晨")
            if (scene == "chat_app") add("最近处于聊天输入场景")
            if (scene in listOf("video_app", "game_app")) add("最近处于娱乐或视频场景")
            if (appSwitchApprox >= 3) add("短时间应用场景切换较多")
            if (sustainedUse) add("近期连续使用时间偏长")
        }
        val counter = buildList {
            if (motionInterference != "low") add("运动干扰会降低心率证据权重")
            if (cleaned.heartRate == 0) add("缺少可用心率")
            if (cleaned.breathRate == 0) add("缺少可用呼吸")
            if (qualityLevel == "low") add("数据可信度较低")
            if (scene in listOf("video_app", "game_app")) add("娱乐或游戏场景下心率与输入波动更容易被外部内容影响")
            if (typingRush > 0.30f && deleteLift < 0.30f && pauseLift < 0.35f) add("高输入速度也可能来自专注投入")
            if (observed.isEmpty()) add("当前信号接近个人基线")
        }
        val sceneStressFactor = when (scene) {
            "chat_app" -> 1.10f
            "video_app", "game_app" -> 0.78f
            "browser_app" -> 0.92f
            else -> 1.0f
        }
        val sceneFatigueBonus = when {
            night > 0.5f && sustainedUse -> 0.14f
            sustainedUse -> 0.08f
            else -> 0f
        }
        val stress = ((heartLift * 0.26f + breathLift * 0.22f + deleteLift * 0.28f + pauseLift * 0.24f) * sceneStressFactor)
            .motionAdjusted(cleaned.motionLevel)
            .coerceIn(0f, 1f)
        val fatigue = (typingDrop * 0.38f + pauseLift * 0.32f + night * 0.20f + lowActivity(cleaned) * 0.10f + sceneFatigueBonus)
            .coerceIn(0f, 1f)
        val arousal = (heartLift * 0.35f + breathLift * 0.25f + typingRush * 0.20f + deleteLift * 0.20f)
            .motionAdjusted(cleaned.motionLevel)
            .coerceIn(0f, 1f)
        val loneliness = (pauseLift * 0.40f + night * 0.32f + typingDrop * 0.28f).coerceIn(0f, 1f)
        val patternPrimary = classifyByFeaturePattern(
            cleaned = cleaned,
            scene = scene,
            hour = hour,
            heartLift = heartLift,
            breathLift = breathLift,
            typingDrop = typingDrop,
            typingRush = typingRush,
            deleteLift = deleteLift,
            pauseLift = pauseLift,
            stress = stress,
            fatigue = fatigue,
            arousal = arousal,
            loneliness = loneliness,
            motionInterference = motionInterference
        )
        val primary = patternPrimary ?: when {
            qualityLevel == "low" -> "不确定"
            motionInterference == "high" -> "运动干扰"
            scene in listOf("video_app", "game_app") && arousal >= 0.42f && stress < 0.50f -> "积极活跃"
            typingRush > 0.30f && deleteLift < 0.30f && pauseLift < 0.35f && stress < 0.45f -> "专注"
            stress >= 0.60f && deleteLift >= 0.48f -> "烦躁"
            stress >= 0.58f && heartLift >= 0.34f && breathLift >= 0.42f && pauseLift >= 0.50f -> "焦虑"
            stress >= 0.55f -> "紧张"
            fatigue >= 0.56f && loneliness >= 0.48f && (night > 0.5f || evening > 0.5f) -> "空落"
            fatigue >= 0.52f && evening < 0.5f -> "疲惫"
            loneliness >= 0.58f -> "空落"
            arousal >= 0.45f && stress < 0.36f -> "积极活跃"
            arousal < 0.25f && stress < 0.28f && fatigue < 0.35f -> "平静"
            else -> "平静"
        }
        val candidates = buildList {
            add(primary)
            if (stress >= 0.42f) addAll(listOf("紧张", "焦虑"))
            if (fatigue >= 0.42f) add("疲惫")
            if (loneliness >= 0.42f) add("空落")
            if (arousal >= 0.42f && stress < 0.45f) add("专注")
            if (scene in listOf("video_app", "game_app") && arousal >= 0.38f) add("积极活跃")
            if (scene == "chat_app" && deleteLift >= 0.42f) add("烦躁")
            if (primary == "焦虑") add("紧张")
            if (primary in listOf("平静", "专注")) addAll(listOf("平静", "专注"))
        }.distinct().take(4)
        val hypotheses = buildList {
            when (scene) {
                "chat_app" -> add("聊天场景下，删除率和停顿更可能反映犹豫、紧张或烦躁")
                "video_app" -> add("视频场景下，心率波动可能来自内容刺激，需降低负向判断强度")
                "game_app" -> add("游戏场景下，高唤醒不一定是负面情绪")
                "browser_app" -> add("浏览场景下，应用切换和停顿需要结合时间段观察")
                else -> add("当前场景信息有限，主要参考结构化指标偏离")
            }
            if (night > 0.5f || evening > 0.5f) add("夜间或晚间场景提高疲惫、低落、空落和恢复需求候选权重")
            if (sustainedUse) add("连续使用偏长，提高疲惫候选权重")
            if (appSwitchApprox >= 3) add("短时间多场景切换，提高压力或分心候选权重")
        }.take(4)
        val confidence = when (qualityLevel) {
            "high" -> 0.78f
            "medium" -> 0.62f
            else -> 0.38f
        }.let {
            when {
                motionInterference == "high" -> it - 0.18f
                patternPrimary != null -> it + 0.08f
                else -> it
            }
        }.coerceIn(0.20f, 0.88f)

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
            dataLimits = missing.map { "缺少$it" }.take(4),
            sceneTag = scene,
            contextHypotheses = hypotheses
        )
    }

    private fun pct(value: Float, baseline: Float): Float {
        if (value <= 0f || baseline <= 0f) return 0f
        return ((value - baseline) / baseline).coerceIn(-3f, 3f)
    }

    private fun hourOfDay(timestamp: Long): Int =
        java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(java.util.Calendar.HOUR_OF_DAY)

    private fun nightWeight(hour: Int): Float =
        if (hour in 0..5 || hour >= 23) 0.75f else 0.10f

    private fun eveningWeight(hour: Int): Float =
        if (hour in 18..22) 0.65f else 0.10f

    private fun classifyByFeaturePattern(
        cleaned: HabitSampleEntity,
        scene: String,
        hour: Int,
        heartLift: Float,
        breathLift: Float,
        typingDrop: Float,
        typingRush: Float,
        deleteLift: Float,
        pauseLift: Float,
        stress: Float,
        fatigue: Float,
        arousal: Float,
        loneliness: Float,
        motionInterference: String
    ): String? {
        val eveningOrNight = hour in 18..23 || hour in 0..5
        val socialOrChat = scene in listOf("chat_app", "social_app")
        val taskScene = scene in listOf("productivity_app", "browser_app", "chat_app")
        val entertainmentScene = scene in listOf("video_app", "game_app", "social_app")
        return when {
            motionInterference == "high" || cleaned.motionLevel >= 0.65f -> "运动干扰"

            entertainmentScene &&
                cleaned.typingSpeed >= 100f &&
                cleaned.deleteRate <= 0.18f &&
                cleaned.pauseDuration <= 3.2f &&
                cleaned.heartRate in 78..112 &&
                cleaned.breathRate in 15..23 &&
                cleaned.motionLevel < 0.58f -> "积极活跃"

            cleaned.typingSpeed >= 122f &&
                cleaned.deleteRate <= 0.18f &&
                cleaned.pauseDuration <= 3.0f &&
                cleaned.heartRate in 70..92 &&
                cleaned.breathRate in 13..19 &&
                cleaned.motionLevel < 0.30f -> "专注"

            cleaned.heartRate <= 80 &&
                cleaned.breathRate <= 16 &&
                cleaned.typingSpeed in 55f..125f &&
                cleaned.deleteRate <= 0.14f &&
                cleaned.pauseDuration <= 3.5f &&
                cleaned.motionLevel < 0.25f -> "平静"

            cleaned.heartRate >= 98 &&
                (cleaned.breathRate >= 23 || cleaned.heartRate >= 120) &&
                cleaned.typingSpeed <= 155f &&
                cleaned.deleteRate >= 0.25f &&
                (
                    cleaned.pauseDuration >= 7.0f ||
                        scene == "unknown" ||
                        scene == "chat_app" && cleaned.deleteRate >= 0.58f && cleaned.typingSpeed <= 132f ||
                        cleaned.typingSpeed <= 90f && cleaned.pauseDuration >= 10f
                    ) &&
                cleaned.motionLevel < 0.55f -> "焦虑"

            scene == "social_app" &&
                cleaned.deleteRate >= 0.40f &&
                cleaned.typingSpeed >= 105f &&
                cleaned.pauseDuration <= 7.5f &&
                cleaned.heartRate >= 90 &&
                cleaned.breathRate >= 19 &&
                cleaned.motionLevel < 0.50f -> "烦躁"

            scene == "chat_app" &&
                cleaned.deleteRate >= 0.40f &&
                (cleaned.typingSpeed >= 180f || cleaned.deleteRate >= 0.50f) &&
                cleaned.pauseDuration <= 4.8f &&
                cleaned.heartRate >= 90 &&
                cleaned.breathRate >= 19 &&
                cleaned.motionLevel < 0.50f -> "烦躁"

            scene == "browser_app" &&
                cleaned.deleteRate >= 0.54f &&
                cleaned.typingSpeed >= 120f &&
                cleaned.pauseDuration <= 6.5f &&
                cleaned.heartRate >= 110 &&
                cleaned.breathRate >= 19 &&
                cleaned.motionLevel < 0.50f -> "烦躁"

            taskScene &&
                cleaned.heartRate >= 88 &&
                cleaned.breathRate >= 19 &&
                cleaned.deleteRate in 0.22f..0.58f &&
                cleaned.pauseDuration in 2.4f..9.0f &&
                cleaned.typingSpeed >= 75f &&
                cleaned.motionLevel < 0.40f -> "紧张"

            cleaned.typingSpeed <= 82f &&
                cleaned.pauseDuration >= 8f &&
                cleaned.heartRate <= 86 &&
                cleaned.breathRate <= 18 &&
                cleaned.motionLevel < 0.22f &&
                (scene in listOf("productivity_app", "browser_app") || scene == "video_app" && cleaned.pauseDuration < 20f) -> "疲惫"

            cleaned.pauseDuration >= 9f &&
                cleaned.typingSpeed <= 68f &&
                cleaned.motionLevel <= 0.16f &&
                scene == "social_app" -> "低落"

            cleaned.pauseDuration >= 13f &&
                cleaned.typingSpeed <= 68f &&
                cleaned.motionLevel <= 0.16f &&
                scene == "chat_app" &&
                (cleaned.typingSpeed <= 45f || cleaned.pauseDuration >= 15f || cleaned.deleteRate <= 0.12f) -> "低落"

            cleaned.pauseDuration >= 15f &&
                cleaned.typingSpeed <= 68f &&
                cleaned.motionLevel <= 0.16f &&
                scene == "unknown" &&
                cleaned.deleteRate >= 0.14f -> "低落"

            cleaned.pauseDuration >= 10f &&
                cleaned.typingSpeed <= 82f &&
                cleaned.motionLevel <= 0.16f &&
                (scene == "video_app" || scene == "unknown" || scene == "chat_app") -> "空落"

            fatigue >= 0.58f && loneliness >= 0.55f && eveningOrNight -> "空落"
            fatigue >= 0.52f && typingDrop >= 0.25f && !eveningOrNight -> "疲惫"
            arousal >= 0.42f && typingRush >= 0.24f && stress < 0.42f && deleteLift < 0.40f -> "专注"
            heartLift < 0.18f && breathLift < 0.18f && pauseLift < 0.40f && arousal < 0.32f -> "平静"
            else -> null
        }
    }

    private fun lowActivity(sample: HabitSampleEntity): Float =
        if (sample.motionLevel < 0.12f && sample.typingSpeed in 1f..60f) 0.75f else 0.10f

    private fun sceneFromContext(contextTag: String): String = when {
        contextTag.contains("chat_app", ignoreCase = true) -> "chat_app"
        contextTag.contains("video_app", ignoreCase = true) -> "video_app"
        contextTag.contains("game_app", ignoreCase = true) -> "game_app"
        contextTag.contains("browser_app", ignoreCase = true) -> "browser_app"
        contextTag.contains("social_app", ignoreCase = true) -> "social_app"
        contextTag.contains("productivity_app", ignoreCase = true) -> "productivity_app"
        else -> "unknown"
    }

    private fun Float?.orZero(): Float = this ?: 0f
    private fun Float.positive(): Float = if (this > 0f) this else 0f
    private fun Float.motionAdjusted(motionLevel: Float): Float =
        if (motionLevel >= 0.45f) this * (1f - (motionLevel - 0.35f).coerceIn(0f, 0.45f)) else this

    private const val FIFTEEN_MINUTES_MS = 15L * 60L * 1000L
}

private fun Map<String, Float>.toCompactString(): String =
    entries.joinToString("|") { "${it.key}=${"%.2f".format(it.value)}" }
