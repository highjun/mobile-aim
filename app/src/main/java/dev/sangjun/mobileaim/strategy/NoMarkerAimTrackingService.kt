package dev.sangjun.mobileaim.strategy

import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.utils.GyroPosePredictor
import dev.sangjun.mobileaim.utils.RotationVectorFrameConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat

/**
 * Tracks aim from gyroscope rotation alone — no camera frame is analyzed. [setZero] anchors a
 * pose the moment the user taps "영점" (the same role a physical scope's zero knobs play); every
 * later frame replays [GyroPosePredictor]'s existing gap-filling math (normally used to bridge a
 * brief *vision* detection gap in the marker-based strategies) starting from that anchor instead
 * of a real solvePnP pose. Distance can't be recovered from rotation alone, so the pose's
 * translation stays pinned at [NoMarkerConfig.NOMINAL_STANDOFF_MM] — the ballistic ladder still
 * works because range comes from the *target point*
 * ([dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig.targetLocalPointMm]), not from this pose's tvec.
 */
class NoMarkerAimTrackingService : IAimTrackingService, ZeroableAimTracking {
    private var zeroPose: PoseEstimate? = null
    private var zeroRotationDeviceFrame: DoubleArray? = null
    private var zeroRotationDegrees: Int = 0

    private val mutableState = MutableStateFlow(notZeroedResult())
    override val state: StateFlow<TrackingResult> = mutableState

    override fun setZero(rotationDeviceFrame: DoubleArray, rotationDegrees: Int) {
        zeroRotationDeviceFrame = rotationDeviceFrame
        zeroRotationDegrees = rotationDegrees
        zeroPose = PoseEstimate(
            rvec = doubleArrayOf(0.0, 0.0, 0.0),
            tvec = doubleArrayOf(0.0, 0.0, NoMarkerConfig.NOMINAL_STANDOFF_MM),
        )
    }

    override fun analyze(
        gray: Mat,
        rotationDegrees: Int,
        timestampMillis: Long,
        intrinsics: CameraIntrinsics?,
        gravityCamera: DoubleArray?,
        rotationDeviceFrame: DoubleArray?,
    ) {
        val zero = zeroPose
        val zeroRotationDeviceFrame = zeroRotationDeviceFrame
        if (zero == null || zeroRotationDeviceFrame == null || rotationDeviceFrame == null) {
            mutableState.value = notZeroedResult(gray.cols(), gray.rows(), rotationDegrees, timestampMillis)
            return
        }

        val qOld = RotationVectorFrameConverter.toCameraFrame(zeroRotationDeviceFrame, zeroRotationDegrees)
        val qNew = RotationVectorFrameConverter.toCameraFrame(rotationDeviceFrame, rotationDegrees)
        val predicted = GyroPosePredictor.predict(zero, qOld, qNew)

        // Always report tracking (never falls into AimViewModel's stale/gyro-fallback path) — that
        // path's timeout is meant for a brief *vision* dropout, and would otherwise discard this
        // pose ~2.5s after every zero.
        mutableState.value = TrackingResult(
            isTracking = true,
            pose = predicted,
            frameWidth = gray.cols(),
            frameHeight = gray.rows(),
            rotationDegrees = rotationDegrees,
            timestampMillis = timestampMillis,
        )
    }

    override fun reset() {
        zeroPose = null
        zeroRotationDeviceFrame = null
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
}
