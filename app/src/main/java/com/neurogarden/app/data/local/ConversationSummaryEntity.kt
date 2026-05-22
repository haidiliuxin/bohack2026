package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_summaries")
data class ConversationSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val riskLevel: String,
    val emotionalLabel: String?,
    val summary: String,
    val suggestedAction: String,
    val shouldNotifyGuardian: Boolean,
    val createdAt: Long
)
