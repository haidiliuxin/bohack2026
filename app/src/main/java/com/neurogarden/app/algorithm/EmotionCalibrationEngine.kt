package com.neurogarden.app.algorithm

import com.neurogarden.app.data.local.FeedbackRecordEntity

object EmotionCalibrationEngine {
    fun calibrate(
        estimate: EmotionalStateEstimate,
        recentFeedback: List<FeedbackRecordEntity>
    ): EmotionalStateEstimate {
        if (recentFeedback.isEmpty()) return estimate
        val labels = recentFeedback.take(12).map { it.userLabel }
        val dominant = labels.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return estimate
        val falseAlarmRate = recentFeedback.take(12).count { it.userLabel == "误报" }.toFloat() / recentFeedback.take(12).size
        val helpfulRate = recentFeedback.take(12).count { it.helpful }.toFloat() / recentFeedback.take(12).size
        val calibratedState = when {
            falseAlarmRate >= 0.45f && estimate.confidence < 0.72f -> "可能误报，继续观察"
            dominant == "低落" -> "低落疲惫"
            dominant == "累" -> "疲惫恢复慢"
            dominant == "烦" -> "高压紧张"
            dominant == "紧张" -> "焦虑紧绷"
            dominant == "没事" && estimate.stressScore < 0.55f -> "相对稳定"
            else -> estimate.primaryState
        }
        val confidenceAdjustment = when {
            falseAlarmRate >= 0.45f -> -0.12f
            helpfulRate >= 0.60f -> 0.10f
            else -> 0.06f
        }
        val explanationSuffix = when {
            falseAlarmRate >= 0.45f -> "近期误报反馈偏多，本次判断已降低打扰强度。"
            else -> "参考近期用户标注“$dominant”进行校准。"
        }
        return estimate.copy(
            primaryState = calibratedState,
            confidence = (estimate.confidence + confidenceAdjustment).coerceIn(0.25f, 0.92f),
            explanation = "${estimate.explanation} $explanationSuffix"
        )
    }
}
