package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.settings.AimMode

/**
 * Reticle position as a fraction (0..1) of the camera preview. [reticleXFraction]/[reticleYFraction]
 * is the shared laser zero for [AimMode.FIREARM]; for [AimMode.MANUAL_GUN] it's that same zero
 * with a computed ballistic-drop offset already folded in (see
 * [dev.sangjun.mobileaim.aim.computeDropOffsetFraction]) — the drag gesture always edits the raw
 * shared zero regardless of which mode is active, this field is what actually gets drawn.
 */
data class AimUiState(
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    /** Whether the IMU CSV log + camera video recording are both currently running (see [dev.sangjun.mobileaim.aim.AimViewModel.toggleLogging]). */
    val isLoggingActive: Boolean = false,
    /** Manual screen brightness (0..1) the app forces on its window, replacing system auto-brightness. */
    val brightness: Float = 0.8f,
    val aimMode: AimMode = AimMode.FIREARM,
    /** Currently selected engagement distance for the drop calculation — see [dev.sangjun.mobileaim.app.settings.MarkerSettings]. */
    val manualGunDistanceMeters: Float = 5f,
    /** The manual gun's configured muzzle velocity — see [dev.sangjun.mobileaim.app.settings.MarkerSettings]. */
    val manualGunVelocityMps: Float = 200f,
)
