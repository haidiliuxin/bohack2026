package com.neurogarden.app.passive

import android.content.Context
import java.util.Calendar

object NotificationPolicyStore {
    private const val PREFS = "passive_notification_policy"
    private const val KEY_LAST_NOTIFY_AT = "last_notify_at"
    private const val KEY_NOTIFY_DAY = "notify_day"
    private const val KEY_NOTIFY_COUNT = "notify_count"
    private const val DEFAULT_COOLDOWN_MS = 15L * 60L * 1000L
    private const val MAX_DAILY_NOTIFICATIONS = 8

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

    private fun dayOfYear(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }
}
