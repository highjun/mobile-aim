package dev.sangjun.mobileaim.aim

import kotlin.math.abs

/**
 * Clamps [raw] to [min]..[max], then magnetically snaps it to the nearest [snapPoints] entry
 * within [thresholdMeters]. Otherwise returns the clamped value unchanged (continuous elsewhere).
 */
fun snapRange(
    raw: Float,
    snapPoints: List<Float> = RangeSliderConfig.SNAP_POINTS_METERS,
    thresholdMeters: Float = RangeSliderConfig.SNAP_THRESHOLD_METERS,
    min: Float = RangeSliderConfig.MIN_METERS,
    max: Float = RangeSliderConfig.MAX_METERS,
): Float {
    val clamped = raw.coerceIn(min, max)
    val nearest = snapPoints.minByOrNull { abs(it - clamped) }
    return if (nearest != null && abs(nearest - clamped) <= thresholdMeters) nearest else clamped
}
