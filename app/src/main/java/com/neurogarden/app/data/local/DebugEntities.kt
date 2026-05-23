package com.neurogarden.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 调试日志实体
 */
@Entity(tableName = "debug_event_logs")
data class DebugEventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val source: String, // app, wear, guardian_service, special_care_service, sync
    val eventType: String,
    val message: String,
    val payloadSummary: String, // 结构化摘要，不包含敏感信息
    val level: String // info, warning, error
)

/**
 * 手表同步状态
 */
@Entity(tableName = "wear_sync_status")
data class WearSyncStatusEntity(
    @PrimaryKey val id: Int = 1,
    val connected: Boolean,
    val lastSampleAt: Long?,
    val lastReceivedAt: Long?,
    val lastError: String?,
    val source: String, // real_sensor, mock_sensor, jsonl_test_data, manual_test
    val sampleCount: Int,
    val updatedAt: Long
)

/**
 * 本地干预反馈记录
 */
@Entity(tableName = "local_intervention_feedback")
data class LocalInterventionFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedbackId: String,
    val eventId: Long,
    val action: String,
    val note: String,
    val createdAt: Long,
    val sensitivityAdjustment: String,
    val nextReminderAt: Long?
)
