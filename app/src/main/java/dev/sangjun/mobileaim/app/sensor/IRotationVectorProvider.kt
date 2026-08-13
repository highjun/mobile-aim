package dev.sangjun.mobileaim.app.sensor

import dev.sangjun.mobileaim.aim.AimViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the device's current orientation as a quaternion `[w, x, y, z]`, sourced from Android's
 * `TYPE_GAME_ROTATION_VECTOR` (gyroscope + accelerometer fused, no magnetometer — same reasoning
 * as [IGravityProvider]: stays reliable near a mounting rig's metal).
 *
 * The reference frame is arbitrary and fixed at whenever the sensor started — only *changes* in
 * this value (the relative rotation between two readings) are physically meaningful, not any
 * single reading on its own. See [dev.sangjun.mobileaim.aim.AimViewModel] for how such a
 * relative change is turned into a predicted pose during a detection gap.
 */
interface IRotationVectorProvider {
    /** `[w, x, y, z]` in the device's physical sensor frame; null until the first reading arrives. */
    val rotation: StateFlow<DoubleArray?>

    /** Begins listening; safe to call repeatedly. */
    fun start()

    /** Stops listening and clears [rotation] back to null. */
    fun stop()
}
