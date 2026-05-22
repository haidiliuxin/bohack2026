package com.neurogarden.app.algorithm

data class DailyMonitoringSummary(
    val date: String,
    val maxRiskScore: Float,
    val averageRiskScore: Float,
    val riskEventCount: Int,
    val confirmedAbnormalCount: Int,
    val falseAlarmCount: Int,
    val guardianFeedbackCount: Int,
    val highestRiskTimeSegment: String,
    val topContributingMetrics: List<String>,
    val dataQualityLevel: String,
    val summaryText: String
)
