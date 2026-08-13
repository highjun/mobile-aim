package dev.sangjun.mobileaim.feature.tracking

import dev.sangjun.mobileaim.config.MarkerConfig
import dev.sangjun.mobileaim.feature.geometry.Point3Mm
import dev.sangjun.mobileaim.feature.marker.IMarkerDetectionService
import dev.sangjun.mobileaim.feature.pose.CameraIntrinsics
import dev.sangjun.mobileaim.feature.pose.IPoseEstimationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat
import org.opencv.objdetect.CharucoBoard

/**
 * Wraps ChArUco marker detection + solvePnP pose estimation behind [IAimTrackingService]. Owns
 * the cornerId -> 3D mm resolution that used to live in
 * [dev.sangjun.mobileaim.feature.aim.AimViewModel] (see [MarkerConfig]'s board geometry docs) —
 * that resolution is ChArUco-specific (an id only means something against this board's own
 * chessboardCorners table), so it doesn't belong in a strategy-agnostic ViewModel.
 */
class CharucoAimTrackingService(
    private val markerService: IMarkerDetectionService,
    private val poseService: IPoseEstimationService,
) : IAimTrackingService {
    private val mutableState = MutableStateFlow(TrackingResult(isTracking = false))
    override val state: StateFlow<TrackingResult> = mutableState

    private var markerSizeMm: Double = MarkerConfig.DEFAULT_BOARD_SIZE_MM
    private var cachedBoard: CharucoBoard? = null
    private var cachedBoardSizeMm: Double? = null

    override fun updateMarkerSizeMm(markerSizeMm: Double) {
        this.markerSizeMm = markerSizeMm
    }

    override fun analyze(
        gray: Mat,
        rotationDegrees: Int,
        timestampMillis: Long,
        intrinsics: CameraIntrinsics?,
        gravityCamera: DoubleArray?,
        rotationDeviceFrame: DoubleArray?,
    ) {
        val markerResult = markerService.analyze(gray, rotationDegrees, timestampMillis)
        if (!markerResult.isDetected) {
            mutableState.value = notTracking(markerResult.frameWidth, markerResult.frameHeight, rotationDegrees, timestampMillis, markerResult.statusMessage)
            return
        }
        if (intrinsics == null) {
            mutableState.value = notTracking(markerResult.frameWidth, markerResult.frameHeight, rotationDegrees, timestampMillis, "Camera calibration unavailable")
            return
        }

        val chessboardCorners = boardFor(markerSizeMm).chessboardCorners
        val objectPoints = markerResult.cornerIds.mapNotNull { id -> boardCornerMm(chessboardCorners, id) }
        if (objectPoints.size != markerResult.cornerIds.size) {
            mutableState.value = notTracking(markerResult.frameWidth, markerResult.frameHeight, rotationDegrees, timestampMillis, "Unresolved board corner id")
            return
        }

        val pose = poseService.estimatePose(objectPoints, markerResult.imagePoints, intrinsics, gravityCamera)
        mutableState.value = if (pose != null) {
            TrackingResult(
                isTracking = true,
                pose = pose,
                visualizationPoints = markerResult.imagePoints,
                frameWidth = markerResult.frameWidth,
                frameHeight = markerResult.frameHeight,
                rotationDegrees = rotationDegrees,
                timestampMillis = timestampMillis,
            )
        } else {
            notTracking(markerResult.frameWidth, markerResult.frameHeight, rotationDegrees, timestampMillis, "Unable to compute marker pose")
        }
    }

    override fun reset() = poseService.reset()

    private fun notTracking(
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
        timestampMillis: Long,
        statusMessage: String?,
    ) = TrackingResult(
        isTracking = false,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        rotationDegrees = rotationDegrees,
        timestampMillis = timestampMillis,
        statusMessage = statusMessage,
    )

    private fun boardFor(boardSizeMm: Double): CharucoBoard {
        cachedBoard?.let { if (cachedBoardSizeMm == boardSizeMm) return it }
        return MarkerConfig.charucoBoard(boardSizeMm).also {
            cachedBoard = it
            cachedBoardSizeMm = boardSizeMm
        }
    }

    private fun boardCornerMm(chessboardCorners: Mat, cornerId: Int): Point3Mm? {
        if (cornerId < 0 || cornerId >= chessboardCorners.rows()) return null
        val xyz = chessboardCorners.get(cornerId, 0)
        return Point3Mm(xyz[0].toDouble(), xyz[1].toDouble(), xyz[2].toDouble())
    }
}
