package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.settings.AimProfile
import dev.sangjun.mobileaim.app.settings.DefaultAimProfiles

/**
 * Reticle state as fractions (0..1) of the camera preview.
 *
 * [reticleXFraction]/[reticleYFraction]: the active profile's bore-sighted zero — what
 * [AimOverlay] draws the zeroing-mode dot at, and what dragging while zeroing edits (see
 * [AimViewModel.onReticleDrag]). Unaffected by [elevationClicks]/[windageClicks].
 *
 * [effectiveReticleXFraction]/[effectiveReticleYFraction]: the zero plus the click adjustment
 * applied (see [clicksToFraction]) — where the aiming reticle is actually drawn, and what
 * [AimViewModel.logCaptureSnapshot] records. Equals the raw zero whenever [isCreepAvailable] is
 * false (no [dev.sangjun.mobileaim.app.camera.CameraFov] yet to convert clicks into a screen
 * fraction with).
 *
 * [mpbrBandM]: the active profile's valid-range band in meters (see [computeMpbrBand]), or `null`
 * if its trajectory can't be reduced to a single band — [AimOverlay] omits the band line then.
 */
data class AimUiState(
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    val effectiveReticleXFraction: Float = 0.5f,
    val effectiveReticleYFraction: Float = 0.5f,
    val elevationClicks: Int = 0,
    val windageClicks: Int = 0,
    /** Whether the IMU CSV log + camera video recording are both currently running (see [dev.sangjun.mobileaim.aim.AimViewModel.toggleLogging]). */
    val isLoggingActive: Boolean = false,
    /** Manual screen brightness (0..1) the app forces on its window, replacing system auto-brightness. */
    val brightness: Float = 0.8f,
    val profiles: List<AimProfile> = DefaultAimProfiles,
    val activeProfileId: String = DefaultAimProfiles.first().id,
    val zeroDistanceM: Float = DefaultAimProfiles.first().zeroDistanceM,
    val mpbrBandM: ClosedRange<Float>? = null,
    /** Whether a [dev.sangjun.mobileaim.app.camera.CameraFov] is available yet — gates the creep pad's arrow buttons (see [AimOverlay]) so a press doesn't silently do nothing. */
    val isCreepAvailable: Boolean = false,
) {
    /** [profiles] entry matching [activeProfileId] — falls back to a placeholder label if it's somehow missing (shouldn't happen; [dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository] keeps them in sync). */
    val activeProfileName: String
        get() = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "프로파일"
}
