package com.neurogarden.app.service

import com.neurogarden.app.data.local.DebugEventLogEntity
import com.neurogarden.app.data.local.LocalInterventionFeedbackEntity
import com.neurogarden.app.data.local.WearSyncStatusEntity
import com.neurogarden.app.data.local.NeuroGardenDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 调试日志服务
 * 用于记录结构化日志，不包含敏感信息
 */
object DebugEventLogService {

    private val _logs = MutableStateFlow<List<DebugEventLogEntity>>(emptyList())
    val logs: StateFlow<List<DebugEventLogEntity>> = _logs.asStateFlow()

    private val inMemoryLogs = ConcurrentLinkedQueue<DebugEventLogEntity>()
    private const val MAX_IN_MEMORY_LOGS = 100

    fun log(
        source: String,
        eventType: String,
        message: String,
        payloadSummary: String = "",
        level: String = "info"
    ) {
        val logEntry = DebugEventLogEntity(
            timestamp = System.currentTimeMillis(),
            source = source,
            eventType = eventType,
            message = message,
            payloadSummary = payloadSummary,
            level = level
        )

        inMemoryLogs.offer(logEntry)
        while (inMemoryLogs.size > MAX_IN_MEMORY_LOGS) {
            inMemoryLogs.poll()
        }

        _logs.value = inMemoryLogs.toList().sortedByDescending { it.timestamp }
    }

    fun logInfo(source: String, eventType: String, message: String, payload: String = "") {
        log(source, eventType, message, payload, "info")
    }

    fun logWarning(source: String, eventType: String, message: String, payload: String = "") {
        log(source, eventType, message, payload, "warning")
    }

    fun logError(source: String, eventType: String, message: String, payload: String = "") {
        log(source, eventType, message, payload, "error")
    }

    // 便捷方法：记录情绪事件处理
    fun logEmotionEventProcessed(
        mode: String,
        riskScore: Float,
        riskLevel: String,
        strategyTags: List<String>
    ) {
        logInfo(
            source = "app",
            eventType = "emotion_event_processed",
            message = "情绪事件处理完成",
            payload = "mode=$mode, riskScore=$riskScore, riskLevel=$riskLevel, tags=${strategyTags.joinToString(",")}"
        )
    }

    // 便捷方法：记录通知生成
    fun logNotificationCreated(
        notificationId: String,
        guardianName: String,
        status: String,
        strategyTags: List<String>
    ) {
        logInfo(
            source = "guardian_service",
            eventType = "notification_created",
            message = "守护通知已生成",
            payload = "notificationId=$notificationId, guardian=$guardianName, status=$status, tags=${strategyTags.joinToString(",")}"
        )
    }

    // 便捷方法：记录反馈
    fun logFeedbackReceived(
        feedbackType: String,
        action: String,
        eventId: Long
    ) {
        logInfo(
            source = "app",
            eventType = "feedback_received",
            message = "收到用户反馈",
            payload = "type=$feedbackType, action=$action, eventId=$eventId"
        )
    }

    // 便捷方法：记录手表同步
    fun logWearSync(
        connected: Boolean,
        source: String,
        sampleCount: Int
    ) {
        logInfo(
            source = "sync",
            eventType = "wear_sync_status",
            message = if (connected) "手表已连接" else "手表未连接",
            payload = "connected=$connected, source=$source, samples=$sampleCount"
        )
    }

    // 便捷方法：记录权限状态
    fun logPermissionStatus(
        permission: String,
        granted: Boolean,
        fallback: String
    ) {
        logInfo(
            source = "app",
            eventType = "permission_status",
            message = "权限状态: $permission",
            payload = "granted=$granted, fallback=$fallback"
        )
    }

    fun clearLogs() {
        inMemoryLogs.clear()
        _logs.value = emptyList()
    }

    fun getRecentLogs(count: Int = 20): List<DebugEventLogEntity> {
        return _logs.value.take(count)
    }
}

/**
 * 设备状态管理器
 * 管理手机和手表的连接状态、数据来源等
 */
object DeviceStateManager {

    private val _wearSyncStatus = MutableStateFlow(WearSyncStatusEntity(
        connected = false,
        lastSampleAt = null,
        lastReceivedAt = null,
        lastError = null,
        source = "mock_sensor",
        sampleCount = 0,
        updatedAt = System.currentTimeMillis()
    ))
    val wearSyncStatus: StateFlow<WearSyncStatusEntity> = _wearSyncStatus.asStateFlow()

    private val _currentDataSource = MutableStateFlow("mock_sensor")
    val currentDataSource: StateFlow<String> = _currentDataSource.asStateFlow()

    private val _isRealSensorAvailable = MutableStateFlow(false)
    val isRealSensorAvailable: StateFlow<Boolean> = _isRealSensorAvailable.asStateFlow()

    fun updateWearSyncStatus(
        connected: Boolean,
        source: String = "mock_sensor",
        sampleCount: Int? = null,
        error: String? = null
    ) {
        val now = System.currentTimeMillis()
        val current = _wearSyncStatus.value
        _wearSyncStatus.value = current.copy(
            connected = connected,
            source = source,
            sampleCount = sampleCount ?: current.sampleCount,
            lastReceivedAt = if (connected) now else current.lastReceivedAt,
            lastSampleAt = if (connected) now else current.lastSampleAt,
            lastError = error,
            updatedAt = now
        )
        _currentDataSource.value = source

        DebugEventLogService.logWearSync(connected, source, sampleCount ?: 0)
    }

    fun setRealSensorAvailable(available: Boolean) {
        _isRealSensorAvailable.value = available
        if (available) {
            updateWearSyncStatus(connected = true, source = "real_sensor")
        }
    }

    fun setDataSource(source: String) {
        _currentDataSource.value = source
        updateWearSyncStatus(connected = source != "none", source = source)
    }

    fun incrementSampleCount() {
        val current = _wearSyncStatus.value
        _wearSyncStatus.value = current.copy(
            sampleCount = current.sampleCount + 1,
            lastSampleAt = System.currentTimeMillis()
        )
    }

    fun reset() {
        _wearSyncStatus.value = WearSyncStatusEntity(
            connected = false,
            lastSampleAt = null,
            lastReceivedAt = null,
            lastError = null,
            source = "mock_sensor",
            sampleCount = 0,
            updatedAt = System.currentTimeMillis()
        )
        _currentDataSource.value = "mock_sensor"
    }
}
