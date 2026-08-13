package dev.sangjun.mobileaim.feature.marker

import dev.sangjun.mobileaim.feature.geometry.Point2Px

/**
 * Result of a ChArUco board detection pass. [imagePoints] holds the sub-pixel location of each
 * detected interior chessboard corner; [cornerIds] holds the matching chessboard-corner ID for
 * each entry (an index into `CharucoBoard.getChessboardCorners()`) — the two lists are parallel
 * and can be zipped to build solvePnP correspondences once a physical board size is known.
 *
 * [rotationDegrees] is the clockwise rotation the analysis pipeline applied to the buffer to
 * produce this frame — camera intrinsics have to be reoriented by the same amount.
 */
data class MarkerDetectionResult(
    val isDetected: Boolean,
    val imagePoints: List<Point2Px> = emptyList(),
    val cornerIds: List<Int> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val timestampMillis: Long = 0L,
    val statusMessage: String? = null,
)
