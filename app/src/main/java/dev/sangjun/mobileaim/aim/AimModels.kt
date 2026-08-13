package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.driftdetection.DriftStatus
import dev.sangjun.mobileaim.driftdetection.IDriftMonitorService
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.utils.Point2Px

/** One rung of the ballistic-reticle ladder: a preset range and where it currently projects to. */
data class RangeReticlePoint(val rangeMeters: Float, val imagePoint: Point2Px)

data class AimUiState(
    val isMarkerDetected: Boolean = false,
    /** In analysis-frame pixel space; [AimOverlay] maps this into the displayed Canvas rect. */
    val reticleImagePoint: Point2Px? = null,
    /** Detected marker corners (TL, TR, BR, BL) in the same frame pixel space, for the overlay. */
    val markerCorners: List<Point2Px> = emptyList(),
    /**
     * [RangeSliderConfig.SNAP_POINTS_METERS] all projected against the current pose, for the
     * ballistic-reticle ladder — the one matching [rangeMeters] is drawn as the active aim point.
     */
    val rangeReticlePoints: List<RangeReticlePoint> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** Absolute aim distance in meters — what the slider shows and what the projection uses. */
    val rangeMeters: Float = RangeSliderConfig.DEFAULT_Z0_METERS,
    /** Current effective offsets/board size (override if the test panel is active, else Settings). */
    val xOffsetMm: Double = MarkerConfig.DEFAULT_X_OFFSET_MM,
    val yOffsetMm: Double = MarkerConfig.DEFAULT_Y_OFFSET_MM,
    val boardSizeMm: Double = MarkerConfig.DEFAULT_BOARD_SIZE_MM,
    /**
     * Current effective Z0 seed (test-panel override if active, else Settings) — distinct from
     * [rangeMeters], which can diverge from this once the top slider is dragged again.
     */
    val z0Meters: Float = RangeSliderConfig.DEFAULT_Z0_METERS,
    /**
     * Non-null only once a miss has gone on long enough that the pose itself is given up on (see
     * [PoseMemoryPolicy]) — null during a brief detection gap even though [isMarkerDetected] is
     * already false, so the warning badge doesn't flicker on every dropped frame.
     */
    val errorMessage: String? = null,
    /** Independent of [isMarkerDetected]/[errorMessage] — see [dev.sangjun.mobileaim.driftdetection.IDriftMonitorService]. */
    val driftStatus: DriftStatus = DriftStatus(),
)

internal data class CameraContext(val cameraId: String)
