package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_samples")
data class HabitSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val heartRate: Int,
    val breathRate: Int,
    val motionLevel: Float,
    val typingSpeed: Float,
    val deleteRate: Float,
    val pauseDuration: Float,
    val userFeedback: String?,
    val contextTag: String,
    val riskLevel: String,
    val createdAt: Long
)
