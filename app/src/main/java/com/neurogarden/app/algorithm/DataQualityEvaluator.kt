package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.RiskEventEntity
import com.neurogarden.app.data.local.SensorRecordEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity

data class DataQualityResult(
    val qualityScore: Int,
    val qualityLevel: String,
    val missingReasons: List<String>,
    val warningText: String
)

object DataQualityEvaluator {
    fun evaluate(
        habitSamples: List<HabitSampleEntity>,
        sensorRecords: List<SensorRecordEntity>,
        riskEvents: List<RiskEventEntity>,
        baseline: UserHabitBaselineEntity?
    ): DataQualityResult {
        val heartCount = sensorRecords.count { it.heartRate > 0 } + habitSamples.count { it.heartRate > 0 }
        val breathCount = sensorRecords.count { it.breathRate > 0 } + habitSamples.count { it.breathRate > 0 }
        val typingCount = habitSamples.count {
            it.typingSpeed > 0f || it.deleteRate > 0f || it.pauseDuration > 0f
        }
        val hasWeather = riskEvents.any { it.weather.isNotBlank() && it.weather != "unknown" }
        val hasWear = sensorRecords.isNotEmpty() || habitSamples.any {
            it.contextTag.contains("Wear", ignoreCase = true) || it.contextTag.contains("手表")
        }
        val baselineScore = when (baseline?.confidenceLevel) {
            "high" -> 20
            "medium" -> 14
            "low" -> 8
            else -> 4
        }

        val score = (
            coverageScore(heartCount, target = 12, maxScore = 20) +
                coverageScore(breathCount, target = 12, maxScore = 18) +
                coverageScore(typingCount, target = 8, maxScore = 18) +
                if (hasWeather) 10 else 0 +
                if (hasWear) 14 else 0 +
                baselineScore
            ).coerceIn(0, 100)

        val missing = buildList {
            if (heartCount < 6) add("心率样本偏少")
            if (breathCount < 6) add("呼吸样本偏少")
            if (typingCount < 4) add("输入节奏样本偏少")
            if (!hasWeather) add("缺少天气结构化数据")
            if (!hasWear) add("未检测到 Wear OS 或手表数据")
            if (baseline == null || baseline.confidenceLevel == "low") add("个人基线可信度仍在学习中")
        }
        val level = when {
            score >= 75 -> "high"
            score >= 45 -> "medium"
            else -> "low"
        }
        val warning = when (level) {
            "high" -> "今日数据较完整，可用于趋势展示。"
            "medium" -> "今日数据基本可用，部分指标仍需补充采集。"
            else -> "今日数据不足，风险判断仅作观察参考。"
        }
        return DataQualityResult(
            qualityScore = score,
            qualityLevel = level,
            missingReasons = missing,
            warningText = warning
        )
    }

    private fun coverageScore(count: Int, target: Int, maxScore: Int): Int =
        ((count.toFloat() / target.toFloat()).coerceIn(0f, 1f) * maxScore).toInt()
}
