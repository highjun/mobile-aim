package dev.sangjun.mobileaim.app.settings

import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<MarkerSettings>

    /** Updates the shared zero both [AimMode]s draw from — see [MarkerSettings]. */
    fun updateReticlePosition(xFraction: Float, yFraction: Float)

    fun updateAimMode(mode: AimMode)

    fun updateManualGunVelocity(velocityMps: Float)

    fun updateManualGunDistance(distanceMeters: Float)

    /** Persists the manual screen brightness (0..1) the app forces on its window. */
    fun updateBrightness(brightness: Float)
}
