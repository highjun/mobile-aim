package dev.sangjun.mobileaim.app.settings

/**
 * Persisted user settings.
 *
 * [reticleXFraction]/[reticleYFraction]: reticle position the user set by dragging it onto the
 * laser dot — a fraction (0..1) of the camera preview.
 *
 * [brightness]: the manual screen brightness (0..1) the app forces on its window, which also
 * disables the system's auto-brightness for this app. Defaults high so an outdoor screen starts
 * bright rather than dimmed by auto-brightness — see [DEFAULT_BRIGHTNESS].
 */
data class MarkerSettings(
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    val brightness: Float = DEFAULT_BRIGHTNESS,
) {
    companion object {
        const val DEFAULT_BRIGHTNESS = 0.8f
    }
}
