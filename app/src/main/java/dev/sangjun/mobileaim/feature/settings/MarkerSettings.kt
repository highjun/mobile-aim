package dev.sangjun.mobileaim.feature.settings

import dev.sangjun.mobileaim.feature.tracking.AimStrategy

/** User-calibrated physical values: board size, boresight offset, range preset, and active tracking strategy. */
data class MarkerSettings(
    val boardSizeMm: Double,
    val xOffsetMm: Double,
    val yOffsetMm: Double,
    val z0Meters: Float,
    val strategy: AimStrategy,
)
