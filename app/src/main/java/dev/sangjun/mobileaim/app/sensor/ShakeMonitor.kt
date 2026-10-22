package dev.sangjun.mobileaim.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.sangjun.mobileaim.aim.ShakeReading
import dev.sangjun.mobileaim.aim.ShakeTracker
import dev.sangjun.mobileaim.aim.cameraElevationRad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Measures how much the muzzle bobs up and down, for the on-screen shake indicator. Reads
 * `TYPE_GAME_ROTATION_VECTOR` (gyroscope + accelerometer, no magnetometer: its yaw is arbitrary but
 * its Z axis is up, which is all the camera's elevation needs) and feeds the camera elevation into
 * a [ShakeTracker]. The phone is rigidly mounted, so the camera's elevation is the barrel's.
 *
 * [reading] is null until the first sample and after [stop]. Published at most every
 * [PublishIntervalNanos] so the overlay doesn't recompose at the sensor rate.
 */
class ShakeMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val tracker = ShakeTracker()
    private val rotationMatrix = FloatArray(9)
    private var lastPublishNanos = 0L
    private var isRunning = false

    private val mutableReading = MutableStateFlow<ShakeReading?>(null)
    val reading: StateFlow<ShakeReading?> = mutableReading

    /** Whether the phone has a rotation-vector sensor at all — without one the indicator stays hidden. */
    val isAvailable: Boolean get() = sensor != null

    fun start() {
        if (isRunning || sensor == null) return
        isRunning = true
        tracker.reset()
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
        mutableReading.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val shake = tracker.update(event.timestamp, cameraElevationRad(rotationMatrix))
        if (event.timestamp - lastPublishNanos >= PublishIntervalNanos) {
            lastPublishNanos = event.timestamp
            mutableReading.value = shake
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val PublishIntervalNanos = 33_000_000L
    }
}
