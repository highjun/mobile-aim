package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.settings.AimProfile
import dev.sangjun.mobileaim.app.settings.DefaultAimProfiles
import dev.sangjun.mobileaim.app.settings.DefaultEnabledZoomLevels
import dev.sangjun.mobileaim.app.settings.ReticleSize

/**
 * Reticle state as fractions (0..1) of the camera preview, for the active profile at [zoomLevel].
 *
 * [reticleXFraction]/[reticleYFraction]: where the reticle was last dragged to at this zoom (see
 * [dev.sangjun.mobileaim.app.settings.ZoomZero]) — screen center when [isZeroed] is false.
 * Unaffected by [elevationClicks]/[windageClicks].
 *
 * [effectiveReticleXFraction]/[effectiveReticleYFraction]: that position plus the click adjustment
 * (see [clicksToFraction]) — where the reticle is actually drawn, and what
 * [AimViewModel.logCaptureSnapshot] records. Equals the dragged position whenever
 * [isCreepAvailable] is false (no [dev.sangjun.mobileaim.app.camera.CameraFov] yet to convert
 * clicks into a screen fraction with).
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
    /** The header's selected zoom level (1..25, see [dev.sangjun.mobileaim.app.settings.ZoomLevels]) — picks which zero is in use. Not the same as [dev.sangjun.mobileaim.app.camera.CameraFov.zoomRatio], which is what CameraX actually settled on. */
    val zoomLevel: Int = 1,
    /** Whether the active profile has a zero at [zoomLevel] — only shown in the zero info (stored coordinates or "—"). */
    val isZeroed: Boolean = false,
    /** Whether the zero shown at [zoomLevel] is an unsaved adjusting-mode edit (see [ZeroDrafts]). */
    val isZeroUnsaved: Boolean = false,
    /** Whether any profile/zoom has an unsaved zero — leaving the adjusting mode asks to save them. */
    val hasUnsavedZeros: Boolean = false,
    /** Zoom levels the header's zoom picker lists (see [dev.sangjun.mobileaim.app.settings.MarkerSettings.enabledZoomLevels]). */
    val enabledZoomLevels: List<Int> = DefaultEnabledZoomLevels,
    val reticleSize: ReticleSize = ReticleSize.Medium,
    /** One creep-pad click in millimetres at 25m (see [dev.sangjun.mobileaim.app.settings.MarkerSettings.clickSizeMm]). */
    val clickSizeMm: Float = DefaultClickSizeMm,
    val profiles: List<AimProfile> = DefaultAimProfiles,
    val activeProfileId: String = DefaultAimProfiles.first().id,
    /** Whether a [dev.sangjun.mobileaim.app.camera.CameraFov] is available yet — gates the creep pad's arrow buttons (see [AimOverlay]) so a press doesn't silently do nothing. */
    val isCreepAvailable: Boolean = false,
) {
    /** [profiles] entry matching [activeProfileId] — falls back to a placeholder label if it's somehow missing (shouldn't happen; [dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository] keeps them in sync). */
    val activeProfileName: String
        get() = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "프로파일"
}
