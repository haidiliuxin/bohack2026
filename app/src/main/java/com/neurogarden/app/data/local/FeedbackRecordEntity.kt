package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback_records")
data class FeedbackRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val predictedRiskLevel: String,
    val predictedState: String,
    val userLabel: String,
    val timingFeedback: String,
    val helpful: Boolean,
    val source: String,
    val createdAt: Long
)
