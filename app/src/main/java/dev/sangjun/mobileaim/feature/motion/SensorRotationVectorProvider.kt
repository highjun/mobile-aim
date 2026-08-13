package dev.sangjun.mobileaim.feature.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps [Sensor.TYPE_GAME_ROTATION_VECTOR] as a [StateFlow] other components can read without
 * touching the sensor framework.
 *
 * [start]/[stop] mirror a camera session's own lifecycle: call [start] once a session is live and
 * [stop] when it tears down.
 */
class SensorRotationVectorProvider(context: Context) : IRotationVectorProvider, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val _rotation = MutableStateFlow<DoubleArray?>(null)
    override val rotation: StateFlow<DoubleArray?> = _rotation

    override fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        _rotation.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        // Android only sends w when the array has a 4th element; otherwise it must be reconstructed
        // from x/y/z per the documented TYPE_(GAME_)ROTATION_VECTOR convention.
        val w = if (event.values.size >= 4) {
            event.values[3].toDouble()
        } else {
            sqrt(max(0.0, 1.0 - x * x - y * y - z * z))
        }
        _rotation.value = doubleArrayOf(w, x, y, z)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
