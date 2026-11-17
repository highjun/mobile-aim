package dev.sangjun.mobileaim.utils

import dev.sangjun.mobileaim.app.sensor.IRotationVectorProvider
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.strategy.checkermarker.RotationMath

/**
 * Predicts how a marker's pose (relative to the camera) should have changed given how the camera
 * itself has rotated since the pose was last measured — used to keep the reticle tracking real
 * camera movement during a brief detection gap, instead of freezing at the last measured pose.
 *
 * Derivation: let `R_CM(t)` be the marker's pose relative to the camera (what solvePnP returns —
 * `v_camera = R_CM * v_marker`), and `R_WC(t)` the camera's own orientation in a fixed reference
 * frame (what [IRotationVectorProvider], converted via [RotationVectorFrameConverter], gives).
 * Since the marker doesn't move in that reference frame, `R_CM(t) = R_CW(t) * R_MW` for some
 * constant `R_MW` (the marker's own fixed orientation in that frame), which eliminates to:
 *
 * ```
 * R_CM(new) = R_CW(new) * R_WC(old) * R_CM(old)
 *           = conjugate(qCameraFrame(new)) * qCameraFrame(old) * R_CM(old)
 * ```
 *
 * This assumes Android's `TYPE_(GAME_)ROTATION_VECTOR` reports device-to-reference-frame
 * orientation (`v_reference = q * v_device`, the convention documented for
 * `SensorManager.getRotationMatrixFromVector`) — unverified without a real device. If gyro
 * prediction turns out to rotate the wrong way in practice, this is the single place to fix: swap
 * which of `qCameraFrameOld`/`qCameraFrameNew` gets [RotationMath.conjugate]d below.
 */
@Deprecated("deprecated CV 파이프라인 전용 — 새 코드에서 참조 금지")
internal object GyroPosePredictor {
    fun predict(
        lastVisionPose: PoseEstimate,
        qCameraFrameOld: DoubleArray,
        qCameraFrameNew: DoubleArray,
    ): PoseEstimate {
        val qPoseOld = RotationMath.normalize(RotationMath.rvecToQuaternion(lastVisionPose.rvec))
        val qOld = RotationMath.normalize(qCameraFrameOld)
        val qNew = RotationMath.normalize(qCameraFrameNew)

        val qDelta = RotationMath.multiply(RotationMath.conjugate(qNew), qOld)
        val qPoseNew = RotationMath.multiply(qDelta, qPoseOld)

        // Rotation-only sensor data can't inform distance; the last measured translation is the
        // best available estimate during a gap.
        return PoseEstimate(RotationMath.quaternionToRvec(qPoseNew), lastVisionPose.tvec.copyOf())
    }
}
