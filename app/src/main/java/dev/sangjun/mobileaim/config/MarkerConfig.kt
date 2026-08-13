package dev.sangjun.mobileaim.config

import dev.sangjun.mobileaim.feature.geometry.Point3Mm
import org.opencv.core.Size
import org.opencv.objdetect.CharucoBoard
import org.opencv.objdetect.Objdetect

/**
 * ChArUco board identity plus the rig's physical operating envelope.
 *
 * [DEFAULT_BOARD_SIZE_MM]/[DEFAULT_Y_OFFSET_MM] are placeholders — measure them against the real
 * printed board/rig. They only seed [dev.sangjun.mobileaim.feature.settings.ISettingsRepository]
 * on first launch; after that the user-editable values from Settings are the source of truth.
 *
 * ## Axis convention (empirically confirmed, not assumed)
 *
 * [CharucoBoard.getChessboardCorners] lays its points out with **local X to the right, local Y
 * DOWN the printed board, Z = 0** for every point (checked on-device: rendering a board via
 * [CharucoBoard.generateImage] and re-detecting it with [org.opencv.objdetect.ArucoDetector] shows
 * marker id 0 — the corner-id with the *smallest* object-space Y — landing at the *smallest* pixel
 * row, i.e. the top of the image). That is the opposite of this project's old single-tag ArUco
 * convention, which was hand-rolled with Y *up*.
 *
 * Working through the pinhole projection equations (`u,v` scale directly with `x,y` for `rvec =
 * identity`, `tvec = (0,0,D)`) shows this Y-down layout reproduces the real, non-mirrored render —
 * so `rvec = identity` really is "camera looking straight at the board's printed face, no tilt."
 * With local X/Y aligned to camera X/Y at identity, local +Z is parallel to camera +Z, which is
 * *forward, away from the camera* (OpenCV's pinhole convention) — so **the board's local +Z axis
 * points away from the camera, through its back**, again the opposite of the old convention.
 *
 * Anything downrange (further from the camera) is therefore +Z here, and "below the marker" is
 * +Y. [OpenCvPoseEstimationService]'s ProjectionAccuracyTest re-derives this independently from
 * synthetic ground truth — if these two disagree, trust the test, not this comment.
 */
object MarkerConfig {
    val ARUCO_DICTIONARY_ID: Int = Objdetect.DICT_4X4_50

    /** ChArUco boards up to this side length are usable; approved for a 20-40cm working distance. */
    const val MAX_BOARD_SIZE_MM: Double = 60.0
    const val DEFAULT_BOARD_SIZE_MM: Double = 50.0
    const val DEFAULT_X_OFFSET_MM: Double = 0.0
    const val DEFAULT_Y_OFFSET_MM: Double = 20.0

    /** The rig's expected camera-to-board distance; poses implying a distance outside this are suspect. */
    val EXPECTED_DISTANCE_RANGE_MM: ClosedFloatingPointRange<Double> = 200.0..400.0

    /** The rig is normally held within this tilt; used to size the gravity-disambiguation logic. */
    const val MAX_OPERATIONAL_TILT_DEGREES: Double = 15.0

    /** Final safety-net cap on the *resolved* pose's tilt — operational envelope plus headroom. */
    const val MAX_TILT_CLAMP_DEGREES: Double = 20.0

    /**
     * A square ChArUco board sized to print at [boardSizeMm] on a side. 4x4 squares (9 interior
     * corners, 8 embedded tags) — deliberately coarser than a naive 5x5: an NxN grid yields exactly
     * (N-1)x(N-1) interior corners, so 3x3 would cap out at 4 corners, the same point count as the
     * single-tag design this project moved away from (see notes on the redesign — that count is
     * what let the old single-tag planar-pose ambiguity bug reproduce). 4x4 keeps a solid margin
     * above that (9 corners) while each square/tag is ~1.33x bigger than 5x5's, directly trading
     * some of that margin for legibility at the rig's 200-400mm working distance.
     */
    fun charucoBoard(boardSizeMm: Double): CharucoBoard {
        val squareLength = (boardSizeMm / BOARD_SQUARES).toFloat()
        val markerLength = (squareLength * MARKER_TO_SQUARE_RATIO).toFloat()
        return CharucoBoard(
            Size(BOARD_SQUARES.toDouble(), BOARD_SQUARES.toDouble()),
            squareLength,
            markerLength,
            Objdetect.getPredefinedDictionary(ARUCO_DICTIONARY_ID),
        )
    }

    /**
     * The aim-point target, in the board's local mm frame, for a given absolute range in meters.
     * See the class doc for why downrange is +Z, "right of the board" is +X, and "below the board"
     * is +Y under this board's (empirically confirmed) axis convention.
     */
    fun targetLocalPointMm(absoluteRangeMeters: Float, xOffsetMm: Double, yOffsetMm: Double): Point3Mm =
        Point3Mm(xOffsetMm, yOffsetMm, absoluteRangeMeters.toDouble() * 1000.0)

    private const val BOARD_SQUARES = 4
    private const val MARKER_TO_SQUARE_RATIO = 0.7
}
