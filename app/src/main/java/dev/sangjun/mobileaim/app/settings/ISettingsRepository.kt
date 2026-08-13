package dev.sangjun.mobileaim.app.settings

import dev.sangjun.mobileaim.strategy.AimStrategy
import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<MarkerSettings>

    fun updateBoardSizeMm(boardSizeMm: Double)
    fun updateXOffsetMm(xOffsetMm: Double)
    fun updateYOffsetMm(yOffsetMm: Double)
    fun updateZ0Meters(z0Meters: Float)
    fun updateStrategy(strategy: AimStrategy)
}
