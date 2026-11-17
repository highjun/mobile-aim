package dev.sangjun.mobileaim.aim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
 * direction is minus the matrix's third column (-R[2], -R[5], -R[8]). Its elevation is taken with
 * atan2 rather than asin(-R[8]) so it stays well-conditioned whatever the phone's pose. It is 0
 * with the phone standing upright (screen perpendicular to the ground, as mounted on the rifle) and
 * independent of whether the phone is turned portrait or landscape in its mount.
 */
fun cameraElevationRad(rotationMatrix: FloatArray): Float {
    val horizontal = sqrt(rotationMatrix[2] * rotationMatrix[2] + rotationMatrix[5] * rotationMatrix[5])
    return atan2(-rotationMatrix[8], horizontal)
}

/**
 * Turns a stream of camera elevations into a [ShakeReading]. A first-order low-pass at
 * [cutoffHz] first drops the fast buzz a handheld aim never makes (desk or keyboard vibration,
 * sensor noise). Then an exponential moving average with
 * time constant [baselineTauNanos] stands in for "where the shooter is holding", and the shake is
 * the elevation minus that average — so a slow, deliberate change of aim fades out while the quick
 * up-and-down tremor stays. A jump of more than [reaimRad] from that average (the shooter swinging
 * to a new aim, or recoil) isn't shake: the filter, the average and the RMS window all restart from
 * the new elevation, so the indicator reads the new hold at once instead of easing back over
 * seconds. Pure (no Android), so it is unit-tested directly.
 */
class ShakeTracker(
    private val baselineTauNanos: Long = 1_000_000_000L,
    val windowNanos: Long = 1_000_000_000L,
    private val cutoffHz: Double = 10.0,
    private val reaimRad: Double = 0.01,
) {
    private var filtered: Double? = null
    private var baseline: Double? = null
    private var lastTimeNanos = 0L
    private val window = ArrayDeque<Pair<Long, Float>>()

    fun update(timeNanos: Long, elevationRad: Float): ShakeReading {
        baseline?.let { if (abs(elevationRad - it) > reaimRad) reset() }
        val dt = if (filtered == null) 0.0 else (timeNanos - lastTimeNanos).coerceAtLeast(0L).toDouble()
        val rc = 1e9 / (2 * PI * cutoffHz)
        val lowPassed = filtered?.let { it + dt / (rc + dt) * (elevationRad - it) } ?: elevationRad.toDouble()
        filtered = lowPassed
        val previous = baseline
        baseline = if (previous == null) lowPassed else previous + (1.0 - exp(-dt / baselineTauNanos)) * (lowPassed - previous)
        lastTimeNanos = timeNanos
        val deviation = (lowPassed - baseline!!).toFloat()
        window.addLast(timeNanos to deviation)
        while (window.isNotEmpty() && timeNanos - window.first().first > windowNanos) window.removeFirst()
        val meanSquare = window.sumOf { (_, d) -> (d * d).toDouble() } / window.size
        return ShakeReading(deviationRad = deviation, rmsRad = sqrt(meanSquare).toFloat())
    }

    fun reset() {
        filtered = null
        baseline = null
        window.clear()
    }
}
