package dev.sangjun.mobileaim.driftdetection

import android.util.Log
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.RotationMath
import dev.sangjun.mobileaim.utils.GyroPosePredictor
import dev.sangjun.mobileaim.utils.RotationVectorFrameConverter
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB

/**
 * Detects mount drift via ORB feature matching against a reference frame captured at zero time.
 *
 * A naive "did the frame change" comparison would false-positive constantly — the rig is *meant*
 * to move (that's aiming). The fix: predict where each reference keypoint *should* land in the
 * current frame using only the gyro's measured rotation since the reference (the same maths
 * [dev.sangjun.mobileaim.utils.GyroPosePredictor] uses to carry a pose across a brief
 * vision gap, applied to a bare ray direction here instead of a full pose) — aiming motion is
 * gyro-explained and cancels out, so only genuinely unexplained motion (the rig's mount slipping
 * relative to the phone) shows up as residual.
 */
class OrbDriftMonitorService : IDriftMonitorService {
    private val orb = ORB.create(DriftConfig.ORB_FEATURE_COUNT)
    private val matcher = BFMatcher.create(Core.NORM_HAMMING)

    private var referenceKeypoints: MatOfKeyPoint? = null
    private var referenceDescriptors: Mat? = null
    private var referenceRotationDegrees: Int = 0
    private var referenceRotationDeviceFrame: DoubleArray? = null

    private val mutableState = MutableStateFlow(DriftStatus())
    override val state: StateFlow<DriftStatus> = mutableState

    override fun setReference(gray: Mat, rotationDegrees: Int, rotationDeviceFrame: DoubleArray?) {
        clearReference()
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        orb.detectAndCompute(gray, Mat(), keypoints, descriptors)
        if (keypoints.toArray().isEmpty()) {
            keypoints.release()
            descriptors.release()
            Log.w(TAG, "No ORB features found for drift reference frame")
            mutableState.value = DriftStatus(hasReference = false)
            return
        }
        referenceKeypoints = keypoints
        referenceDescriptors = descriptors
        referenceRotationDegrees = rotationDegrees
        referenceRotationDeviceFrame = rotationDeviceFrame
        mutableState.value = DriftStatus(hasReference = true)
    }

    override fun analyze(gray: Mat, rotationDegrees: Int, intrinsics: CameraIntrinsics?, rotationDeviceFrame: DoubleArray?) {
        val refKeypoints = referenceKeypoints ?: return
        val refDescriptors = referenceDescriptors ?: return
        val refRotationDeviceFrame = referenceRotationDeviceFrame

        val currentKeypoints = MatOfKeyPoint()
        val currentDescriptors = Mat()
        try {
            orb.detectAndCompute(gray, Mat(), currentKeypoints, currentDescriptors)
            if (currentDescriptors.empty() || refDescriptors.empty()) {
                mutableState.value = DriftStatus(hasReference = true, matchCount = 0)
                return
            }

            val knnMatches = mutableListOf<MatOfDMatch>()
            matcher.knnMatch(refDescriptors, currentDescriptors, knnMatches, 2)
            val goodMatches = knnMatches
                .map { it.toArray() }
                .filter { it.size == 2 && it[0].distance < DriftConfig.LOWE_RATIO * it[1].distance }
                .map { it[0] }

            if (goodMatches.size < DriftConfig.MIN_GOOD_MATCHES || intrinsics == null ||
                rotationDeviceFrame == null || refRotationDeviceFrame == null
            ) {
                // Not enough signal to say anything — report the match count so the UI/logs can
                // tell "insufficient data" apart from "checked, looks fine".
                mutableState.value = DriftStatus(hasReference = true, matchCount = goodMatches.size)
                return
            }

            val qOld = RotationVectorFrameConverter.toCameraFrame(refRotationDeviceFrame, referenceRotationDegrees)
            val qNew = RotationVectorFrameConverter.toCameraFrame(rotationDeviceFrame, rotationDegrees)
            val qDelta = RotationMath.multiply(RotationMath.conjugate(qNew), qOld)

            val refPoints = refKeypoints.toArray()
            val currentPoints = currentKeypoints.toArray()
            val residualsPx = goodMatches.mapNotNull { match ->
                val refPoint = refPoints[match.queryIdx].pt
                val currentPoint = currentPoints[match.trainIdx].pt
                val predicted = predictCurrentPosition(refPoint.x, refPoint.y, qDelta, intrinsics) ?: return@mapNotNull null
                val dx = predicted.first - currentPoint.x
                val dy = predicted.second - currentPoint.y
                sqrt(dx * dx + dy * dy)
            }
            if (residualsPx.isEmpty()) {
                mutableState.value = DriftStatus(hasReference = true, matchCount = goodMatches.size)
                return
            }

            val medianResidualPx = residualsPx.sorted()[residualsPx.size / 2]
            mutableState.value = DriftStatus(
                hasReference = true,
                matchCount = goodMatches.size,
                medianResidualPx = medianResidualPx.toFloat(),
                isDrifting = medianResidualPx > DriftConfig.MAX_RESIDUAL_PX,
            )
        } finally {
            currentKeypoints.release()
            currentDescriptors.release()
        }
    }

    override fun clearReference() {
        referenceKeypoints?.release()
        referenceDescriptors?.release()
        referenceKeypoints = null
        referenceDescriptors = null
        referenceRotationDeviceFrame = null
        mutableState.value = DriftStatus(hasReference = false)
    }

    /** Rotates the reference pixel's normalized ray by [qDelta] and reprojects it — see the class doc. */
    private fun predictCurrentPosition(
        refX: Double,
        refY: Double,
        qDelta: DoubleArray,
        intrinsics: CameraIntrinsics,
    ): Pair<Double, Double>? {
        val ray = doubleArrayOf((refX - intrinsics.cx) / intrinsics.fx, (refY - intrinsics.cy) / intrinsics.fy, 1.0)
        val rotated = RotationMath.rotateVector(qDelta, ray)
        if (rotated[2] <= MIN_FORWARD_DEPTH) return null
        return Pair(
            intrinsics.cx + intrinsics.fx * rotated[0] / rotated[2],
            intrinsics.cy + intrinsics.fy * rotated[1] / rotated[2],
        )
    }

    private companion object {
        const val TAG = "MobileAimDrift"
        const val MIN_FORWARD_DEPTH = 1e-6
    }
}
