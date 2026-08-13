package dev.sangjun.mobileaim.driftdetection

/**
 * [medianResidualPx] is only meaningful when [matchCount] clears [dev.sangjun.mobileaim.driftdetection.DriftConfig.MIN_GOOD_MATCHES]
 * — below that, there isn't enough signal to say anything, and [isDrifting] stays false rather
 * than guessing.
 */
data class DriftStatus(
    val hasReference: Boolean = false,
    val matchCount: Int = 0,
    val medianResidualPx: Float = 0f,
    val isDrifting: Boolean = false,
)
