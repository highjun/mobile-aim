package dev.sangjun.mobileaim.aim

import kotlin.math.tan

/**
 * Every reticle click nudges by this angle — 7mm at 25m (0.28 mrad, about 0.96 MOA), one square of
 * the army's 25m zeroing target and about one K2 rear-sight click. Click counts can then be read
 * straight off the target: a group 3 squares low takes 3 clicks up. See [clicksToFraction].
 */
const val RadiansPerClick = 0.007f / 25f

/**
 * Converts a signed click count into a screen-fraction offset, along whichever axis the caller's
 * [focalLengthPx]/[referenceLengthPx] pair describes — elevation uses the camera's vertical pair
 * ([dev.sangjun.mobileaim.app.camera.CameraFov.focalLengthYPx] /
 * [dev.sangjun.mobileaim.app.camera.CameraFov.referenceHeightPx]), windage uses the horizontal
 * pair ([dev.sangjun.mobileaim.app.camera.CameraFov.referenceWidthPx]). Both values in a pair must
 * be in the same coordinate system for the result to mean anything.
 *
 * Doesn't decide sign convention (which direction a positive click count moves the reticle on
 * screen) — that's the caller's concern (see [dev.sangjun.mobileaim.aim.AimViewModel]).
 */
fun clicksToFraction(clicks: Int, focalLengthPx: Float, referenceLengthPx: Float): Float {
    if (clicks == 0 || referenceLengthPx <= 0f) return 0f
    val angleRad = clicks * RadiansPerClick
    return focalLengthPx * tan(angleRad) / referenceLengthPx
}
