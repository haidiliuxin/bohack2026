package com.neurogarden.wear.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BreathHapticController(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    fun startPattern(inhaleSeconds: Int, exhaleSeconds: Int) {
        stop()
        job = scope.launch {
            while (true) {
                vibrate(80)
                delay(inhaleSeconds * 1000L)
                vibrate(60)
                delay(140)
                vibrate(60)
                delay(exhaleSeconds * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        vibrator.cancel()
    }

    private fun vibrate(durationMs: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
