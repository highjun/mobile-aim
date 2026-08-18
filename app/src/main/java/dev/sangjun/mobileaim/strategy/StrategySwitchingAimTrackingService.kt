package dev.sangjun.mobileaim.strategy

import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat

/**
 * The single [IAimTrackingService] Koin binds — delegates every call to whichever strategy
 * [currentStrategy] currently selects. [analyze] reads it synchronously (it runs on the camera
 * analyzer thread), and writes the delegate's freshly-produced [TrackingResult] straight into
 * [state] rather than subscribing to the delegate's own flow — every strategy already updates its
 * state synchronously inside its own `analyze`, so reading `.value` right after delegating is
 * exact, and avoids needing a [kotlinx.coroutines.CoroutineScope] just to `flatMapLatest` between
 * strategies.
 *
 * `currentStrategy` used to come from the app's shared [dev.sangjun.mobileaim.app.settings.ISettingsRepository]
 * — that settings model is now v1's reticle-drag position only, so this deprecated class owns its
 * own strategy selection instead of reaching into settings that no longer model it.
 */
@Deprecated("v1은 모드가 하나(수동 드래그)뿐이라 전략 전환 추상화가 불필요해짐")
class StrategySwitchingAimTrackingService(
    private val strategies: Map<AimStrategy, IAimTrackingService>,
) : IAimTrackingService, ZeroableAimTracking {
    var currentStrategy: AimStrategy = AimStrategy.CHARUCO

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
        val strategy = currentStrategy
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
    override fun setZero() {
        val active = strategies.getValue(currentStrategy)
        (active as? ZeroableAimTracking)?.setZero()
    }
}
