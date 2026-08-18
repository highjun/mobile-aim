package dev.sangjun.mobileaim.app.settings

/** Reticle position the user set by dragging it onto the laser dot — a fraction (0..1) of the camera preview. */
data class MarkerSettings(
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
)
