package dev.sangjun.mobileaim.aim

import kotlin.math.sqrt

private const val GravityMps2 = 9.8f

/**
 * The real K2C1 rifle (5.56×45mm NATO) — seed value for the "화기" default
 * [dev.sangjun.mobileaim.app.settings.AimProfile]. ~920 m/s is a typical published muzzle
 * velocity for this class of 5.56mm rifle.
 */
const val K2C1RifleMuzzleVelocityMps = 920f

/**
 * Academy Science's K2C1 replica (6mm BB, spring/manual action) — seed value for the "수동건"
 * default [dev.sangjun.mobileaim.app.settings.AimProfile]. ~200 fps is a rough estimate for this
 * class of toy gun. Any gap between this and a specific gun's real velocity is expected to be
 * absorbed by comparing against a real shot and adjusting the profile's muzzle velocity field, the
 * same way a real scope's calculated zero gets fine-tuned after a confirmation shot.
 */
const val K2C1ReplicaMuzzleVelocityMps = 61f

/**
 * The Maximum Point Blank Range band — the span of distances (inclusive, meters) over which a
 * projectile zeroed at [zeroDistanceM] stays within [targetRadiusCm] of the point of aim without
 * any holdover. This app's screen has no room for a per-distance BDC ladder (~1.8 MOA per pixel —
 * see docs/선행기술-도트사이트.md's K2C1 calculation, whose 50–200m engagement range spans under
 * 1px total), so a shooter reads one band instead of a per-distance readout.
 *
 * Model: the bore fires [hobCm] below the line of sight, at just enough upward angle `θ` that the
 * trajectory crosses the line of sight exactly at [zeroDistanceM] (a real, sighted-in zero — not
 * the simplified "symmetric arc" shortcut). Position relative to the line of sight at distance `d`
 * is `e(d) = θ·d − k·d² − HOB` where `k = g / (2·v²)`; the band is where `e(d)` stays within
 * `±targetRadiusCm`.
 *
 * Returns `null` if the trajectory's peak rise exceeds [targetRadiusCm] — the valid region would
 * then split into two disjoint distance bands, which doesn't fit a single "X–Ym" readout; the
 * caller should omit the band line rather than show a misleading single range. Also `null` for any
 * non-positive [zeroDistanceM]/[velocityMps]/[targetRadiusCm], or negative [hobCm].
 */
fun computeMpbrBand(zeroDistanceM: Float, velocityMps: Float, hobCm: Float, targetRadiusCm: Float): ClosedRange<Float>? {
    if (zeroDistanceM <= 0f || velocityMps <= 0f || targetRadiusCm <= 0f || hobCm < 0f) return null

    val hobM = hobCm / 100f
    val targetRadiusM = targetRadiusCm / 100f
    val k = GravityMps2 / (2f * velocityMps * velocityMps)
    val theta = (hobM + k * zeroDistanceM * zeroDistanceM) / zeroDistanceM

    val peakDistance = theta / (2f * k)
    val peakOffset = theta * peakDistance - k * peakDistance * peakDistance - hobM
    if (peakOffset > targetRadiusM) return null

    // Roots of e(d) = -targetRadiusM, i.e. k*d^2 - theta*d + (hobM - targetRadiusM) = 0.
    val c = hobM - targetRadiusM
    val discriminant = theta * theta - 4f * k * c
    if (discriminant < 0f) return null
    val sqrtDiscriminant = sqrt(discriminant)
    val smallerRoot = (theta - sqrtDiscriminant) / (2f * k)
    val largerRoot = (theta + sqrtDiscriminant) / (2f * k)

    // e(0) = -hobM is already inside [-R, R] whenever hobM <= targetRadiusM, so the band starts
    // at 0 rather than at the (then-negative, non-physical) smaller root.
    val near = if (hobM <= targetRadiusM) 0f else smallerRoot
    return near..largerRoot
}
