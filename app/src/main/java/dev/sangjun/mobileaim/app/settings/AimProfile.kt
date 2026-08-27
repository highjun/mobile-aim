package dev.sangjun.mobileaim.app.settings

import dev.sangjun.mobileaim.aim.K2C1ReplicaMuzzleVelocityMps
import dev.sangjun.mobileaim.aim.K2C1RifleMuzzleVelocityMps

/**
 * A named zero — one physical setup (gun + mount) worth of aiming data. Replaces the old
 * `AimMode` toggle: instead of two hardcoded equipment slots sharing a single reticle position,
 * any number of profiles can be saved/loaded, each with its own zero, click adjustments, and
 * ballistic parameters (see [dev.sangjun.mobileaim.aim.computeMpbrBand]).
 *
 * [reticleXFraction]/[reticleYFraction]: the bore-sighted zero, set by dragging the reticle onto
 * the laser dot — unaffected by [elevationClicks]/[windageClicks].
 *
 * [elevationClicks]/[windageClicks]: fine adjustment dialed in after a confirmation shot, kept as
 * signed click counts rather than pre-converted into a screen fraction — see
 * [dev.sangjun.mobileaim.aim.clicksToFraction] for why (screen size/FOV can change; converting at
 * click time would bake today's geometry into a stored value that should stay geometry-agnostic).
 */
data class AimProfile(
    val id: String,
    val name: String,
    val reticleXFraction: Float = 0.5f,
    val reticleYFraction: Float = 0.5f,
    val elevationClicks: Int = 0,
    val windageClicks: Int = 0,
    val muzzleVelocityMps: Float,
    val zeroDistanceM: Float,
    val hobCm: Float,
    val targetRadiusCm: Float,
)

const val FirearmProfileId = "firearm"
const val ManualGunProfileId = "manual_gun"

/**
 * Seeded on first run and by the v0→v1 settings migration (see [migrateAimModeToProfiles]).
 * [AimProfile.zeroDistanceM]/[AimProfile.hobCm]/[AimProfile.targetRadiusCm] didn't exist before
 * v1 — 화기's values match the K2C1 MPBR reference calculation in
 * docs/선행기술-도트사이트.md (HOB from Lower 1/3 Co-Witness, target radius from IPSC half-height);
 * 수동건's are a rough starting point pending a real mount measurement (see
 * docs/하드웨어-설계-미정사항.md).
 */
val DefaultAimProfiles = listOf(
    AimProfile(
        id = FirearmProfileId,
        name = "화기",
        muzzleVelocityMps = K2C1RifleMuzzleVelocityMps,
        zeroDistanceM = 50f,
        hobCm = 4f,
        targetRadiusCm = 38f,
    ),
    AimProfile(
        id = ManualGunProfileId,
        name = "수동건",
        muzzleVelocityMps = K2C1ReplicaMuzzleVelocityMps,
        zeroDistanceM = 5f,
        hobCm = 4f,
        targetRadiusCm = 5f,
    ),
)
