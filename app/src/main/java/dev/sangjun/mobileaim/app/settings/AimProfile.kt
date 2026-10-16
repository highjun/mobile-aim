package dev.sangjun.mobileaim.app.settings

/** The zoom levels the header offers (1x–8x) — each one keeps its own [ZoomZero] per profile. */
val ZoomLevels: IntRange = 1..8

/**
 * One zoom level's zero. [xFraction]/[yFraction] (0..1 across the displayed preview) are where
 * the reticle was last dragged to — onto the laser bore-sighter's dot, or straight onto a group
 * of hits. [elevationClicks]/[windageClicks] are the creep-pad clicks dialed in since that drag;
 * they move the reticle by an angle (see [dev.sangjun.mobileaim.aim.clicksToFraction]) and are
 * kept as counts so the shot log can record how far the zero was nudged. A drag resets them to 0
 * (the drag itself becomes the new starting point).
 */
data class ZoomZero(
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val elevationClicks: Int = 0,
    val windageClicks: Int = 0,
)

/**
 * A named gun — one physical setup (gun + mount) worth of zeros, one per zoom level in [zeros].
 * A zoom level missing from [zeros] hasn't been zeroed yet: the reticle sits at screen center and
 * shooting mode stays unavailable for it (see [dev.sangjun.mobileaim.aim.AimUiState.isZeroed]).
 *
 * Each zoom keeps its own zero instead of reprojecting one zero across zooms: on a multi-lens
 * phone (Galaxy S25 Ultra) CameraX may switch to a telephoto lens as zoom goes up, and that lens's
 * optical axis differs from the main lens's, so a zero captured at one zoom doesn't carry over.
 */
data class AimProfile(
    val id: String,
    val name: String,
    val zeros: Map<Int, ZoomZero> = emptyMap(),
) {
    fun zeroFor(zoomLevel: Int): ZoomZero? = zeros[zoomLevel]
}

const val FirearmProfileId = "firearm"
const val ManualGunProfileId = "manual_gun"

/** Seeded on first run and by the v0→v1 settings migration (see [migrateAimModeToProfiles]). */
val DefaultAimProfiles = listOf(
    AimProfile(
        id = FirearmProfileId,
        name = "화기",
    ),
    AimProfile(
        id = ManualGunProfileId,
        name = "수동건",
    ),
)
