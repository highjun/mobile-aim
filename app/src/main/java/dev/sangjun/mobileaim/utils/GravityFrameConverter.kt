package dev.sangjun.mobileaim.utils

import dev.sangjun.mobileaim.app.sensor.IGravityProvider
import dev.sangjun.mobileaim.strategy.checkermarker.GravityPoseDisambiguator
import dev.sangjun.mobileaim.strategy.checkermarker.ImageProxyMatConverter
import dev.sangjun.mobileaim.strategy.checkermarker.IntrinsicsTransform

/**
 * Rotates a gravity reading from [IGravityProvider]'s device-physical sensor frame into the
 * camera's optical frame that [dev.sangjun.mobileaim.strategy.checkermarker.GravityPoseDisambiguator]
 * expects.
 *
 * This composes two rotations:
 *
 * 1. **Device frame -> "natural-orientation, back-camera-facing" frame.** Android's sensor frame
 *    is X-right/Y-up/Z-toward-the-user (out of the *screen*), fixed to the chassis regardless of
 *    current display rotation. The back camera looks the opposite way (away from the user, through
 *    the *back*), and image rows increase downward rather than up, so this step is `(x, -y, -z)`.
 * 2. **In-plane rotation by [rotationDegrees].** Same clockwise buffer rotation
 *    [dev.sangjun.mobileaim.strategy.checkermarker.ImageProxyMatConverter] applies to frames and
 *    [dev.sangjun.mobileaim.strategy.checkermarker.IntrinsicsTransform.rotate] applies to intrinsics, reused
 *    here (stripped of its translation terms, since a direction has no position) so all three stay
 *    consistent for whatever orientation the device is locked to.
 *
 * Step 1 is a fixed geometric fact; step 2 reuses this codebase's own tested rotation convention.
 * What neither step can confirm from a desk is whether [rotationDegrees] alone (which folds in
 * both the camera's `SENSOR_ORIENTATION` *and* the natural-vs-current-orientation offset) is the
 * *complete* natural-frame-to-analysis-frame rotation, or whether a device's specific sensor
 * mounting angle needs an additional correction — that only shows on a real phone, per this
 * feature's plan. If gravity disambiguation picks the wrong pose consistently rather than
 * occasionally, look here first.
 */
@Deprecated("deprecated CV 파이프라인 전용 — 새 코드에서 참조 금지")
internal object GravityFrameConverter {
    fun toCameraFrame(gravityDeviceFrame: DoubleArray, rotationDegrees: Int): DoubleArray {
        val (x, y, z) = Triple(gravityDeviceFrame[0], -gravityDeviceFrame[1], -gravityDeviceFrame[2])
        val (rx, ry) = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> -y to x
            180 -> -x to -y
            270 -> y to -x
            else -> x to y
        }
        return doubleArrayOf(rx, ry, z)
    }
}
