package dev.sangjun.mobileaim.logging

import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import dev.sangjun.mobileaim.app.sensor.IGravityProvider
import dev.sangjun.mobileaim.app.sensor.IRotationVectorProvider
import dev.sangjun.mobileaim.utils.fileTimestampNow
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
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

    // A java.lang.Thread can only ever be started once — even after it's finished, calling
    // .start() again throws IllegalThreadStateException. start()/stop() can now run many times
    // per process (user-toggled Play/Pause, not just once at app launch), so a fresh HandlerThread
    // is created on every start() rather than reusing one instance across the whole session.
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var writer: BufferedWriter? = null

    private val mutableLatestAccel = MutableStateFlow<DoubleArray?>(null)
    override val latestAccel: StateFlow<DoubleArray?> = mutableLatestAccel
    private val mutableLatestGyro = MutableStateFlow<DoubleArray?>(null)
    override val latestGyro: StateFlow<DoubleArray?> = mutableLatestGyro

    override fun start() {
        if (handlerThread != null) return
        val thread = HandlerThread("MobileAimSensorLog").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
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
        handlerThread?.quitSafely()
        handlerThread = null
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

    /**
     * A fresh file per session (unlike `captures.csv`, which one file spans the app's whole
     * lifetime — see [CaptureLogWriter]), so this always inserts a new MediaStore row rather than
     * needing to resolve an existing one.
     */
    private fun openWriter(): BufferedWriter? {
        val fileName = "motion_${fileTimestampNow()}.csv"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, SensorMediaRoot)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null
                val stream = context.contentResolver.openOutputStream(uri) ?: return null
                BufferedWriter(OutputStreamWriter(stream)).apply { write("timestamp_ms,sensor,x,y,z\n") }
            } else {
                val dir = File(context.getExternalFilesDir(null), SensorMediaRoot).apply { mkdirs() }
                val file = File(dir, fileName)
                BufferedWriter(FileWriter(file)).apply { write("timestamp_ms,sensor,x,y,z\n") }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to open motion log file", error)
            null
        }
    }

    private companion object {
        const val TAG = "MobileAimMotionLog"
    }
}
