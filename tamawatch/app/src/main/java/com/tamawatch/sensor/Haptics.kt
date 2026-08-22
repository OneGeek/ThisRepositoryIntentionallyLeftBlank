package com.tamawatch.sensor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tamawatch.core.model.DomainEvent

/** Wrist-tap feedback. Patterns from asset list §B6. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled = true

    private fun buzz(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    fun tick() = buzz(longArrayOf(0, 12), intArrayOf(0, 90))
    fun confirm() = buzz(longArrayOf(0, 25), intArrayOf(0, 160))
    fun error() = buzz(longArrayOf(0, 40, 40, 40), intArrayOf(0, 200, 0, 200))
    fun call() = buzz(longArrayOf(0, 120, 80, 120), intArrayOf(0, 255, 0, 255))
    fun evolve() = buzz(longArrayOf(0, 40, 40, 40, 40, 120), intArrayOf(0, 120, 0, 180, 0, 255))

    fun onEvent(e: DomainEvent) = when (e) {
        is DomainEvent.Called -> call()
        is DomainEvent.Evolved, is DomainEvent.Hatched -> evolve()
        is DomainEvent.Scolded -> if (e.correct) confirm() else error()
        is DomainEvent.Fed -> if (e.refused) error() else confirm()
        is DomainEvent.Cleaned -> confirm()
        is DomainEvent.Healed -> if (e.gentle) confirm() else tick()
        is DomainEvent.StepGoal -> confirm()
        is DomainEvent.Petted -> if (e.effective) confirm() else tick()
        is DomainEvent.Annoyed -> error()
        is DomainEvent.WokeUp -> tick()
        else -> Unit
    }
}
