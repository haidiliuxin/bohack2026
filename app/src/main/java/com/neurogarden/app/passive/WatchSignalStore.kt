package com.neurogarden.app.passive

import android.content.Context
import com.neurogarden.shared.model.SensorPacket

data class WatchSignalSettings(
    val simulationEnabled: Boolean,
    val simulatedHeartRate: Int,
    val simulatedBreathRate: Int,
    val simulatedMotionLevel: Float
)

object WatchSignalStore {
    private const val PREFS = "watch_signals"
    private const val KEY_SIM_ENABLED = "sim_enabled"
    private const val KEY_SIM_HEART = "sim_heart"
    private const val KEY_SIM_BREATH = "sim_breath"
    private const val KEY_SIM_MOTION = "sim_motion"
    private const val KEY_REAL_HEART = "real_heart"
    private const val KEY_REAL_BREATH = "real_breath"
    private const val KEY_REAL_MOTION = "real_motion"
    private const val KEY_REAL_AT = "real_at"
    private const val FRESH_MS = 2L * 60L * 1000L

    fun saveSettings(context: Context, settings: WatchSignalSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIM_ENABLED, settings.simulationEnabled)
            .putInt(KEY_SIM_HEART, settings.simulatedHeartRate)
            .putInt(KEY_SIM_BREATH, settings.simulatedBreathRate)
            .putFloat(KEY_SIM_MOTION, settings.simulatedMotionLevel)
            .apply()
    }

    fun readSettings(context: Context): WatchSignalSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WatchSignalSettings(
            simulationEnabled = prefs.getBoolean(KEY_SIM_ENABLED, false),
            simulatedHeartRate = prefs.getInt(KEY_SIM_HEART, 96),
            simulatedBreathRate = prefs.getInt(KEY_SIM_BREATH, 20),
            simulatedMotionLevel = prefs.getFloat(KEY_SIM_MOTION, 0.10f)
        )
    }

    fun saveRealPacket(context: Context, packet: SensorPacket) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REAL_HEART, packet.heartRate)
            .putInt(KEY_REAL_BREATH, packet.breathRate)
            .putFloat(KEY_REAL_MOTION, packet.motionLevel)
            .putLong(KEY_REAL_AT, System.currentTimeMillis())
            .apply()
    }

    fun currentPacket(context: Context, now: Long = System.currentTimeMillis()): SensorPacket? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val realAt = prefs.getLong(KEY_REAL_AT, 0L)
        if (realAt > 0L && now - realAt <= FRESH_MS) {
            return SensorPacket(
                heartRate = prefs.getInt(KEY_REAL_HEART, 0),
                breathRate = prefs.getInt(KEY_REAL_BREATH, 0),
                heartRateWave = 4f,
                motionLevel = prefs.getFloat(KEY_REAL_MOTION, 0f),
                timestamp = realAt
            )
        }

        val settings = readSettings(context)
        if (!settings.simulationEnabled) return null
        return SensorPacket(
            heartRate = settings.simulatedHeartRate,
            breathRate = settings.simulatedBreathRate,
            heartRateWave = 4f,
            motionLevel = settings.simulatedMotionLevel,
            timestamp = now
        )
    }
}
