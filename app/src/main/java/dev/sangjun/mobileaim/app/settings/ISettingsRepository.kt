package dev.sangjun.mobileaim.app.settings

import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<MarkerSettings>

    fun updateReticlePosition(xFraction: Float, yFraction: Float)

    /** Persists the manual screen brightness (0..1) the app forces on its window. */
    fun updateBrightness(brightness: Float)
}
