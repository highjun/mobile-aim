package dev.sangjun.mobileaim.aim

import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * How much the muzzle is bobbing up and down right now. [deviationRad] is the camera's elevation
 * minus its recent average (positive = pointing higher than a moment ago); [rmsRad] is the root
 * mean square of that over the last [ShakeTracker.windowNanos]. Both are angles, so at the 25m
 * zeroing target they read as millimetres via [toMmAt25m].
 */
data class ShakeReading(val deviationRad: Float, val rmsRad: Float)

/** An angle as millimetres of offset at [ZeroingDistanceMeters]. */
fun Float.toMmAt25m(): Float = this * ZeroingDistanceMeters * 1000f

/**
 * Elevation (pitch above the horizon, radians) of where the back camera looks, from a 3x3
 * device-to-world rotation matrix ([android.hardware.SensorManager.getRotationMatrixFromVector],
 * row-major, world Z up). The back camera looks along the device's -Z axis, so its world-frame
 * direction is minus the matrix's third column and its elevation is asin(-R[8]). Independent of how
 * the phone is turned in its mount (portrait or landscape).
 */
fun cameraElevationRad(rotationMatrix: FloatArray): Float = asin((-rotationMatrix[8]).coerceIn(-1f, 1f))

/**
 * Turns a stream of camera elevations into a [ShakeReading]: an exponential moving average with
 * time constant [baselineTauNanos] stands in for "where the shooter is holding", and the shake is
 * the elevation minus that average — so a slow, deliberate change of aim fades out while the quick
 * up-and-down tremor stays. Pure (no Android), so it is unit-tested directly.
 */
class ShakeTracker(
    private val baselineTauNanos: Long = 1_000_000_000L,
    val windowNanos: Long = 1_000_000_000L,
) {
    private var baseline: Double? = null
    private var lastTimeNanos = 0L
    private val window = ArrayDeque<Pair<Long, Float>>()

    fun update(timeNanos: Long, elevationRad: Float): ShakeReading {
        val previous = baseline
        baseline = if (previous == null) {
            elevationRad.toDouble()
        } else {
            val dt = (timeNanos - lastTimeNanos).coerceAtLeast(0L).toDouble()
            val alpha = 1.0 - exp(-dt / baselineTauNanos)
            previous + alpha * (elevationRad - previous)
        }
        lastTimeNanos = timeNanos
        val deviation = (elevationRad - baseline!!).toFloat()
        window.addLast(timeNanos to deviation)
        while (window.isNotEmpty() && timeNanos - window.first().first > windowNanos) window.removeFirst()
        val meanSquare = window.sumOf { (_, d) -> (d * d).toDouble() } / window.size
        return ShakeReading(deviationRad = deviation, rmsRad = sqrt(meanSquare).toFloat())
    }

    fun reset() {
        baseline = null
        window.clear()
    }
}
