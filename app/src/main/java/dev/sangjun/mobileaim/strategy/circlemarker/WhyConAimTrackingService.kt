package dev.sangjun.mobileaim.strategy.circlemarker

import dev.sangjun.mobileaim.strategy.IAimTrackingService
import dev.sangjun.mobileaim.strategy.TrackingResult
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.utils.Point2Px
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Stage 1 (see the MobileAim plan's M4): a single circle only constrains position + plane normal
 * (5 of 6 pose DOF) — rotation about the normal ("roll") isn't observable from one circle.
 * [poseFor] picks a *canonical* roll by keeping the marker's local "up" as close as possible to
 * the camera's own up direction, rather than any physically-real reference on the marker. That
 * means the ballistic ladder's left/right (X) axis has no real meaning here — see
 * [dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig] — only the range-driven Y (elevation) offset is
 * meaningful until a real roll reference is added (gravity-based roll fixing, or a WhyCode-style
 * necklace-coded marker — both deferred; see the plan's Stage 2/3).
 */
@Deprecated("v1에서 비전/마커 추적을 제거함 — 수동 드래그 조준은 aim/AimViewModel 참고")
class WhyConAimTrackingService(
    private val detector: WhyConDetector = WhyConDetector(),
) : IAimTrackingService {
    private val mutableState = MutableStateFlow(notTracking())
    override val state: StateFlow<TrackingResult> = mutableState

    private var markerDiameterMm: Double = WhyConConfig.DEFAULT_MARKER_DIAMETER_MM
    private var previousPositionMm: DoubleArray? = null

    override fun updateMarkerSizeMm(markerSizeMm: Double) {
        markerDiameterMm = markerSizeMm
    }

    override fun analyze(
        gray: Mat,
        rotationDegrees: Int,
        timestampMillis: Long,
        intrinsics: CameraIntrinsics?,
        gravityCamera: DoubleArray?,
        rotationDeviceFrame: DoubleArray?,
    ) {
        if (intrinsics == null) {
            mutableState.value = notTracking(gray.cols(), gray.rows(), rotationDegrees, timestampMillis, "Camera calibration unavailable")
            return
        }
        val ellipse = detector.detect(gray)
        if (ellipse == null) {
            mutableState.value = notTracking(gray.cols(), gray.rows(), rotationDegrees, timestampMillis, "Marker not found")
            return
        }

        val conic = ConicMath.ellipseToConic(ellipse)
        val candidates = CirclePoseRecovery.recover(conic, intrinsics, markerDiameterMm / 2.0)
        val chosen = WhyConPoseDisambiguator.select(candidates, previousPositionMm)
        if (chosen == null) {
            mutableState.value = notTracking(gray.cols(), gray.rows(), rotationDegrees, timestampMillis, "Unable to compute marker pose")
            return
        }
        previousPositionMm = chosen.positionMm

        mutableState.value = TrackingResult(
            isTracking = true,
            pose = poseFor(chosen),
            visualizationPoints = ellipseVisualizationPoints(ellipse),
            frameWidth = gray.cols(),
            frameHeight = gray.rows(),
            rotationDegrees = rotationDegrees,
            timestampMillis = timestampMillis,
        )
    }

    override fun reset() {
        previousPositionMm = null
    }

    /**
     * Builds a full rotation from just the recovered normal: local Z (away from camera, matching
     * ChArUco's axis convention — see [dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig]) is `-normal`;
     * local Y is the camera's own "down" (image +Y) projected onto the plane perpendicular to
     * that Z — the canonical-roll choice described in the class doc.
     */
    private fun poseFor(candidate: CirclePoseCandidate): PoseEstimate {
        val localZ = DoubleArray(3) { -candidate.normal[it] }
        val cameraDown = doubleArrayOf(0.0, 1.0, 0.0)
        // If the marker is viewed edge-on enough that "camera down" is nearly parallel to its
        // normal, that reference is unusable (near-zero perpendicular component) — fall back to
        // camera-right instead.
        val referenceAxis = if (abs(dot(cameraDown, localZ)) < 0.95) cameraDown else doubleArrayOf(1.0, 0.0, 0.0)
        val localYRaw = DoubleArray(3) { referenceAxis[it] - dot(referenceAxis, localZ) * localZ[it] }
        val localY = normalize(localYRaw)
        val localX = cross(localY, localZ)

        val rotationMat = Mat(3, 3, CvType.CV_64F).apply {
            for (row in 0..2) put(row, 0, localX[row], localY[row], localZ[row])
        }
        val rvecMat = Mat()
        val rvec = try {
            Calib3d.Rodrigues(rotationMat, rvecMat)
            DoubleArray(3).also { rvecMat.get(0, 0, it) }
        } finally {
            rotationMat.release()
            rvecMat.release()
        }
        return PoseEstimate(rvec = rvec, tvec = candidate.positionMm)
    }

    private fun ellipseVisualizationPoints(ellipse: RotatedEllipse): List<Point2Px> {
        val cosA = cos(ellipse.angleRad)
        val sinA = sin(ellipse.angleRad)
        return (0 until 360 step 30).map { deg ->
            val t = Math.toRadians(deg.toDouble())
            val lx = ellipse.a * cos(t)
            val ly = ellipse.b * sin(t)
            Point2Px(
                (ellipse.cx + lx * cosA - ly * sinA).toFloat(),
                (ellipse.cy + lx * sinA + ly * cosA).toFloat(),
            )
        }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun normalize(v: DoubleArray): DoubleArray {
        val norm = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return DoubleArray(3) { v[it] / norm }
    }

    private fun notTracking(
        frameWidth: Int = 0,
        frameHeight: Int = 0,
        rotationDegrees: Int = 0,
        timestampMillis: Long = 0L,
        statusMessage: String? = null,
    ) = TrackingResult(
        isTracking = false,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        rotationDegrees = rotationDegrees,
        timestampMillis = timestampMillis,
        statusMessage = statusMessage,
    )
}
