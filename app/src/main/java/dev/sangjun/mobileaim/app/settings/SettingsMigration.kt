package dev.sangjun.mobileaim.app.settings

import kotlin.math.roundToInt

/**
 * v0 → v1: the old `AimMode` enum (two fixed equipment slots sharing one reticle position)
 * becomes [AimProfile] (an arbitrary list of named guns). Both default profiles start from the
 * *same* [reticleXFraction]/[reticleYFraction] — the v0 schema stored only one shared zero despite
 * `AimMode`'s KDoc claiming otherwise, so there's no actual per-mode data to split. v0 had no zoom,
 * so that zero lands in the 1x slot (see [legacyZeroToZoomZeros]).
 *
 * [aimModeOrdinal] selects which default profile becomes active by id, mirroring the v0
 * `AimMode.entries[ordinal]` lookup (0 = 화기 → [FirearmProfileId], 1 = 수동건 →
 * [ManualGunProfileId]; an out-of-range value falls back to [FirearmProfileId], same as the old
 * `AimMode.entries.getOrElse`). By id, not list position, since [DefaultAimProfiles]' order is free
 * to change.
 *
 * A pure function so it's testable without SharedPreferences — see
 * [dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository], which is the only caller and
 * treats this as a per-read idempotent transform (not a one-time "just upgraded" event), since
 * `allowBackup` can restore a v0-format store onto a fresh install.
 */
/** v0 `AimMode` entries in ordinal order, as the profile ids they became. */
private val LegacyAimModeProfileIds = listOf(FirearmProfileId, ManualGunProfileId)

fun migrateAimModeToProfiles(aimModeOrdinal: Int, reticleXFraction: Float, reticleYFraction: Float): MarkerSettings {
    val zeros = legacyZeroToZoomZeros(reticleXFraction, reticleYFraction, zeroZoomRatio = 1f, elevationClicks = 0, windageClicks = 0)
    val profiles = DefaultAimProfiles.map { it.copy(zeros = zeros) }
    val activeProfileId = LegacyAimModeProfileIds.getOrElse(aimModeOrdinal) { LegacyAimModeProfileIds.first() }
    return MarkerSettings(profiles = profiles, activeProfileId = activeProfileId)
}

/**
 * A profile saved before per-zoom zeros held one zero plus the zoom it was captured at — that
 * zero goes into the zoom slot nearest to [zeroZoomRatio] (pinch zoom could leave it between
 * whole steps; clamped to [ZoomLevels]). An untouched default (screen center, no clicks) was never
 * actually zeroed, so it becomes no zero at all rather than a fake one.
 */
fun legacyZeroToZoomZeros(
    reticleXFraction: Float,
    reticleYFraction: Float,
    zeroZoomRatio: Float,
    elevationClicks: Int,
    windageClicks: Int,
): Map<Int, ZoomZero> {
    val untouched = reticleXFraction == 0.5f && reticleYFraction == 0.5f && elevationClicks == 0 && windageClicks == 0
    if (untouched) return emptyMap()
    val zoomLevel = zeroZoomRatio.roundToInt().coerceIn(ZoomLevels.first, ZoomLevels.last)
    return mapOf(zoomLevel to ZoomZero(reticleXFraction, reticleYFraction, elevationClicks, windageClicks))
}
