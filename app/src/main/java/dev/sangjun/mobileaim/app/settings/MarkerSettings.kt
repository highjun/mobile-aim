package dev.sangjun.mobileaim.app.settings

/**
 * Persisted user settings.
 *
 * [reticleXFraction]/[reticleYFraction]: the shared zero — set by dragging the reticle onto the
 * laser dot, a fraction (0..1) of the camera preview. Both [AimMode]s draw from this same point:
 * [AimMode.FIREARM] shows it as-is (a real bullet's ballistic drop is negligible at short/mid
 * range), while [AimMode.MANUAL_GUN] adds a computed drop offset on top of it (see
 * [dev.sangjun.mobileaim.aim.computeDropOffsetFraction]) — the laser stays a valid zero reference
 * either way, only the display differs.
 *
 * [manualGunVelocityMps]/[manualGunDistanceMeters]: inputs to that drop calculation — the manual/
 * spring gun's muzzle velocity (set once, roughly, since a slow projectile drops noticeably even
 * over a few meters — see [AimMode]) and the currently selected engagement distance (5..10 range,
 * last value remembered). [DEFAULT_MANUAL_GUN_VELOCITY_MPS] is deliberately high (firearm-like) so
 * an unconfigured gun computes a near-zero offset rather than an alarming, wrong one.
 *
 * [brightness]: the manual screen brightness (0..1) the app forces on its window, which also
 * disables the system's auto-brightness for this app. Defaults high so an outdoor screen starts
 * bright rather than dimmed by auto-brightness — see [DEFAULT_BRIGHTNESS].
 */
data class MarkerSettings(
    val aimMode: AimMode = AimMode.FIREARM,
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    val manualGunVelocityMps: Float = DEFAULT_MANUAL_GUN_VELOCITY_MPS,
    val manualGunDistanceMeters: Float = DEFAULT_MANUAL_GUN_DISTANCE_METERS,
    val brightness: Float = DEFAULT_BRIGHTNESS,
) {
    companion object {
        const val DEFAULT_BRIGHTNESS = 0.8f
        const val DEFAULT_MANUAL_GUN_VELOCITY_MPS = 200f
        const val DEFAULT_MANUAL_GUN_DISTANCE_METERS = 5f
    }
}
