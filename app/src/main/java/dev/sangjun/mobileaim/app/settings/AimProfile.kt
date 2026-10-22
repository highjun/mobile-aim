package dev.sangjun.mobileaim.app.settings

/**
 * Every zoom level the app knows (1x–25x) — each one keeps its own [ZoomZero] per profile. The
 * header only lists the ones the user turned on ([MarkerSettings.enabledZoomLevels]) and the
 * camera can reach (Galaxy S25 Ultra's API maximum is 10x).
 */
val ZoomLevels: IntRange = 1..25

/** Turned on for a fresh install — the 1x–8x the header offered before the range grew. */
val DefaultEnabledZoomLevels: List<Int> = (1..8).toList()

/**
 * Reticle size steps — only the ring, the outer ticks and their thickness change; the aim point
 * (where the thin center lines cross) stays put, so a bigger reticle doesn't add aiming error.
 */
enum class ReticleSize { Small, Medium, Large }

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
 * A zoom level missing from [zeros] hasn't been zeroed yet: the reticle sits at screen center (see
 * [dev.sangjun.mobileaim.aim.AimUiState.isZeroed]).
 *
 * Each zoom keeps its own zero instead of reprojecting one zero across zooms: on a multi-lens
 * phone (Galaxy S25 Ultra) CameraX may switch to a telephoto lens as zoom goes up, and that lens's
 * optical axis differs from the main lens's, so a zero captured at one zoom doesn't carry over.
 *
 * [lastZoomLevel] is the zoom this gun was last used at — switching to the profile switches the
 * camera to it, so picking a gun brings back its zoom and that zoom's zero together. Null for a
 * profile never used yet (the current zoom stays).
 */
data class AimProfile(
    val id: String,
    val name: String,
    val zeros: Map<Int, ZoomZero> = emptyMap(),
    val lastZoomLevel: Int? = null,
) {
    fun zeroFor(zoomLevel: Int): ZoomZero? = zeros[zoomLevel]
}

const val FirearmProfileId = "firearm"
const val ManualGunProfileId = "manual_gun"

/**
 * Seeded on first run and by the v0→v1 settings migration (see [migrateAimModeToProfiles]). The
 * first one is selected on a fresh install. The ids predate the names (화기 → K2C1, 수동건 →
 * 에어소프트) and stay as they are so saved data keeps matching.
 */
val DefaultAimProfiles = listOf(
    AimProfile(
        id = ManualGunProfileId,
        name = "에어소프트",
    ),
    AimProfile(
        id = FirearmProfileId,
        name = "K2C1",
    ),
)
