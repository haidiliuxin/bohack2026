package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_records")
data class SensorRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val heartRate: Int,
    val breathRate: Int,
    val motionLevel: Float,
    val stressScore: Float,
    val confidence: Float,
    val state: String
)
