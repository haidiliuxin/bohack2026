package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.HabitSampleEntity

data class TrendAssessment(
    val shortWindowRisk: Float,
    val mediumWindowRisk: Float,
    val longWindowRisk: Float,
    val sustainedDeviationMinutes: Int,
    val recoverySpeed: Float,
    val shouldIntervene: Boolean,
    val trendLabel: String,
    val explanation: String
) {
    companion object {
        fun empty(): TrendAssessment =
            TrendAssessment(
                shortWindowRisk = 0f,
                mediumWindowRisk = 0f,
                longWindowRisk = 0f,
                sustainedDeviationMinutes = 0,
                recoverySpeed = 0f,
                shouldIntervene = false,
                trendLabel = "趋势学习中",
                explanation = "需要更多连续样本来判断持续偏离。"
            )
    }
}

object TrendAnalyzer {
    private const val FIVE_MINUTES = 5L * 60L * 1000L
    private const val THIRTY_MINUTES = 30L * 60L * 1000L
    private const val TWO_HOURS = 2L * 60L * 60L * 1000L

    fun analyze(
        samples: List<HabitSampleEntity>,
        now: Long = System.currentTimeMillis()
    ): TrendAssessment {
        if (samples.size < 3) return TrendAssessment.empty()

        val recent5 = samples.filter { it.timestamp >= now - FIVE_MINUTES }
        val recent30 = samples.filter { it.timestamp >= now - THIRTY_MINUTES }
        val recent120 = samples.filter { it.timestamp >= now - TWO_HOURS }

        val shortRisk = riskAverage(recent5)
        val mediumRisk = riskAverage(recent30)
        val longRisk = riskAverage(recent120)
        val sustainedMinutes = sustainedDeviationMinutes(samples, now)
        val recovery = recoverySpeed(recent30)
        val shouldIntervene = sustainedMinutes >= 15 || mediumRisk >= 0.62f || (shortRisk >= 0.72f && recovery < 0.05f)
        val label = when {
            sustainedMinutes >= 30 && mediumRisk >= 0.55f -> "持续偏离"
            mediumRisk >= 0.62f -> "短期高压趋势"
            shortRisk >= 0.72f -> "即时明显偏离"
            longRisk >= 0.48f -> "日内恢复偏慢"
            else -> "趋势平稳"
        }
        val explanation = when (label) {
            "持续偏离" -> "过去约 $sustainedMinutes 分钟持续高于日常节奏，建议进行低打扰关怀。"
            "短期高压趋势" -> "近 30 分钟多项信号偏高，不像单次波动。"
            "即时明显偏离" -> "近 5 分钟状态变化明显，需要继续观察是否恢复。"
            "日内恢复偏慢" -> "近 2 小时仍有轻微偏离，恢复速度偏慢。"
            else -> "近期样本整体接近日常节奏，暂不主动打扰。"
        }

        return TrendAssessment(
            shortWindowRisk = shortRisk,
            mediumWindowRisk = mediumRisk,
            longWindowRisk = longRisk,
            sustainedDeviationMinutes = sustainedMinutes,
            recoverySpeed = recovery,
            shouldIntervene = shouldIntervene,
            trendLabel = label,
            explanation = explanation
        )
    }

    private fun riskAverage(samples: List<HabitSampleEntity>): Float {
        if (samples.isEmpty()) return 0f
        return samples.map { sample ->
            val heart = ((sample.heartRate - 72) / 38f).coerceIn(0f, 1f)
            val breath = ((sample.breathRate - 12) / 12f).coerceIn(0f, 1f)
            val delete = (sample.deleteRate / 0.18f).coerceIn(0f, 1f)
            val pause = (sample.pauseDuration / 5f).coerceIn(0f, 1f)
            heart * 0.28f + breath * 0.24f + delete * 0.22f + pause * 0.26f
        }.average().toFloat()
    }

    private fun sustainedDeviationMinutes(samples: List<HabitSampleEntity>, now: Long): Int {
        val ordered = samples.sortedByDescending { it.timestamp }
        var oldestHighAt = now
        for (sample in ordered) {
            val risk = riskAverage(listOf(sample))
            if (risk < 0.45f) break
            oldestHighAt = sample.timestamp
        }
        return ((now - oldestHighAt).coerceAtLeast(0L) / 60_000L).toInt()
    }

    private fun recoverySpeed(samples: List<HabitSampleEntity>): Float {
        if (samples.size < 2) return 0f
        val ordered = samples.sortedBy { it.timestamp }
        val first = riskAverage(ordered.take((ordered.size / 2).coerceAtLeast(1)))
        val second = riskAverage(ordered.takeLast((ordered.size / 2).coerceAtLeast(1)))
        return first - second
    }
}
