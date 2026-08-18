package dev.sangjun.mobileaim.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedPrefsSettingsRepository(context: Context) : ISettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableSettings = MutableStateFlow(
        MarkerSettings(
            reticleXFraction = prefs.getFloat(KEY_RETICLE_X_FRACTION, 0.5f),
            reticleYFraction = prefs.getFloat(KEY_RETICLE_Y_FRACTION, 0.5f),
        ),
    )
    override val settings: StateFlow<MarkerSettings> = mutableSettings

    override fun updateReticlePosition(xFraction: Float, yFraction: Float) {
        prefs.edit()
            .putFloat(KEY_RETICLE_X_FRACTION, xFraction)
            .putFloat(KEY_RETICLE_Y_FRACTION, yFraction)
            .apply()
        mutableSettings.value = MarkerSettings(xFraction, yFraction)
    }

    private companion object {
        const val PREFS_NAME = "mobileaim_settings"
        const val KEY_RETICLE_X_FRACTION = "reticle_x_fraction"
        const val KEY_RETICLE_Y_FRACTION = "reticle_y_fraction"
    }
}
