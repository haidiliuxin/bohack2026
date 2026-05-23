package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_audit_logs")
data class AgentAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestTime: Long,
    val triggerReason: String,
    val httpSuccess: Boolean,
    val responseEmotion: String,
    val riskScore: Float,
    val riskLevel: String,
    val confidence: Float,
    val mainReasons: String,
    val fallbackUsed: Boolean,
    val fallbackReason: String?,
    val cacheUsed: Boolean,
    val createdAt: Long
)
