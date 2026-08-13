package dev.sangjun.mobileaim.config

object RangeSliderConfig {
    const val MIN_METERS = 5f
    const val MAX_METERS = 200f

    /**
     * 5/10 are indoor validation-test points (short range, no live target needed) — the rest are
     * the real operating range. Also drawn as tick labels under the slider.
     */
    val SNAP_POINTS_METERS = listOf(5f, 10f, 25f, 50f, 100f, 150f, 200f)
    const val SNAP_THRESHOLD_METERS = 3f

    /** Seeds Settings' Z0 on first launch; Z0 is the slider's starting position. */
    const val DEFAULT_Z0_METERS = 25f
}
