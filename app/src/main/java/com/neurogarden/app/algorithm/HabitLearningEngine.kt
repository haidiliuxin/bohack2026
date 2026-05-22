package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity
import java.util.Calendar
import kotlin.math.max

enum class HabitLearningWindow(val days: Int) {
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
    THIRTY_DAYS(30)
}

object HabitLearningEngine {
    private const val MEDIUM_SAMPLE_COUNT = 8
    private const val HIGH_SAMPLE_COUNT = 20

    fun windowStart(now: Long, window: HabitLearningWindow): Long =
        now - window.days * 24L * 60L * 60L * 1000L

    fun buildBaseline(
        samples: List<HabitSampleEntity>,
        now: Long = System.currentTimeMillis()
    ): UserHabitBaselineEntity {
        val baselineSamples = samples.filter { SignalQualityEvaluator.isGoodBaselineSample(it) }
        if (baselineSamples.isEmpty()) return defaultBaseline(now)

        return UserHabitBaselineEntity(
            avgRestingHeartRate = trimmedAverage(baselineSamples.map { it.heartRate.toFloat() }),
            avgBreathRate = trimmedAverage(baselineSamples.map { it.breathRate.toFloat() }),
            avgTypingSpeed = trimmedAverageOrDefault(baselineSamples.map { it.typingSpeed }.filter { it > 0f }, 100f),
            avgDeleteRate = trimmedAverageOrDefault(baselineSamples.map { it.deleteRate }.filter { it >= 0f }, 0.05f),
            avgPauseDuration = trimmedAverageOrDefault(baselineSamples.map { it.pauseDuration }.filter { it >= 0f }, 1.5f),
            commonActiveStartHour = baselineSamples.minOf { hourOfDay(it.timestamp) },
            commonActiveEndHour = baselineSamples.maxOf { hourOfDay(it.timestamp) },
            avgRecoveryDuration = estimateRecoveryDuration(baselineSamples),
            sampleCount = baselineSamples.size,
            confidenceLevel = confidenceFor(baselineSamples.size),
            createdAt = baselineSamples.minOf { it.createdAt },
            updatedAt = now
        )
    }

    fun buildThresholdProfile(
        baseline: UserHabitBaselineEntity,
        now: Long = System.currentTimeMillis()
    ): ThresholdProfileEntity {
        val personalized = baseline.sampleCount >= MEDIUM_SAMPLE_COUNT
        return if (personalized) {
            ThresholdProfileEntity(
                heartRateDeltaWarning = max(12f, baseline.avgRestingHeartRate * 0.18f),
                breathRateWarning = max(18f, baseline.avgBreathRate + 5f),
                typingSpeedDeltaWarning = 0.30f,
                deleteRateWarning = max(0.12f, baseline.avgDeleteRate * 2.2f),
                pauseDurationWarning = max(3f, baseline.avgPauseDuration * 1.8f),
                riskTriggerDuration = 180,
                guardianNotifyThreshold = 0.78f,
                updatedBy = "habit_learning",
                updatedReason = "基于 ${baseline.sampleCount} 条个人习惯样本生成个体化阈值",
                updatedAt = now
            )
        } else {
            defaultThresholdProfile(now, "样本不足，使用冷启动默认阈值")
        }
    }

    fun confidenceFor(sampleCount: Int): String = when {
        sampleCount >= HIGH_SAMPLE_COUNT -> "high"
        sampleCount >= MEDIUM_SAMPLE_COUNT -> "medium"
        else -> "low"
    }

    fun isPersonalizedEnabled(baseline: UserHabitBaselineEntity?): Boolean =
        baseline != null && baseline.sampleCount >= MEDIUM_SAMPLE_COUNT

    fun defaultBaseline(now: Long = System.currentTimeMillis()): UserHabitBaselineEntity =
        UserHabitBaselineEntity(
            avgRestingHeartRate = 72f,
            avgBreathRate = 12f,
            avgTypingSpeed = 100f,
            avgDeleteRate = 0.05f,
            avgPauseDuration = 1.5f,
            commonActiveStartHour = 9,
            commonActiveEndHour = 22,
            avgRecoveryDuration = 180f,
            sampleCount = 0,
            confidenceLevel = "low",
            createdAt = now,
            updatedAt = now
        )

    fun defaultThresholdProfile(
        now: Long = System.currentTimeMillis(),
        reason: String = "冷启动默认阈值"
    ): ThresholdProfileEntity =
        ThresholdProfileEntity(
            heartRateDeltaWarning = 18f,
            breathRateWarning = 18f,
            typingSpeedDeltaWarning = 0.35f,
            deleteRateWarning = 0.18f,
            pauseDurationWarning = 4f,
            riskTriggerDuration = 180,
            guardianNotifyThreshold = 0.82f,
            updatedBy = "default",
            updatedReason = reason,
            updatedAt = now
        )

    private fun estimateRecoveryDuration(samples: List<HabitSampleEntity>): Float {
        val elevated = samples.count { it.riskLevel != "stable" }
        return if (elevated == 0) 120f else 180f + elevated * 12f
    }

    private fun trimmedAverage(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        if (values.size < 5) return values.average().toFloat()
        val sorted = values.sorted()
        val trim = (sorted.size * 0.10f).toInt().coerceAtLeast(1)
        return sorted.drop(trim).dropLast(trim).average().toFloat()
    }

    private fun trimmedAverageOrDefault(values: List<Float>, defaultValue: Float): Float =
        if (values.isEmpty()) defaultValue else trimmedAverage(values)

    private fun hourOfDay(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.HOUR_OF_DAY)
    }
}
