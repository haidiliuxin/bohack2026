package com.neurogarden.app.passive

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import java.util.Calendar

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
    val lastEventType: Int,
    val lastAppCategory: String
)

data class TypingFeatureStatus(
    val accessibilityEnabled: Boolean,
    val todaySampleCount: Int,
    val lastCollectedAt: Long,
    val collectingNow: Boolean
)

object AccessibilitySignalStore {
    private const val PREFS = "accessibility_signals"
    private const val KEY_TYPED_COUNT = "typed_count"
    private const val KEY_DELETE_COUNT = "delete_count"
    private const val KEY_LAST_EVENT_AT = "last_event_at"
    private const val KEY_LAST_FLUSH_AT = "last_flush_at"
    private const val KEY_LAST_DELTA = "last_delta"
    private const val KEY_LAST_EVENT_TYPE = "last_event_type"
    private const val KEY_LAST_APP_CATEGORY = "last_app_category"
    private const val KEY_SAMPLE_DAY = "sample_day"
    private const val KEY_TODAY_SAMPLE_COUNT = "today_sample_count"
    private const val SERVICE_CONNECTED_EVENT = -100
    private const val COLLECTING_WINDOW_MS = 2L * 60L * 1000L

    fun recordServiceConnected(context: Context, eventTime: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_EVENT_TYPE, SERVICE_CONNECTED_EVENT)
            .apply()
    }

    fun recordTextChange(
        context: Context,
        addedCount: Int,
        removedCount: Int,
        fallbackDelta: Int,
        eventType: Int,
        eventTime: Long,
        packageName: String? = null
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
        val currentDay = dayOfYear(eventTime)
        val savedDay = prefs.getInt(KEY_SAMPLE_DAY, -1)
        val todaySampleCount = if (savedDay == currentDay) prefs.getInt(KEY_TODAY_SAMPLE_COUNT, 0) else 0
        val hasFeatureDelta = typedDelta > 0 || deletedDelta > 0
        prefs.edit()
            .putInt(KEY_TYPED_COUNT, typed + typedDelta)
            .putInt(KEY_DELETE_COUNT, deleted + deletedDelta)
            .putLong(KEY_LAST_EVENT_AT, eventTime)
            .putInt(KEY_LAST_DELTA, typedDelta - deletedDelta)
            .putInt(KEY_LAST_EVENT_TYPE, eventType)
            .putString(KEY_LAST_APP_CATEGORY, packageName.toAppCategory())
            .putInt(KEY_SAMPLE_DAY, currentDay)
            .putInt(KEY_TODAY_SAMPLE_COUNT, todaySampleCount + if (hasFeatureDelta) 1 else 0)
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

    fun recordRawEvent(context: Context, eventType: Int, eventTime: Long, packageName: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_EVENT_TYPE, eventType)
            .putString(KEY_LAST_APP_CATEGORY, packageName.toAppCategory())
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
            lastEventType = prefs.getInt(KEY_LAST_EVENT_TYPE, 0),
            lastAppCategory = prefs.getString(KEY_LAST_APP_CATEGORY, "unknown") ?: "unknown"
        )
    }

    fun lastAppCategory(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_APP_CATEGORY, "unknown") ?: "unknown"

    fun status(context: Context, now: Long = System.currentTimeMillis()): TypingFeatureStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentDay = dayOfYear(now)
        val savedDay = prefs.getInt(KEY_SAMPLE_DAY, -1)
        val lastEventAt = prefs.getLong(KEY_LAST_EVENT_AT, 0L)
        val todaySamples = if (savedDay == currentDay) prefs.getInt(KEY_TODAY_SAMPLE_COUNT, 0) else 0
        return TypingFeatureStatus(
            accessibilityEnabled = isAccessibilityServiceEnabled(context),
            todaySampleCount = todaySamples,
            lastCollectedAt = lastEventAt,
            collectingNow = lastEventAt > 0L && now - lastEventAt <= COLLECTING_WINDOW_MS
        )
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/com.neurogarden.app.passive.TypingFeatureAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

    private fun dayOfYear(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun String?.toAppCategory(): String {
        val value = this?.lowercase().orEmpty()
        return when {
            value.isBlank() -> "unknown"
            listOf("wechat", "qq", "telegram", "whatsapp", "discord", "message", "sms").any { value.contains(it) } -> "chat_app"
            listOf("bilibili", "youtube", "tiktok", "douyin", "kuaishou", "video").any { value.contains(it) } -> "video_app"
            listOf("chrome", "browser", "edge", "firefox", "webview").any { value.contains(it) } -> "browser_app"
            listOf("game", "mihoyo", "tencent.tmgp").any { value.contains(it) } -> "game_app"
            value.contains("launcher") -> "launcher"
            else -> "other_app"
        }
    }
}
