package dev.sangjun.mobileaim.app.settings

import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<MarkerSettings>

    /**
     * Sets the active profile's zero for [zoomLevel] to the dragged position [xFraction]/[yFraction]
     * and resets that zoom's click counts to 0 — the drag is the new starting point, and clicks
     * dialed in before it would otherwise silently offset the reticle away from where it was just
     * dragged to (see [ZoomZero]).
     */
    fun setZero(zoomLevel: Int, xFraction: Float, yFraction: Float)

    /**
     * Nudges the active profile's click counts for [zoomLevel] (see [ZoomZero]). A zoom that has
     * no zero yet starts from screen center.
     */
    fun updateClicks(zoomLevel: Int, elevationDelta: Int, windageDelta: Int)

    /** Switches which profile is active — no-op if [profileId] doesn't match any saved profile. */
    fun setActiveProfile(profileId: String)

    /** Adds [profile] and makes it the active one. */
    fun addProfile(profile: AimProfile)

    /** Renames a profile in place — no-op if [profileId] doesn't match any saved profile. */
    fun renameProfile(profileId: String, name: String)

    /** Removes a profile; no-op if it's the last remaining one (there must always be at least one). If it was active, the first remaining profile becomes active. */
    fun deleteProfile(profileId: String)

    /** Persists the manual screen brightness (0..1) the app forces on its window. */
    fun updateBrightness(brightness: Float)

    /** Persists the selected zoom level (clamped to [ZoomLevels]) — see [MarkerSettings.zoomLevel]. */
    fun updateZoomLevel(zoomLevel: Int)

    /** Replaces [profileId]'s zero at [zoomLevel] as a whole — used when the adjusting mode's unsaved zeros are saved. */
    fun saveZero(profileId: String, zoomLevel: Int, zero: ZoomZero)

    /** Persists which zoom levels the header lists (filtered to [ZoomLevels], sorted; ignored if empty). */
    fun updateEnabledZoomLevels(zoomLevels: List<Int>)

    /** Persists the reticle size step. */
    fun updateReticleSize(size: ReticleSize)
}
