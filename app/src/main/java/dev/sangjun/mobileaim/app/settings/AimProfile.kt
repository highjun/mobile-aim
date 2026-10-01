package dev.sangjun.mobileaim.app.settings

/**
 * A named zero — one physical setup (gun + mount) worth of aiming data. Replaces the old
 * `AimMode` toggle: instead of two hardcoded equipment slots sharing a single reticle position,
 * any number of profiles can be saved/loaded, each with its own zero and click adjustments.
 *
 * [reticleXFraction]/[reticleYFraction]: the bore-sighted zero, set by dragging the reticle onto
 * the laser dot — unaffected by [elevationClicks]/[windageClicks]. A screen fraction only means
 * anything at a particular zoom level (zooming crops-and-magnifies the sensor, narrowing the field
 * of view), so it's paired with [zeroZoomRatio], the zoom that was active when it was last set —
 * see that field's KDoc.
 *
 * [elevationClicks]/[windageClicks]: fine adjustment dialed in after a confirmation shot, kept as
 * signed click counts rather than pre-converted into a screen fraction — see
 * [dev.sangjun.mobileaim.aim.clicksToFraction] for why (screen size/FOV can change; converting at
 * click time would bake today's geometry into a stored value that should stay geometry-agnostic).
 */
data class AimProfile(
    val id: String,
    val name: String,
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    /**
     * The zoom ratio that was active when [reticleXFraction]/[reticleYFraction] were last set (by
     * drag or by the boresighting arrow pad — see [dev.sangjun.mobileaim.aim.AimViewModel.onReticleDrag]).
     * A screen fraction at one zoom doesn't point at the same real-world spot at another zoom
     * (fractions scale with the zoom ratio around screen center), so displaying/using the raw zero
     * at a different current zoom requires reprojecting it by `currentZoomRatio / zeroZoomRatio`
     * around (0.5, 0.5) — see [dev.sangjun.mobileaim.aim.AimViewModel]'s `uiState`. Old saved
     * profiles predate zoom entirely, so they're read back as `1f` (equivalent to having been set
     * at 1x, which is exactly what happened).
     */
    val zeroZoomRatio: Float = 1f,
    val elevationClicks: Int = 0,
    val windageClicks: Int = 0,
)

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
