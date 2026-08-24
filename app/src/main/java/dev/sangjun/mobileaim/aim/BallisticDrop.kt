package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.camera.CameraFov
import kotlin.math.atan
import kotlin.math.tan

private const val GravityMps2 = 9.8f

/**
 * How far below the laser-straight-line zero a slow projectile's reticle should sit, as a
 * fraction of the camera preview's height — the same role a BDC reticle's holdover marks play for
 * a real firearm, just computed instead of pre-printed. A laser bore-sighter only shows where the
 * barrel points in a straight line; an actual projectile drops under gravity the whole time it's
 * in flight, so at low enough velocity (see [dev.sangjun.mobileaim.app.settings.MarkerSettings])
 * that drop stops being negligible even over a few meters.
 *
 * Pure projectile-motion approximation (no drag) — good enough given [velocityMps] is itself a
 * user-entered estimate rather than a chronographed value; any systematic gap between this and
 * reality (drag, hop-up, the camera FOV approximation) is expected to be absorbed by adjusting
 * that velocity empirically after a confirmation shot, the same way a real scope's zero gets
 * fine-tuned after dialing in calculated clicks.
 */
fun computeDropOffsetFraction(distanceMeters: Float, velocityMps: Float, fov: CameraFov): Float {
    if (distanceMeters <= 0f || velocityMps <= 0f) return 0f
    val timeOfFlightSeconds = distanceMeters / velocityMps
    val dropMeters = 0.5f * GravityMps2 * timeOfFlightSeconds * timeOfFlightSeconds
    val dropAngleRad = atan(dropMeters / distanceMeters)
    val dropPx = fov.focalLengthYPx * tan(dropAngleRad)
    return dropPx / fov.referenceHeightPx
}
