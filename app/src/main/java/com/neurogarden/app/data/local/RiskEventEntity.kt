package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "risk_events")
data class RiskEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val riskScore: Float,
    val riskLevel: String,
    val confidence: Float,
    val mainReasons: String,
    val metricDeviationPercent: String,
    val heartRateDeviationPercent: Float,
    val breathRateDeviationPercent: Float,
    val typingSpeedDeviationPercent: Float,
    val deleteRateDeviationPercent: Float,
    val pauseDurationDeviationPercent: Float,
    val motionLevel: Float,
    val weather: String,
    val timeSegment: String,
    val agentAnalysis: String,
    val suggestedAction: String,
    val guardianNotified: Boolean,
    val guardianFeedback: String?,
    val isFalseAlarm: Boolean,
    val createdAt: Long
)
