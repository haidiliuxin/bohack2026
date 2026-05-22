package com.neurogarden.app.passive

import android.content.Context

data class PassiveDebugSnapshot(
    val evaluatedAt: Long,
    val typingSpeed: Float,
    val deleteRate: Float,
    val pauseDuration: Float,
    val interactionRisk: Float,
    val heartRate: Int,
    val breathRate: Int,
    val motionLevel: Float,
    val physiologyRisk: Float,
    val combinedRisk: Float,
    val alertAllowed: Boolean,
    val lastReason: String
)

object PassiveDebugStore {
    private const val PREFS = "passive_debug"
    private const val KEY_EVALUATED_AT = "evaluated_at"
    private const val KEY_TYPING_SPEED = "typing_speed"
    private const val KEY_DELETE_RATE = "delete_rate"
    private const val KEY_PAUSE = "pause"
    private const val KEY_INTERACTION_RISK = "interaction_risk"
    private const val KEY_HEART = "heart"
    private const val KEY_BREATH = "breath"
    private const val KEY_MOTION = "motion"
    private const val KEY_PHYSIOLOGY_RISK = "physiology_risk"
    private const val KEY_COMBINED_RISK = "combined_risk"
    private const val KEY_ALERT_ALLOWED = "alert_allowed"
    private const val KEY_REASON = "reason"

    fun save(context: Context, snapshot: PassiveDebugSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_EVALUATED_AT, snapshot.evaluatedAt)
            .putFloat(KEY_TYPING_SPEED, snapshot.typingSpeed)
            .putFloat(KEY_DELETE_RATE, snapshot.deleteRate)
            .putFloat(KEY_PAUSE, snapshot.pauseDuration)
            .putFloat(KEY_INTERACTION_RISK, snapshot.interactionRisk)
            .putInt(KEY_HEART, snapshot.heartRate)
            .putInt(KEY_BREATH, snapshot.breathRate)
            .putFloat(KEY_MOTION, snapshot.motionLevel)
            .putFloat(KEY_PHYSIOLOGY_RISK, snapshot.physiologyRisk)
            .putFloat(KEY_COMBINED_RISK, snapshot.combinedRisk)
            .putBoolean(KEY_ALERT_ALLOWED, snapshot.alertAllowed)
            .putString(KEY_REASON, snapshot.lastReason)
            .apply()
    }

    fun read(context: Context): PassiveDebugSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PassiveDebugSnapshot(
            evaluatedAt = prefs.getLong(KEY_EVALUATED_AT, 0L),
            typingSpeed = prefs.getFloat(KEY_TYPING_SPEED, 0f),
            deleteRate = prefs.getFloat(KEY_DELETE_RATE, 0f),
            pauseDuration = prefs.getFloat(KEY_PAUSE, 0f),
            interactionRisk = prefs.getFloat(KEY_INTERACTION_RISK, 0f),
            heartRate = prefs.getInt(KEY_HEART, 0),
            breathRate = prefs.getInt(KEY_BREATH, 0),
            motionLevel = prefs.getFloat(KEY_MOTION, 0f),
            physiologyRisk = prefs.getFloat(KEY_PHYSIOLOGY_RISK, 0f),
            combinedRisk = prefs.getFloat(KEY_COMBINED_RISK, 0f),
            alertAllowed = prefs.getBoolean(KEY_ALERT_ALLOWED, false),
            lastReason = prefs.getString(KEY_REASON, "还没有后台评估记录") ?: "还没有后台评估记录"
        )
    }
}
