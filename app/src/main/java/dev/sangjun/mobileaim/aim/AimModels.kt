package dev.sangjun.mobileaim.aim

/** Reticle position as a fraction (0..1) of the camera preview — the user drags it here and it stays put. */
data class AimUiState(
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    /** Whether the IMU CSV log + camera video recording are both currently running (see [dev.sangjun.mobileaim.aim.AimViewModel.toggleLogging]). */
    val isLoggingActive: Boolean = false,
)
