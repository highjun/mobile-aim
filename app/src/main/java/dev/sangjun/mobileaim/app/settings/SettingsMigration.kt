package dev.sangjun.mobileaim.app.settings

/**
 * v0 → v1: the old `AimMode` enum (two fixed equipment slots sharing one reticle position)
 * becomes [AimProfile] (an arbitrary list of named zeros). Both default profiles start from the
 * *same* [reticleXFraction]/[reticleYFraction] — the v0 schema stored only one shared zero despite
 * `AimMode`'s KDoc claiming otherwise, so there's no actual per-mode data to split.
 *
 * [aimModeOrdinal] selects which default profile becomes active, mirroring the v0
 * `AimMode.entries[ordinal]` lookup (falls back to the first/화기 profile for an out-of-range
 * value, same as the old `AimMode.entries.getOrElse`).
 *
 * A pure function so it's testable without SharedPreferences — see
 * [dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository], which is the only caller and
 * treats this as a per-read idempotent transform (not a one-time "just upgraded" event), since
 * `allowBackup` can restore a v0-format store onto a fresh install.
 */
fun migrateAimModeToProfiles(aimModeOrdinal: Int, reticleXFraction: Float, reticleYFraction: Float): MarkerSettings {
    val profiles = DefaultAimProfiles.map { it.copy(reticleXFraction = reticleXFraction, reticleYFraction = reticleYFraction) }
    val activeProfile = profiles.getOrElse(aimModeOrdinal) { profiles.first() }
    return MarkerSettings(profiles = profiles, activeProfileId = activeProfile.id)
}
