package dev.sangjun.mobileaim.config

/** Ranges for the on-screen X/Y/D test controls used to exercise the aim algorithm during testing. */
object TestPanelConfig {
    /** X/Y offset pad's half-extent. Started at the same order of magnitude as the max board size. */
    const val MIN_OFFSET_MM = -60f
    const val MAX_OFFSET_MM = 60f

    /** Board-size slider range. Kept near the real board (50mm default) — a value too far off breaks pose estimation. */
    const val MIN_BOARD_SIZE_MM = 30f
    const val MAX_BOARD_SIZE_MM = 60f // matches MarkerConfig.MAX_BOARD_SIZE_MM
}
