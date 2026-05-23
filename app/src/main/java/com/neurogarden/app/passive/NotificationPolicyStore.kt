package com.neurogarden.app.passive

import android.content.Context
import java.util.Calendar

object NotificationPolicyStore {
    private const val PREFS = "passive_notification_policy"
    private const val KEY_LAST_NOTIFY_AT = "last_notify_at"
    private const val KEY_NOTIFY_DAY = "notify_day"
    private const val KEY_NOTIFY_COUNT = "notify_count"
    private const val KEY_LAST_POPUP_AT = "last_popup_at"
    private const val KEY_POPUP_WINDOW_START = "popup_window_start"
    private const val KEY_POPUP_WINDOW_COUNT = "popup_window_count"
    private const val DEFAULT_COOLDOWN_MS = 15L * 60L * 1000L
    private const val MAX_DAILY_NOTIFICATIONS = 8
    private const val POPUP_WINDOW_MS = 60L * 60L * 1000L

    data class Status(
        val countToday: Int,
        val maxDaily: Int,
        val cooldownRemainingMinutes: Int,
        val canNotifyNow: Boolean
    )

    fun canNotify(
        context: Context,
        now: Long = System.currentTimeMillis(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        maxDailyNotifications: Int = MAX_DAILY_NOTIFICATIONS
    ): Boolean {
        if (maxDailyNotifications <= 0) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentDay = dayOfYear(now)
        val savedDay = prefs.getInt(KEY_NOTIFY_DAY, -1)
        val count = if (savedDay == currentDay) prefs.getInt(KEY_NOTIFY_COUNT, 0) else 0
        val lastNotifyAt = prefs.getLong(KEY_LAST_NOTIFY_AT, 0L)
        val cooldownPassed = now - lastNotifyAt >= cooldownMs
        return cooldownPassed && count < maxDailyNotifications
    }

    fun recordNotification(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentDay = dayOfYear(now)
        val savedDay = prefs.getInt(KEY_NOTIFY_DAY, -1)
        val count = if (savedDay == currentDay) prefs.getInt(KEY_NOTIFY_COUNT, 0) else 0
        prefs.edit()
            .putLong(KEY_LAST_NOTIFY_AT, now)
            .putInt(KEY_NOTIFY_DAY, currentDay)
            .putInt(KEY_NOTIFY_COUNT, count + 1)
            .apply()
    }

    fun canShowPopup(
        context: Context,
        now: Long = System.currentTimeMillis(),
        minIntervalMs: Long = 30_000L,
        maxHourlyPopups: Int = 2
    ): Boolean {
        if (maxHourlyPopups <= 0) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastPopupAt = prefs.getLong(KEY_LAST_POPUP_AT, 0L)
        if (now - lastPopupAt < minIntervalMs) return false
        val windowStart = prefs.getLong(KEY_POPUP_WINDOW_START, 0L)
        val count = if (now - windowStart < POPUP_WINDOW_MS) {
            prefs.getInt(KEY_POPUP_WINDOW_COUNT, 0)
        } else {
            0
        }
        return count < maxHourlyPopups
    }

    fun recordPopup(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val windowStart = prefs.getLong(KEY_POPUP_WINDOW_START, 0L)
        val withinWindow = now - windowStart < POPUP_WINDOW_MS
        val nextWindowStart = if (withinWindow) windowStart else now
        val nextCount = if (withinWindow) prefs.getInt(KEY_POPUP_WINDOW_COUNT, 0) + 1 else 1
        prefs.edit()
            .putLong(KEY_LAST_POPUP_AT, now)
            .putLong(KEY_POPUP_WINDOW_START, nextWindowStart)
            .putInt(KEY_POPUP_WINDOW_COUNT, nextCount)
            .apply()
    }

    fun status(
        context: Context,
        now: Long = System.currentTimeMillis(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        maxDailyNotifications: Int = MAX_DAILY_NOTIFICATIONS
    ): Status {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentDay = dayOfYear(now)
        val savedDay = prefs.getInt(KEY_NOTIFY_DAY, -1)
        val count = if (savedDay == currentDay) prefs.getInt(KEY_NOTIFY_COUNT, 0) else 0
        val lastNotifyAt = prefs.getLong(KEY_LAST_NOTIFY_AT, 0L)
        val remainingMs = (cooldownMs - (now - lastNotifyAt)).coerceAtLeast(0L)
        val can = maxDailyNotifications > 0 && remainingMs == 0L && count < maxDailyNotifications
        return Status(
            countToday = count,
            maxDaily = maxDailyNotifications,
            cooldownRemainingMinutes = ((remainingMs + 59_999L) / 60_000L).toInt(),
            canNotifyNow = can
        )
    }

    private fun dayOfYear(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }
}
