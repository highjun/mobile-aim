package dev.sangjun.mobileaim.feature.motion

import dev.sangjun.mobileaim.feature.pose.RotationMath

/**
 * Rotates a full orientation quaternion from [IRotationVectorProvider]'s device-physical sensor
 * frame into the camera's optical frame — the quaternion analogue of [GravityFrameConverter],
 * built from the exact same physical device-to-camera relationship (front/back flip, then
 * in-plane rotation by `rotationDegrees`).
 *
 * A single vector converts by just applying that relationship directly (what
 * [GravityFrameConverter] does). A full rotation needs the standard change-of-basis conjugation
 * instead: if `qFrame` is the fixed rotation mapping device-frame vectors to their camera-frame
 * counterparts, then a device-frame rotation `qDevice` becomes, in camera-frame terms,
 * `qCamera = qFrame * qDevice * qFrame^-1`.
 *
 * Same caveat as [GravityFrameConverter]: whether `rotationDegrees` alone fully captures the
 * device's `SENSOR_ORIENTATION` for this relationship is unverified without a real device.
 */
internal object RotationVectorFrameConverter {
    fun toCameraFrame(deviceQuaternion: DoubleArray, rotationDegrees: Int): DoubleArray {
        val qFrame = frameChangeQuaternion(rotationDegrees)
        val qDevice = RotationMath.normalize(deviceQuaternion)
        return RotationMath.multiply(
            RotationMath.multiply(qFrame, qDevice),
            RotationMath.conjugate(qFrame),
        )
    }

    /** `qFrame`: front/back flip (matches [GravityFrameConverter]'s `(x, -y, -z)` step) then in-plane rotation. */
    private fun frameChangeQuaternion(rotationDegrees: Int): DoubleArray {
        val normalizedDegrees = (((rotationDegrees % 360) + 360) % 360).toDouble()
        val inPlane = RotationMath.rvecToQuaternion(doubleArrayOf(0.0, 0.0, Math.toRadians(normalizedDegrees)))
        return RotationMath.multiply(inPlane, FLIP)
    }

    private val FLIP = RotationMath.rvecToQuaternion(doubleArrayOf(Math.PI, 0.0, 0.0))
}
