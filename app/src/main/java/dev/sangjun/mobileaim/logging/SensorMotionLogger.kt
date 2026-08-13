package dev.sangjun.mobileaim.logging

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import dev.sangjun.mobileaim.app.sensor.IGravityProvider
import dev.sangjun.mobileaim.app.sensor.IRotationVectorProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Continuously logs raw accelerometer + gyroscope readings to a CSV file for offline analysis
 * (e.g. characterising the recoil impulse profile) — a different job from
 * [IGravityProvider]/[IRotationVectorProvider], which expose fused, filtered orientation for the
 * aim pipeline itself, not a raw data record.
 *
 * Runs on its own [HandlerThread] so file I/O never lands on the camera analyzer thread or main
 * thread. Rows use a long format (`timestamp_ms,sensor,x,y,z`) rather than one column pair per
 * sensor: accelerometer and gyroscope arrive at their own, uncorrelated rates, so joining them
 * into a wide row per timestamp would mean inventing values for whichever sensor didn't fire.
 */
class SensorMotionLogger(private val context: Context) : ISensorMotionLogger, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val handlerThread = HandlerThread("MobileAimSensorLog")
    private var handler: Handler? = null
    private var writer: BufferedWriter? = null

    private val mutableLatestAccel = MutableStateFlow<DoubleArray?>(null)
    override val latestAccel: StateFlow<DoubleArray?> = mutableLatestAccel
    private val mutableLatestGyro = MutableStateFlow<DoubleArray?>(null)
    override val latestGyro: StateFlow<DoubleArray?> = mutableLatestGyro

    override fun start() {
        if (handlerThread.isAlive) return
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        this.handler = handler
        writer = openWriter()
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        // Capture into a local before posting: the close runs asynchronously on handlerThread, and
        // by the time it does, the `writer` field below has already moved on to null.
        val writerToClose = writer
        writer = null
        handler?.post {
            try {
                writerToClose?.flush()
                writerToClose?.close()
            } catch (error: Exception) {
                Log.w(TAG, "Failed to close motion log", error)
            }
        }
        handlerThread.quitSafely()
        handler = null
        mutableLatestAccel.value = null
        mutableLatestGyro.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val label = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "accel"
            Sensor.TYPE_GYROSCOPE -> "gyro"
            else -> return
        }
        val values = doubleArrayOf(event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())
        if (label == "accel") mutableLatestAccel.value = values else mutableLatestGyro.value = values

        try {
            writer?.write("${System.currentTimeMillis()},$label,${values[0]},${values[1]},${values[2]}\n")
        } catch (error: Exception) {
            Log.w(TAG, "Failed to write motion log row", error)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun openWriter(): BufferedWriter? = try {
        val dir = File(context.getExternalFilesDir(null), "sessions").apply { mkdirs() }
        val file = File(dir, "motion_${System.currentTimeMillis()}.csv")
        BufferedWriter(FileWriter(file)).apply { write("timestamp_ms,sensor,x,y,z\n") }
    } catch (error: Exception) {
        Log.w(TAG, "Failed to open motion log file", error)
        null
    }

    private companion object {
        const val TAG = "MobileAimMotionLog"
    }
}
