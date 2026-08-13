package dev.sangjun.mobileaim.strategy

import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat

/**
 * Tracks aim with no camera frame and no rotation sensing at all — the same role a physical
 * scope's zero knobs play. [setZero] just starts reporting [ZERO_POSE]; since the camera is
 * rigidly mounted to the barrel, a fixed pose is *already* correct for every subsequent frame —
 * anything downrange of the barrel always projects to the same screen position regardless of how
 * the whole rig rotates, exactly like a reticle etched in scope glass. (An earlier version
 * replayed [dev.sangjun.mobileaim.utils.GyroPosePredictor]'s gap-filling math from the zero point,
 * which instead tracks a *world-fixed* point — correct for briefly bridging a real marker (see
 * [dev.sangjun.mobileaim.aim.AimViewModel]'s stale-pose handling), but wrong here: it made the
 * reticle drift away from the true boresight as soon as the rig panned away from the zeroed
 * direction, the opposite of how a scope behaves.)
 *
 * Distance can't be recovered without vision, so the pose's translation stays pinned at
 * [NoMarkerConfig.NOMINAL_STANDOFF_MM] — the ballistic ladder still works because range comes from
 * the *target point* ([dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig.targetLocalPointMm]),
 * not from this pose's tvec.
 */
class NoMarkerAimTrackingService : IAimTrackingService, ZeroableAimTracking {
    private var isZeroed = false

    private val mutableState = MutableStateFlow(notZeroedResult())
    override val state: StateFlow<TrackingResult> = mutableState

    override fun setZero() {
        isZeroed = true
    }

    override fun analyze(
        gray: Mat,
        rotationDegrees: Int,
        timestampMillis: Long,
        intrinsics: CameraIntrinsics?,
        gravityCamera: DoubleArray?,
        rotationDeviceFrame: DoubleArray?,
    ) {
        if (!isZeroed) {
            mutableState.value = notZeroedResult(gray.cols(), gray.rows(), rotationDegrees, timestampMillis)
            return
        }

        // Always report tracking (never falls into AimViewModel's stale/gyro-fallback path) — that
        // path's timeout is meant for a brief *vision* dropout, and would otherwise discard this
        // pose ~2.5s after every zero.
        mutableState.value = TrackingResult(
            isTracking = true,
            pose = ZERO_POSE,
            frameWidth = gray.cols(),
            frameHeight = gray.rows(),
            rotationDegrees = rotationDegrees,
            timestampMillis = timestampMillis,
        )
    }

    override fun reset() {
        isZeroed = false
    }

    private fun notZeroedResult(
        frameWidth: Int = 0,
        frameHeight: Int = 0,
        rotationDegrees: Int = 0,
        timestampMillis: Long = 0L,
    ) = TrackingResult(
        isTracking = false,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        rotationDegrees = rotationDegrees,
        timestampMillis = timestampMillis,
        statusMessage = "영점을 설정하세요",
    )

    private companion object {
        val ZERO_POSE = PoseEstimate(
            rvec = doubleArrayOf(0.0, 0.0, 0.0),
            tvec = doubleArrayOf(0.0, 0.0, NoMarkerConfig.NOMINAL_STANDOFF_MM),
        )
    }
}
