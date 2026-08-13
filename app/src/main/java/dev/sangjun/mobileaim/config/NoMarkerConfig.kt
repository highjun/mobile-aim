package dev.sangjun.mobileaim.config

/**
 * No-marker mode tracks only rotation (gyro delta since the user's zero) — there is no vision-based
 * distance estimate to feed the pose's translation, so it stays pinned at an arbitrary constant
 * within [MarkerConfig.EXPECTED_DISTANCE_RANGE_MM]. This only affects internal solvePnP-style
 * conditioning, not the displayed range: the ballistic ladder's actual distance comes from the
 * *target point* ([MarkerConfig.targetLocalPointMm]'s range parameter), not from this pose's tvec.
 */
object NoMarkerConfig {
    const val NOMINAL_STANDOFF_MM = 300.0
}
