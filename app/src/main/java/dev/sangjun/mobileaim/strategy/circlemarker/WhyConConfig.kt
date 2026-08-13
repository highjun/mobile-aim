package dev.sangjun.mobileaim.strategy.circlemarker

import dev.sangjun.mobileaim.strategy.IAimTrackingService

/**
 * Marker: a black ring with a white inner circle (concentric, achromatic — same detection logic
 * works under any color cast). Geometry constants describe the *printed* marker; physical overall
 * size is user-configurable at runtime (see [dev.sangjun.mobileaim.strategy.IAimTrackingService.updateMarkerSizeMm]),
 * same as ChArUco's board size.
 */
object WhyConConfig {
    /** Printed inner/outer *radius* ratio — the projected area ratio should be this squared. */
    const val INNER_OUTER_RADIUS_RATIO = 0.5

    /** Fractional slack around the expected (radius ratio)^2 area ratio, for print/lighting noise. */
    const val AREA_RATIO_TOLERANCE = 0.3

    /** `4*pi*area/perimeter^2`; 1.0 is a perfect circle. Print/detection noise won't hit that exactly. */
    const val MIN_CIRCULARITY = 0.85

    const val MIN_CONTOUR_AREA_PX = 20.0

    /** How close two candidate contours' centres must be (px) to count as "concentric". */
    const val MAX_CENTER_OFFSET_PX = 4.0

    /** Outer circle diameter — same default/max scale as ChArUco's board for a fair comparison rig. */
    const val DEFAULT_MARKER_DIAMETER_MM = 50.0
    const val MAX_MARKER_DIAMETER_MM = 60.0
}
