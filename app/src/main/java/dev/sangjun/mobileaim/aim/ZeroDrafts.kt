package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.settings.ZoomZero

/**
 * Zero edits made in the adjusting mode but not saved yet, keyed by profile id and zoom level.
 * They show on screen right away but only reach settings when the user saves them on leaving the
 * mode (see [AimViewModel.saveZeroDrafts]), so a stray touch while adjusting can't silently
 * overwrite a zero. Kept in memory only — closing the app drops them.
 */
data class ZeroDrafts(val zeros: Map<Pair<String, Int>, ZoomZero> = emptyMap()) {
    val isEmpty: Boolean get() = zeros.isEmpty()

    fun isUnsaved(profileId: String, zoomLevel: Int): Boolean = (profileId to zoomLevel) in zeros

    /** The draft if there is one, else [saved]. */
    fun zeroFor(profileId: String, zoomLevel: Int, saved: ZoomZero?): ZoomZero? = zeros[profileId to zoomLevel] ?: saved

    /** A drag moves the zero to the dragged position and resets the clicks — same rule as before drafts. */
    fun drag(profileId: String, zoomLevel: Int, xFraction: Float, yFraction: Float): ZeroDrafts =
        copy(zeros = zeros + ((profileId to zoomLevel) to ZoomZero(xFraction, yFraction)))

    /** Clicks start from the draft, else the [saved] zero, else screen center. */
    fun click(profileId: String, zoomLevel: Int, saved: ZoomZero?, elevationDelta: Int, windageDelta: Int): ZeroDrafts {
        val base = zeroFor(profileId, zoomLevel, saved) ?: ZoomZero()
        val updated = base.copy(
            elevationClicks = base.elevationClicks + elevationDelta,
            windageClicks = base.windageClicks + windageDelta,
        )
        return copy(zeros = zeros + ((profileId to zoomLevel) to updated))
    }
}
