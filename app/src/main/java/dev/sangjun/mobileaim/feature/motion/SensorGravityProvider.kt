package dev.sangjun.mobileaim.feature.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps [Sensor.TYPE_GRAVITY] — a fused, low-pass-filtered accelerometer+gyroscope estimate (no
 * magnetometer, so it stays reliable near whatever ferrous/magnetic hardware a mounting rig might
 * use) — as a [StateFlow] other components can read without touching the sensor framework.
 *
 * [start]/[stop] mirror a camera session's own lifecycle: call [start] once a session is live and
 * [stop] when it tears down.
 */
class SensorGravityProvider(context: Context) : IGravityProvider, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _gravity = MutableStateFlow<DoubleArray?>(null)
    override val gravity: StateFlow<DoubleArray?> = _gravity

    override fun start() {
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        _gravity.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GRAVITY) return
        _gravity.value = doubleArrayOf(
            event.values[0].toDouble(),
            event.values[1].toDouble(),
            event.values[2].toDouble(),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
