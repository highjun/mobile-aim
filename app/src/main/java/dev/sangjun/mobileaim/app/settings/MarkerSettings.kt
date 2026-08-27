package dev.sangjun.mobileaim.app.settings

/**
 * Persisted user settings.
 *
 * [profiles]/[activeProfileId]: see [AimProfile] — an arbitrary list of named zeros, replacing the
 * old fixed 화기/수동 `AimMode` pair.
 *
 * [brightness]: the manual screen brightness (0..1) the app forces on its window, which also
 * disables the system's auto-brightness for this app. Defaults high so an outdoor screen starts
 * bright rather than dimmed by auto-brightness — see [DEFAULT_BRIGHTNESS].
 */
data class MarkerSettings(
    val profiles: List<AimProfile> = DefaultAimProfiles,
    val activeProfileId: String = DefaultAimProfiles.first().id,
    val brightness: Float = DEFAULT_BRIGHTNESS,
) {
    /** Falls back to the first profile if [activeProfileId] doesn't match any — shouldn't happen outside a corrupted store, since [dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository] never persists an empty profile list. */
    val activeProfile: AimProfile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

    companion object {
        const val DEFAULT_BRIGHTNESS = 0.8f
    }
}
