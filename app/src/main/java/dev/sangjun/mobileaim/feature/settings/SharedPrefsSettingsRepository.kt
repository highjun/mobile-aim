package dev.sangjun.mobileaim.feature.settings

import android.content.Context
import dev.sangjun.mobileaim.config.MarkerConfig
import dev.sangjun.mobileaim.config.RangeSliderConfig
import dev.sangjun.mobileaim.feature.tracking.AimStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedPrefsSettingsRepository(context: Context) : ISettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableSettings = MutableStateFlow(
        MarkerSettings(
            boardSizeMm = prefs.getFloat(KEY_BOARD_SIZE_MM, MarkerConfig.DEFAULT_BOARD_SIZE_MM.toFloat()).toDouble(),
            xOffsetMm = prefs.getFloat(KEY_X_OFFSET_MM, MarkerConfig.DEFAULT_X_OFFSET_MM.toFloat()).toDouble(),
            yOffsetMm = prefs.getFloat(KEY_Y_OFFSET_MM, MarkerConfig.DEFAULT_Y_OFFSET_MM.toFloat()).toDouble(),
            z0Meters = prefs.getFloat(KEY_Z0_METERS, RangeSliderConfig.DEFAULT_Z0_METERS),
            strategy = prefs.getString(KEY_STRATEGY, null)?.let { runCatching { AimStrategy.valueOf(it) }.getOrNull() }
                ?: AimStrategy.CHARUCO,
        ),
    )
    override val settings: StateFlow<MarkerSettings> = mutableSettings

    override fun updateBoardSizeMm(boardSizeMm: Double) {
        prefs.edit().putFloat(KEY_BOARD_SIZE_MM, boardSizeMm.toFloat()).apply()
        mutableSettings.value = mutableSettings.value.copy(boardSizeMm = boardSizeMm)
    }

    override fun updateXOffsetMm(xOffsetMm: Double) {
        prefs.edit().putFloat(KEY_X_OFFSET_MM, xOffsetMm.toFloat()).apply()
        mutableSettings.value = mutableSettings.value.copy(xOffsetMm = xOffsetMm)
    }

    override fun updateYOffsetMm(yOffsetMm: Double) {
        prefs.edit().putFloat(KEY_Y_OFFSET_MM, yOffsetMm.toFloat()).apply()
        mutableSettings.value = mutableSettings.value.copy(yOffsetMm = yOffsetMm)
    }

    override fun updateZ0Meters(z0Meters: Float) {
        prefs.edit().putFloat(KEY_Z0_METERS, z0Meters).apply()
        mutableSettings.value = mutableSettings.value.copy(z0Meters = z0Meters)
    }

    override fun updateStrategy(strategy: AimStrategy) {
        prefs.edit().putString(KEY_STRATEGY, strategy.name).apply()
        mutableSettings.value = mutableSettings.value.copy(strategy = strategy)
    }

    private companion object {
        const val PREFS_NAME = "mobileaim_settings"
        const val KEY_BOARD_SIZE_MM = "board_size_mm"
        const val KEY_X_OFFSET_MM = "x_offset_mm"
        const val KEY_Y_OFFSET_MM = "y_offset_mm"
        const val KEY_Z0_METERS = "z0_meters"
        const val KEY_STRATEGY = "strategy"
    }
}
