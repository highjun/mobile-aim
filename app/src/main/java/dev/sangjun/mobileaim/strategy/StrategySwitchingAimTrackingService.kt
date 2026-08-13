package dev.sangjun.mobileaim.strategy

import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat

/**
 * The single [IAimTrackingService] Koin binds — delegates every call to whichever strategy
 * [ISettingsRepository.settings] currently selects. [analyze] reads the setting synchronously
 * (it runs on the camera analyzer thread, not react to the settings [kotlinx.coroutines.flow.Flow]),
 * and writes the delegate's freshly-produced [TrackingResult] straight into [state] rather than
 * subscribing to the delegate's own flow — every strategy already updates its state synchronously
 * inside its own `analyze`, so reading `.value` right after delegating is exact, and avoids needing
 * a [kotlinx.coroutines.CoroutineScope] just to `flatMapLatest` between strategies.
 */
class StrategySwitchingAimTrackingService(
    private val strategies: Map<AimStrategy, IAimTrackingService>,
    private val settingsRepository: ISettingsRepository,
) : IAimTrackingService, ZeroableAimTracking {
    private var lastStrategy: AimStrategy? = null
    private val mutableState = MutableStateFlow(TrackingResult(isTracking = false))
    override val state: StateFlow<TrackingResult> = mutableState

    override fun analyze(
        gray: Mat,
        rotationDegrees: Int,
        timestampMillis: Long,
        intrinsics: CameraIntrinsics?,
        gravityCamera: DoubleArray?,
        rotationDeviceFrame: DoubleArray?,
    ) {
        val strategy = settingsRepository.settings.value.strategy
        val previous = lastStrategy
        if (previous != null && previous != strategy) {
            // Switching strategies mid-session must not let one carry state into the other (e.g. a
            // stale ChArUco pose bleeding into a freshly-selected WhyCon run).
            strategies.getValue(previous).reset()
        }
        lastStrategy = strategy

        val active = strategies.getValue(strategy)
        active.analyze(gray, rotationDegrees, timestampMillis, intrinsics, gravityCamera, rotationDeviceFrame)
        mutableState.value = active.state.value
    }

    override fun reset() = strategies.values.forEach { it.reset() }

    override fun updateMarkerSizeMm(markerSizeMm: Double) {
        strategies.values.forEach { it.updateMarkerSizeMm(markerSizeMm) }
    }

    /** No-op when the active strategy doesn't support zeroing (e.g. ChArUco/WhyCon). */
    override fun setZero(rotationDeviceFrame: DoubleArray, rotationDegrees: Int) {
        val active = strategies.getValue(settingsRepository.settings.value.strategy)
        (active as? ZeroableAimTracking)?.setZero(rotationDeviceFrame, rotationDegrees)
    }
}
