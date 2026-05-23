package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emotion_evaluation_records")
data class EmotionEvaluationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val predictedPrimaryEmotion: String,
    val predictedSecondaryEmotions: String,
    val userCorrectedEmotion: String,
    val confidence: Float,
    val valence: Float,
    val arousal: Float,
    val stress: Float,
    val fatigue: Float,
    val loneliness: Float,
    val signalSummary: String,
    val contextSummary: String,
    val agentVersion: String,
    val wasAccepted: Boolean,
    val notes: String?
)
