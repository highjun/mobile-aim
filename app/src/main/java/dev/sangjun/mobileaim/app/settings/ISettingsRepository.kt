package dev.sangjun.mobileaim.app.settings

import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<MarkerSettings>

    /**
     * Updates the active profile's bore-sighted zero — see [AimProfile.reticleXFraction]/
     * [AimProfile.reticleYFraction]. Also resets [AimProfile.elevationClicks]/[AimProfile.windageClicks]
     * to 0: they were dialed in relative to the *old* zero, so they'd silently offset the reticle
     * away from wherever this new zero was just set to.
     */
    fun updateReticlePosition(xFraction: Float, yFraction: Float)

    /** Nudges the active profile's click counts — see [AimProfile.elevationClicks]/[AimProfile.windageClicks]. */
    fun updateClicks(elevationDelta: Int, windageDelta: Int)

    /** Switches which profile is active — no-op if [profileId] doesn't match any saved profile. */
    fun setActiveProfile(profileId: String)

    /** Adds [profile] and makes it the active one. */
    fun addProfile(profile: AimProfile)

    /** Renames a profile in place — no-op if [profileId] doesn't match any saved profile. */
    fun renameProfile(profileId: String, name: String)

    /** Removes a profile; no-op if it's the last remaining one (there must always be at least one). If it was active, the first remaining profile becomes active. */
    fun deleteProfile(profileId: String)

    /** Overwrites a profile's ballistic parameters in place — no-op if [profileId] doesn't match any saved profile. */
    fun updateProfileBallistics(profileId: String, muzzleVelocityMps: Float, zeroDistanceM: Float, hobCm: Float, targetRadiusCm: Float)

    /** Persists the manual screen brightness (0..1) the app forces on its window. */
    fun updateBrightness(brightness: Float)
}
