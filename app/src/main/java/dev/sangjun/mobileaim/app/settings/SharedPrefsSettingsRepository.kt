package dev.sangjun.mobileaim.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedPrefsSettingsRepository(context: Context) : ISettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableSettings = MutableStateFlow(
        MarkerSettings(
            aimMode = AimMode.entries.getOrElse(prefs.getInt(KEY_AIM_MODE, AimMode.FIREARM.ordinal)) { AimMode.FIREARM },
            reticleXFraction = prefs.getFloat(KEY_RETICLE_X_FRACTION, 0.5f),
            reticleYFraction = prefs.getFloat(KEY_RETICLE_Y_FRACTION, 0.5f),
            manualGunVelocityMps = prefs.getFloat(KEY_MANUAL_GUN_VELOCITY_MPS, MarkerSettings.DEFAULT_MANUAL_GUN_VELOCITY_MPS),
            manualGunDistanceMeters = prefs.getFloat(KEY_MANUAL_GUN_DISTANCE_METERS, MarkerSettings.DEFAULT_MANUAL_GUN_DISTANCE_METERS),
            brightness = prefs.getFloat(KEY_BRIGHTNESS, MarkerSettings.DEFAULT_BRIGHTNESS),
        ),
    )
    override val settings: StateFlow<MarkerSettings> = mutableSettings

    override fun updateReticlePosition(xFraction: Float, yFraction: Float) {
        prefs.edit()
            .putFloat(KEY_RETICLE_X_FRACTION, xFraction)
            .putFloat(KEY_RETICLE_Y_FRACTION, yFraction)
            .apply()
        mutableSettings.value = mutableSettings.value.copy(reticleXFraction = xFraction, reticleYFraction = yFraction)
    }

    override fun updateAimMode(mode: AimMode) {
        prefs.edit().putInt(KEY_AIM_MODE, mode.ordinal).apply()
        mutableSettings.value = mutableSettings.value.copy(aimMode = mode)
    }

    override fun updateManualGunVelocity(velocityMps: Float) {
        prefs.edit().putFloat(KEY_MANUAL_GUN_VELOCITY_MPS, velocityMps).apply()
        mutableSettings.value = mutableSettings.value.copy(manualGunVelocityMps = velocityMps)
    }

    override fun updateManualGunDistance(distanceMeters: Float) {
        prefs.edit().putFloat(KEY_MANUAL_GUN_DISTANCE_METERS, distanceMeters).apply()
        mutableSettings.value = mutableSettings.value.copy(manualGunDistanceMeters = distanceMeters)
    }

    override fun updateBrightness(brightness: Float) {
        prefs.edit().putFloat(KEY_BRIGHTNESS, brightness).apply()
        mutableSettings.value = mutableSettings.value.copy(brightness = brightness)
    }

    private companion object {
        const val PREFS_NAME = "mobileaim_settings"
        const val KEY_RETICLE_X_FRACTION = "reticle_x_fraction"
        const val KEY_RETICLE_Y_FRACTION = "reticle_y_fraction"
        const val KEY_AIM_MODE = "aim_mode"
        const val KEY_MANUAL_GUN_VELOCITY_MPS = "manual_gun_velocity_mps"
        const val KEY_MANUAL_GUN_DISTANCE_METERS = "manual_gun_distance_meters"
        const val KEY_BRIGHTNESS = "brightness"
    }
}
