package dev.sangjun.mobileaim.aim

import kotlin.math.PI
import kotlin.math.tan

/** Every reticle click nudges by this many minutes of angle — see [clicksToFraction]. */
const val MoaPerClick = 2f

private const val ArcminutesPerDegree = 60.0
private const val RadiansPerMoa = (PI / 180.0 / ArcminutesPerDegree).toFloat()

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
    val angleRad = clicks * MoaPerClick * RadiansPerMoa
    return focalLengthPx * tan(angleRad) / referenceLengthPx
}

/**
 * Reprojects a screen fraction (along one axis) captured at [zeroZoomRatio] onto what it should be
 * at [currentZoomRatio] instead — see
 * [dev.sangjun.mobileaim.app.settings.AimProfile.zeroZoomRatio]'s KDoc for why a raw fraction only
 * means the same real-world point at the zoom it was captured at. Zooming crops-and-magnifies the
 * sensor symmetrically around the frame's optical center (0.5), so the offset from there scales by
 * exactly `currentZoomRatio / zeroZoomRatio`; at [currentZoomRatio] == [zeroZoomRatio] this is the
 * identity (returns [fraction] unchanged).
 */
fun reprojectFractionForZoom(fraction: Float, currentZoomRatio: Float, zeroZoomRatio: Float): Float {
    val ratio = currentZoomRatio / zeroZoomRatio
    return 0.5f + ratio * (fraction - 0.5f)
}
