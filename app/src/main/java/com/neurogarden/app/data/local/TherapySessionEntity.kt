package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "therapy_sessions")
data class TherapySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val beforeStressScore: Float,
    val afterStressScore: Float,
    val beforeHeartRate: Int,
    val afterHeartRate: Int,
    val beforeBreathRate: Int,
    val afterBreathRate: Int,
    val therapyMode: String,
    val userFeedback: String?
)
