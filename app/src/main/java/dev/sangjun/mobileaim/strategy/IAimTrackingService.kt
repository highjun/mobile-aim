package dev.sangjun.mobileaim.strategy

import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.IMarkerDetectionService
import dev.sangjun.mobileaim.strategy.checkermarker.IPoseEstimationService
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.utils.GyroPosePredictor
import dev.sangjun.mobileaim.utils.Point2Px
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat

/**
 * A strategy for turning a camera frame into a [PoseEstimate] — detection and pose estimation
 * merged into one step per strategy, rather than the old two-stage
 * [dev.sangjun.mobileaim.strategy.checkermarker.IMarkerDetectionService] + IPoseEstimationService split.
 * That split assumed every strategy detects discrete point correspondences and feeds them to
 * solvePnP; WhyCon-style circular markers instead decompose a single conic (and need the camera
 * matrix at detection time to do it), and the no-marker strategy doesn't look at the frame at
 * all. [PoseEstimate] (rvec/tvec) is kept as the one thing every strategy must still produce,
 * since [dev.sangjun.mobileaim.aim.AimViewModel]'s smoothing/ladder-projection/stale-pose
 * prediction only consume that and don't care how it was obtained.
 */
@Deprecated("v1은 모드가 하나(수동 드래그)뿐이라 전략 전환 추상화가 불필요해짐")
interface IAimTrackingService {
    val state: StateFlow<TrackingResult>

    /**
     * [gray] is an already rotation-corrected grayscale frame — analyze reads it but does not
     * release it (the caller owns its lifecycle). [intrinsics]/[gravityCamera] are already in
     * camera-optical-frame form; [rotationDeviceFrame] is the raw device-sensor-frame rotation
     * (a strategy that needs a *change* in orientation, not a single reading, converts it itself
     * relative to its own reference — see [dev.sangjun.mobileaim.utils.GyroPosePredictor]).
     * Any of these may be null when not yet available; a strategy that doesn't need one ignores it.
     */
    fun analyze(
        gray: Mat,
        rotationDegrees: Int,
        timestampMillis: Long,
        intrinsics: CameraIntrinsics?,
        gravityCamera: DoubleArray?,
        rotationDeviceFrame: DoubleArray?,
    )

    /** Forgets any state kept between calls, so a later re-acquisition isn't biased by stale data. */
    fun reset()

    /**
     * The physical size (mm) of this strategy's marker, if it has one — e.g. ChArUco's board side
     * length. No-op default: strategies without a sized physical marker (no-marker mode) ignore it.
     */
    fun updateMarkerSizeMm(markerSizeMm: Double) {}
}

data class TrackingResult(
    val isTracking: Boolean,
    val pose: PoseEstimate? = null,
    /** Points to draw as detection feedback (ChArUco corners, WhyCon ellipse samples, ...). */
    val visualizationPoints: List<Point2Px> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val timestampMillis: Long = 0L,
    val statusMessage: String? = null,
)
