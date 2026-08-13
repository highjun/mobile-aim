package dev.sangjun.mobileaim.feature.settings

import dev.sangjun.mobileaim.feature.tracking.AimStrategy
import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<MarkerSettings>

    fun updateBoardSizeMm(boardSizeMm: Double)
    fun updateXOffsetMm(xOffsetMm: Double)
    fun updateYOffsetMm(yOffsetMm: Double)
    fun updateZ0Meters(z0Meters: Float)
    fun updateStrategy(strategy: AimStrategy)
}
