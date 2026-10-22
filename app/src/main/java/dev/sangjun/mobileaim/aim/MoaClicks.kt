package dev.sangjun.mobileaim.aim

import kotlin.math.tan

/** Distance the zeroing target sits at — click sizes are given as millimetres on a target this far away. */
const val ZeroingDistanceMeters = 25f

/**
 * One square of the zeroing target the shooter is reading clicks off, in millimetres at
 * [ZeroingDistanceMeters]. Measured on the paper: the K2 25m zeroing target's squares are 8mm, the
 * K-1A's are 10mm. Click counts can then be read straight off the target: a group 3 squares low
 * takes 3 clicks down. Any other target can be typed in (see [ClickSizeMmRange]).
 */
enum class ZeroingTarget(val label: String, val squareMm: Float) {
    K2("K2 영점표적지", 8f),
    K1A("K-1A 영점표적지", 10f),
}

/** Click sizes the settings accept, in millimetres at [ZeroingDistanceMeters]. */
val ClickSizeMmRange = 1f..50f

/** The default click: one square of the K2 zeroing target. */
const val DefaultClickSizeMm = 8f

/** The angle one click turns the reticle by when a click is [clickSizeMm] at [ZeroingDistanceMeters]. */
fun radiansPerClick(clickSizeMm: Float): Float = clickSizeMm / 1000f / ZeroingDistanceMeters

/**
 * Every reticle click nudges by this angle by default — 8mm at 25m (0.32 mrad, about 1.1 MOA), one
 * square of the K2 25m zeroing target. The settings can change it (see [radiansPerClick]).
 */
val RadiansPerClick = radiansPerClick(DefaultClickSizeMm)

/**
 * Converts a signed click count into a screen-fraction offset, along whichever axis the caller's
 * [focalLengthPx]/[referenceLengthPx] pair describes — elevation uses the camera's vertical pair
 * ([dev.sangjun.mobileaim.app.camera.CameraFov.focalLengthYPx] /
 * [dev.sangjun.mobileaim.app.camera.CameraFov.referenceHeightPx]), windage uses the horizontal
 * pair ([dev.sangjun.mobileaim.app.camera.CameraFov.referenceWidthPx]). Both values in a pair must
 * be in the same coordinate system for the result to mean anything. [radiansPerClick] is the angle
 * of one click (see [dev.sangjun.mobileaim.aim.radiansPerClick]).
 *
 * Doesn't decide sign convention (which direction a positive click count moves the reticle on
 * screen) — that's the caller's concern (see [dev.sangjun.mobileaim.aim.AimViewModel]).
 */
fun clicksToFraction(clicks: Int, focalLengthPx: Float, referenceLengthPx: Float, radiansPerClick: Float = RadiansPerClick): Float {
    if (clicks == 0 || referenceLengthPx <= 0f) return 0f
    val angleRad = clicks * radiansPerClick
    return focalLengthPx * tan(angleRad) / referenceLengthPx
}
