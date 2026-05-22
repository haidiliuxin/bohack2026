package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_habit_baselines")
data class UserHabitBaselineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val avgRestingHeartRate: Float,
    val avgBreathRate: Float,
    val avgTypingSpeed: Float,
    val avgDeleteRate: Float,
    val avgPauseDuration: Float,
    val commonActiveStartHour: Int,
    val commonActiveEndHour: Int,
    val avgRecoveryDuration: Float,
    val sampleCount: Int,
    val confidenceLevel: String,
    val createdAt: Long,
    val updatedAt: Long
)
