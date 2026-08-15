package com.tamawatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Wraps TYPE_STEP_COUNTER (cumulative since boot). Emits the raw total; the
 * EconomyEngine handles baselining, daily reset, and reboot discontinuities.
 */
class StepSource(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var onTotal: ((Long) -> Unit)? = null

    val available get() = sensor != null

    fun start(onTotal: (Long) -> Unit) {
        this.onTotal = onTotal
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm.unregisterListener(this)
        onTotal = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            onTotal?.invoke(event.values.firstOrNull()?.toLong() ?: return)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
