package com.neurogarden.app.data.repository

import com.neurogarden.app.agent.AgentSignalResponse
import com.neurogarden.app.data.local.AgentAuditLogDao
import com.neurogarden.app.data.local.AgentAuditLogEntity

class AgentAuditLogRepository(private val dao: AgentAuditLogDao) {
    val recentLogs = dao.observeRecent()

    suspend fun record(
        triggerReason: String,
        response: AgentSignalResponse?,
        httpSuccess: Boolean,
        fallbackUsed: Boolean,
        fallbackReason: String?,
        cacheUsed: Boolean = false,
        requestSummary: String = "",
        latencyMs: Long = 0L,
        requestTime: Long = System.currentTimeMillis()
    ) {
        dao.insert(
            AgentAuditLogEntity(
                requestTime = requestTime,
                triggerReason = triggerReason,
                httpSuccess = httpSuccess,
                responseEmotion = response?.primaryEmotion ?: response?.emotionalState ?: response?.riskLevel ?: "unknown",
                riskScore = response?.riskScore ?: 0f,
                riskLevel = response?.riskLevel ?: "unknown",
                confidence = response?.confidence ?: 0f,
                mainReasons = response?.mainReasons.orEmpty().take(3).joinToString("|"),
                requestSummary = requestSummary,
                latencyMs = latencyMs,
                fallbackUsed = fallbackUsed,
                fallbackReason = fallbackReason,
                cacheUsed = cacheUsed,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear() = dao.deleteAll()
}
