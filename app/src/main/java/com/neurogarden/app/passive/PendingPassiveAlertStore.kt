package com.neurogarden.app.passive

import android.content.Context

data class PendingPassiveAlert(
    val id: Long,
    val title: String,
    val message: String,
    val createdAt: Long
)

object PendingPassiveAlertStore {
    private const val PREFS = "pending_passive_alert"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_MESSAGE = "message"
    private const val KEY_CREATED_AT = "created_at"
    private const val KEY_CONSUMED_ID = "consumed_id"

    fun save(context: Context, title: String, message: String, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ID, now)
            .putString(KEY_TITLE, title)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_CREATED_AT, now)
            .apply()
    }

    fun read(context: Context): PendingPassiveAlert? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_ID, 0L)
        if (id <= 0L || id == prefs.getLong(KEY_CONSUMED_ID, 0L)) return null
        return PendingPassiveAlert(
            id = id,
            title = prefs.getString(KEY_TITLE, null) ?: return null,
            message = prefs.getString(KEY_MESSAGE, null) ?: return null,
            createdAt = prefs.getLong(KEY_CREATED_AT, id)
        )
    }

    fun consume(context: Context, id: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CONSUMED_ID, id)
            .apply()
    }
}
