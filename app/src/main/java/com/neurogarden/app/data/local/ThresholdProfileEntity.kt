package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threshold_profiles")
data class ThresholdProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val heartRateDeltaWarning: Float,
    val breathRateWarning: Float,
    val typingSpeedDeltaWarning: Float,
    val deleteRateWarning: Float,
    val pauseDurationWarning: Float,
    val riskTriggerDuration: Int,
    val guardianNotifyThreshold: Float,
    val updatedBy: String,
    val updatedReason: String,
    val updatedAt: Long
)
