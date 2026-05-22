package com.neurogarden.app.algorithm

data class DiscomfortBoundary(
    val mode: CareMode,
    val observeThreshold: Float,
    val popupThreshold: Float,
    val guardianThreshold: Float,
    val dailyLimit: Int,
    val cooldownMinutes: Int,
    val explanation: String
)

data class DiscomfortAssessment(
    val discomfortScore: Float,
    val shouldShowPopup: Boolean,
    val shouldNotifyGuardian: Boolean,
    val confidenceGate: String,
    val explanation: String
)

object DiscomfortBoundaryCalculator {
    fun boundaryFor(mode: CareMode): DiscomfortBoundary = when (mode) {
        CareMode.SELF_MONITORING -> DiscomfortBoundary(
            mode = mode,
            observeThreshold = 0.45f,
            popupThreshold = 0.68f,
            guardianThreshold = 1.00f,
            dailyLimit = 4,
            cooldownMinutes = 15,
            explanation = "自我监测模式更克制，只做本人温和提醒，不触发监护确认。"
        )

        CareMode.FAMILY_GUARDIAN -> DiscomfortBoundary(
            mode = mode,
            observeThreshold = 0.40f,
            popupThreshold = 0.62f,
            guardianThreshold = 0.78f,
            dailyLimit = 3,
            cooldownMinutes = 15,
            explanation = "家庭守护模式平衡准确性和确认效率，多个指标持续偏离才建议守护确认。"
        )

        CareMode.SPECIAL_CARE -> DiscomfortBoundary(
            mode = mode,
            observeThreshold = 0.35f,
            popupThreshold = 0.56f,
            guardianThreshold = 0.68f,
            dailyLimit = 2,
            cooldownMinutes = 30,
            explanation = "特殊关怀模式更敏感，但每日上限更低、冷却更严格，避免频繁打扰。"
        )
    }

    fun shouldShowPopup(score: Float, mode: CareMode, dataQualityLevel: String): Boolean {
        if (dataQualityLevel == "low") return false
        return score >= boundaryFor(mode).popupThreshold
    }

    fun assessEvent(
        mode: CareMode,
        riskScore: Float,
        motionLevel: Float,
        dataQualityLevel: String,
        sustained: Boolean,
        guardianAlreadyNotified: Boolean
    ): DiscomfortAssessment {
        val boundary = boundaryFor(mode)
        val motionAdjustedScore = if (motionLevel >= 0.60f) riskScore * 0.55f else riskScore
        val dataAllowed = dataQualityLevel != "low"
        val showPopup = dataAllowed && motionAdjustedScore >= boundary.popupThreshold
        val notifyGuardian = dataAllowed &&
            boundary.guardianThreshold < 1f &&
            motionAdjustedScore >= boundary.guardianThreshold &&
            sustained &&
            !guardianAlreadyNotified
        val gate = when {
            !dataAllowed -> "数据可信度 low，仅记录"
            motionLevel >= 0.60f -> "运动干扰较高，已降低权重"
            !sustained && motionAdjustedScore >= boundary.guardianThreshold -> "未满足持续性，仅弹窗观察"
            else -> "满足当前模式数据门槛"
        }
        return DiscomfortAssessment(
            discomfortScore = motionAdjustedScore.coerceIn(0f, 1f),
            shouldShowPopup = showPopup,
            shouldNotifyGuardian = notifyGuardian,
            confidenceGate = gate,
            explanation = "模式=${mode.name}; 弹窗阈值=${"%.2f".format(boundary.popupThreshold)}; 守护阈值=${"%.2f".format(boundary.guardianThreshold)}"
        )
    }
}
