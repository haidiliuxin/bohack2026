package com.neurogarden.app.passive

import android.content.Context

data class InteractionSignalSnapshot(
    val typingSpeed: Float,
    val deleteRate: Float,
    val pauseDuration: Float
)

data class AccessibilityDebugSnapshot(
    val typedCount: Int,
    val deleteCount: Int,
    val lastEventAt: Long,
    val lastFlushAt: Long,
    val lastDelta: Int,
    val lastEventType: Int
)

object AccessibilitySignalStore {
    private const val PREFS = "accessibility_signals"
    private const val KEY_TYPED_COUNT = "typed_count"
    private const val KEY_DELETE_COUNT = "delete_count"
    private const val KEY_LAST_EVENT_AT = "last_event_at"
    private const val KEY_LAST_FLUSH_AT = "last_flush_at"
    private const val KEY_LAST_DELTA = "last_delta"
    private const val KEY_LAST_EVENT_TYPE = "last_event_type"
    private const val SERVICE_CONNECTED_EVENT = -100

    fun recordServiceConnected(context: Context, eventTime: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_EVENT_AT, eventTime)
            .putInt(KEY_LAST_EVENT_TYPE, SERVICE_CONNECTED_EVENT)
            .apply()
    }

    fun recordTextChange(
        context: Context,
        addedCount: Int,
        removedCount: Int,
        fallbackDelta: Int,
        eventType: Int,
        eventTime: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val typed = prefs.getInt(KEY_TYPED_COUNT, 0)
        val deleted = prefs.getInt(KEY_DELETE_COUNT, 0)
        val typedDelta = when {
            addedCount > 0 -> addedCount
            fallbackDelta > 0 -> fallbackDelta
            else -> 0
        }
        val deletedDelta = when {
            removedCount > 0 -> removedCount
            fallbackDelta < 0 -> -fallbackDelta
            else -> 0
        }
        prefs.edit()
            .putInt(KEY_TYPED_COUNT, typed + typedDelta)
            .putInt(KEY_DELETE_COUNT, deleted + deletedDelta)
            .putLong(KEY_LAST_EVENT_AT, eventTime)
            .putInt(KEY_LAST_DELTA, typedDelta - deletedDelta)
            .putInt(KEY_LAST_EVENT_TYPE, eventType)
            .apply()
    }

    fun recordTextDelta(context: Context, delta: Int, eventTime: Long) {
        recordTextChange(
            context = context,
            addedCount = delta.coerceAtLeast(0),
            removedCount = (-delta).coerceAtLeast(0),
            fallbackDelta = delta,
            eventType = 16,
            eventTime = eventTime
        )
    }

    fun recordRawEvent(context: Context, eventType: Int, eventTime: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_EVENT_AT, eventTime)
            .putInt(KEY_LAST_EVENT_TYPE, eventType)
            .apply()
    }

    fun snapshotAndReset(context: Context, now: Long = System.currentTimeMillis()): InteractionSignalSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastFlushAt = prefs.getLong(KEY_LAST_FLUSH_AT, now - 60_000L)
        val windowMinutes = ((now - lastFlushAt).coerceAtLeast(1_000L)) / 60_000f
        val typedCount = prefs.getInt(KEY_TYPED_COUNT, 0)
        val deleteCount = prefs.getInt(KEY_DELETE_COUNT, 0)
        val lastEventAt = prefs.getLong(KEY_LAST_EVENT_AT, 0L)
        val pauseDuration = if (lastEventAt == 0L) 0f else ((now - lastEventAt).coerceAtLeast(0L) / 1000f)

        prefs.edit()
            .putInt(KEY_TYPED_COUNT, 0)
            .putInt(KEY_DELETE_COUNT, 0)
            .putLong(KEY_LAST_FLUSH_AT, now)
            .apply()

        return InteractionSignalSnapshot(
            typingSpeed = typedCount / windowMinutes,
            deleteRate = if (typedCount == 0) 0f else deleteCount.toFloat() / typedCount.toFloat(),
            pauseDuration = pauseDuration
        )
    }

    fun debugSnapshot(context: Context): AccessibilityDebugSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AccessibilityDebugSnapshot(
            typedCount = prefs.getInt(KEY_TYPED_COUNT, 0),
            deleteCount = prefs.getInt(KEY_DELETE_COUNT, 0),
            lastEventAt = prefs.getLong(KEY_LAST_EVENT_AT, 0L),
            lastFlushAt = prefs.getLong(KEY_LAST_FLUSH_AT, 0L),
            lastDelta = prefs.getInt(KEY_LAST_DELTA, 0),
            lastEventType = prefs.getInt(KEY_LAST_EVENT_TYPE, 0)
        )
    }
}
