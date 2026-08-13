package dev.sangjun.mobileaim.feature.motion

import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the device's current gravity direction, in Android's fixed physical-device sensor
 * frame (X out the right edge, Y out the top edge, Z out of the screen toward the user, as if
 * held upright in its natural orientation). This frame does NOT rotate with the display or with
 * CameraX's target rotation — reorienting it into the camera's optical frame (X right, Y down, Z
 * forward through the lens) is a separate step left to whatever wires this into pose estimation,
 * since it depends on the camera's `SENSOR_ORIENTATION` and is worth verifying against a real
 * device rather than folding silently into this provider.
 *
 * Despite the name, Android's `TYPE_GRAVITY` sensor reports a vector pointing AWAY from the
 * ground (toward the sky) when the device is at rest — the reaction to gravity, not gravity
 * itself. That is also the convention [dev.sangjun.mobileaim.feature.pose.GravityPoseDisambiguator]
 * expects for its `gravityCamera` parameter, once rotated into camera frame.
 */
interface IGravityProvider {
    /** `[x, y, z]` in the device's physical sensor frame; null until the first reading arrives. */
    val gravity: StateFlow<DoubleArray?>

    /** Begins listening; safe to call repeatedly. */
    fun start()

    /** Stops listening and clears [gravity] back to null. */
    fun stop()
}
