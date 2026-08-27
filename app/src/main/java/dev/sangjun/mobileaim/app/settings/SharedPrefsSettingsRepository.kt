package dev.sangjun.mobileaim.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPrefsSettingsRepository(context: Context) : ISettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableSettings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<MarkerSettings> = mutableSettings

    /**
     * Reads the current schema — a pure function of whatever keys are present right now, not a
     * one-time "just upgraded" event, since `allowBackup` can restore a v0-format store onto a
     * fresh install. Runs once at construction; migrating is cheap and idempotent (see
     * [migrateAimModeToProfiles]).
     */
    private fun loadSettings(): MarkerSettings {
        val schemaVersion = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        if (schemaVersion < 1) {
            val migrated = migrateAimModeToProfiles(
                aimModeOrdinal = prefs.getInt(KEY_AIM_MODE, 0),
                reticleXFraction = prefs.getFloat(KEY_RETICLE_X_FRACTION, 0.5f),
                reticleYFraction = prefs.getFloat(KEY_RETICLE_Y_FRACTION, 0.5f),
            )
            // Old keys (KEY_RETICLE_X/Y_FRACTION, KEY_AIM_MODE) are intentionally left in place —
            // if this migration only partially completes, re-running it from scratch is safe.
            persistProfiles(migrated.profiles)
            prefs.edit()
                .putString(KEY_ACTIVE_PROFILE_ID, migrated.activeProfileId)
                .putInt(KEY_SCHEMA_VERSION, 1)
                .apply()
            return migrated.copy(brightness = prefs.getFloat(KEY_BRIGHTNESS, MarkerSettings.DEFAULT_BRIGHTNESS))
        }

        val profiles = prefs.getString(KEY_PROFILES, null)?.let(::parseProfiles)?.takeIf { it.isNotEmpty() } ?: DefaultAimProfiles
        val activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null) ?: profiles.first().id
        return MarkerSettings(
            profiles = profiles,
            activeProfileId = activeProfileId,
            brightness = prefs.getFloat(KEY_BRIGHTNESS, MarkerSettings.DEFAULT_BRIGHTNESS),
        )
    }

    override fun updateReticlePosition(xFraction: Float, yFraction: Float) {
        updateActiveProfile { it.copy(reticleXFraction = xFraction, reticleYFraction = yFraction) }
    }

    override fun updateClicks(elevationDelta: Int, windageDelta: Int) {
        updateActiveProfile {
            it.copy(elevationClicks = it.elevationClicks + elevationDelta, windageClicks = it.windageClicks + windageDelta)
        }
    }

    override fun setActiveProfile(profileId: String) {
        if (mutableSettings.value.profiles.none { it.id == profileId }) return
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
        mutableSettings.value = mutableSettings.value.copy(activeProfileId = profileId)
    }

    override fun addProfile(profile: AimProfile) {
        val current = mutableSettings.value
        val updated = current.profiles + profile
        persistProfiles(updated)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profile.id).apply()
        mutableSettings.value = current.copy(profiles = updated, activeProfileId = profile.id)
    }

    override fun renameProfile(profileId: String, name: String) {
        updateProfile(profileId) { it.copy(name = name) }
    }

    override fun deleteProfile(profileId: String) {
        val current = mutableSettings.value
        if (current.profiles.size <= 1) return
        val updated = current.profiles.filterNot { it.id == profileId }
        if (updated.size == current.profiles.size) return // profileId didn't match anything

        persistProfiles(updated)
        val newActiveId = if (current.activeProfileId == profileId) updated.first().id else current.activeProfileId
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, newActiveId).apply()
        mutableSettings.value = current.copy(profiles = updated, activeProfileId = newActiveId)
    }

    override fun updateProfileBallistics(profileId: String, muzzleVelocityMps: Float, zeroDistanceM: Float, hobCm: Float, targetRadiusCm: Float) {
        updateProfile(profileId) {
            it.copy(muzzleVelocityMps = muzzleVelocityMps, zeroDistanceM = zeroDistanceM, hobCm = hobCm, targetRadiusCm = targetRadiusCm)
        }
    }

    override fun updateBrightness(brightness: Float) {
        prefs.edit().putFloat(KEY_BRIGHTNESS, brightness).apply()
        mutableSettings.value = mutableSettings.value.copy(brightness = brightness)
    }

    private fun updateActiveProfile(transform: (AimProfile) -> AimProfile) {
        updateProfile(mutableSettings.value.activeProfileId, transform)
    }

    private fun updateProfile(profileId: String, transform: (AimProfile) -> AimProfile) {
        val current = mutableSettings.value
        val updatedProfiles = current.profiles.map { if (it.id == profileId) transform(it) else it }
        persistProfiles(updatedProfiles)
        mutableSettings.value = current.copy(profiles = updatedProfiles)
    }

    private fun persistProfiles(profiles: List<AimProfile>) {
        prefs.edit().putString(KEY_PROFILES, profiles.toJson()).apply()
    }

    private companion object {
        const val PREFS_NAME = "mobileaim_settings"
        const val KEY_SCHEMA_VERSION = "settings_schema_version"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_BRIGHTNESS = "brightness"

        // v0 keys — read-only, kept around for the migration; never written after schema v1.
        const val KEY_RETICLE_X_FRACTION = "reticle_x_fraction"
        const val KEY_RETICLE_Y_FRACTION = "reticle_y_fraction"
        const val KEY_AIM_MODE = "aim_mode"
    }
}

private fun List<AimProfile>.toJson(): String = JSONArray(map { it.toJson() }).toString()

private fun parseProfiles(json: String): List<AimProfile> {
    val array = JSONArray(json)
    return (0 until array.length()).map { array.getJSONObject(it).toAimProfile() }
}

private fun AimProfile.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("reticleXFraction", reticleXFraction)
    put("reticleYFraction", reticleYFraction)
    put("elevationClicks", elevationClicks)
    put("windageClicks", windageClicks)
    put("muzzleVelocityMps", muzzleVelocityMps)
    put("zeroDistanceM", zeroDistanceM)
    put("hobCm", hobCm)
    put("targetRadiusCm", targetRadiusCm)
}

private fun JSONObject.toAimProfile(): AimProfile = AimProfile(
    id = getString("id"),
    name = getString("name"),
    reticleXFraction = getDouble("reticleXFraction").toFloat(),
    reticleYFraction = getDouble("reticleYFraction").toFloat(),
    elevationClicks = getInt("elevationClicks"),
    windageClicks = getInt("windageClicks"),
    muzzleVelocityMps = getDouble("muzzleVelocityMps").toFloat(),
    zeroDistanceM = getDouble("zeroDistanceM").toFloat(),
    hobCm = getDouble("hobCm").toFloat(),
    targetRadiusCm = getDouble("targetRadiusCm").toFloat(),
)
