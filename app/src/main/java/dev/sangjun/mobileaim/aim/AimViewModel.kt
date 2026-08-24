package dev.sangjun.mobileaim.aim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sangjun.mobileaim.app.camera.CameraFov
import dev.sangjun.mobileaim.app.settings.AimMode
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.logging.CaptureRow
import dev.sangjun.mobileaim.logging.ICaptureLogWriter
import dev.sangjun.mobileaim.logging.ISensorMotionLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * v1: no vision, no pose estimation, no real-time camera frame processing. There's one shared
 * zero the user drags onto their laser bore-sighter (see [onReticleDrag]) — held completely
 * fixed, exactly like a scope's reticle rigidly mounted to the barrel. [AimMode.FIREARM] shows
 * that zero as-is (a real bullet's ballistic drop is negligible at short/mid range, so the laser
 * is trustworthy). [AimMode.MANUAL_GUN] adds a computed drop offset on top of the same zero (see
 * [computeDropOffsetFraction]) — a slow airgun/spring-gun projectile drops noticeably even over a
 * few meters, so the raw laser point alone isn't accurate for it. [motionLogger] is unrelated to
 * either — only for offline shake-test analysis.
 */
class AimViewModel(
    private val settingsRepository: ISettingsRepository,
    private val motionLogger: ISensorMotionLogger,
    private val captureLogWriter: ICaptureLogWriter,
) : ViewModel() {
    /**
     * Whether the continuous logging session (IMU CSV here + camera video recording, started by
     * [dev.sangjun.mobileaim.app.ui.MainScreen] reacting to [uiState]'s [AimUiState.isLoggingActive])
     * is running. Off by default — never starts on its own, only [toggleLogging] flips it.
     */
    private val isLoggingActiveFlow = MutableStateFlow(false)

    /** Set once from [onCameraFovAvailable] when the camera binds — null until then, and the drop offset is skipped (falls back to the raw zero, same as firearm mode) rather than guessing. */
    private val cameraFovFlow = MutableStateFlow<CameraFov?>(null)

    val uiState: StateFlow<AimUiState> = combine(
        settingsRepository.settings,
        isLoggingActiveFlow,
        cameraFovFlow,
    ) { settings, isLoggingActive, cameraFov ->
        val dropOffset = if (settings.aimMode == AimMode.MANUAL_GUN && cameraFov != null) {
            computeDropOffsetFraction(settings.manualGunDistanceMeters, settings.manualGunVelocityMps, cameraFov)
        } else {
            0f
        }
        AimUiState(
            reticleXFraction = settings.reticleXFraction,
            reticleYFraction = settings.reticleYFraction + dropOffset,
            isLoggingActive = isLoggingActive,
            brightness = settings.brightness,
            aimMode = settings.aimMode,
            manualGunDistanceMeters = settings.manualGunDistanceMeters,
            manualGunVelocityMps = settings.manualGunVelocityMps,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AimUiState())

    /** [xFraction]/[yFraction] are 0..1 across the displayed camera preview. Always edits the shared zero, regardless of [AimMode]. */
    fun onReticleDrag(xFraction: Float, yFraction: Float) {
        settingsRepository.updateReticlePosition(xFraction.coerceIn(0f, 1f), yFraction.coerceIn(0f, 1f))
    }

    /** Toggles between [AimMode.FIREARM] and [AimMode.MANUAL_GUN] — both draw from the same shared zero. */
    fun onToggleAimMode() {
        val next = if (uiState.value.aimMode == AimMode.FIREARM) AimMode.MANUAL_GUN else AimMode.FIREARM
        settingsRepository.updateAimMode(next)
    }

    /** [distanceMeters] is the currently selected manual-gun engagement distance (5..10), used in the drop calculation. */
    fun onManualGunDistanceChange(distanceMeters: Float) {
        settingsRepository.updateManualGunDistance(distanceMeters)
    }

    /** [velocityMps] is the manual gun's configured muzzle velocity, used in the drop calculation. */
    fun onManualGunVelocityChange(velocityMps: Float) {
        settingsRepository.updateManualGunVelocity(velocityMps)
    }

    /** Called once the camera binds (see [dev.sangjun.mobileaim.app.camera.CameraPreview]'s `onCameraFovReady`). */
    fun onCameraFovAvailable(fov: CameraFov) {
        cameraFovFlow.value = fov
    }

    /** [brightness] is 0..1; persisted so the manual screen brightness survives app restarts. */
    fun onBrightnessChange(brightness: Float) {
        settingsRepository.updateBrightness(brightness.coerceIn(0f, 1f))
    }

    /** Starts/stops the IMU CSV log; [uiState]'s [AimUiState.isLoggingActive] separately drives video recording. */
    fun toggleLogging() {
        val next = !isLoggingActiveFlow.value
        if (next) motionLogger.start() else motionLogger.stop()
        isLoggingActiveFlow.value = next
    }

    override fun onCleared() {
        motionLogger.stop()
    }

    /**
     * Records a "조준 완료" capture event — [captureId] is shared with the photo file's name (see
     * [dev.sangjun.mobileaim.app.ui.MainScreen]'s shutter button) so the two can be matched up later.
     */
    fun logCaptureSnapshot(captureId: String) {
        val state = uiState.value
        captureLogWriter.append(
            CaptureRow(
                captureId = captureId,
                timestampMillis = System.currentTimeMillis(),
                reticleXFraction = state.reticleXFraction,
                reticleYFraction = state.reticleYFraction,
                accel = motionLogger.latestAccel.value,
                gyro = motionLogger.latestGyro.value,
            ),
        )
    }
}
