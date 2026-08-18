package dev.sangjun.mobileaim.aim

/** Reticle position as a fraction (0..1) of the camera preview — the user drags it here and it stays put. */
data class AimUiState(
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
)
