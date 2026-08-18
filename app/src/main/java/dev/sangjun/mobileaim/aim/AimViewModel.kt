package dev.sangjun.mobileaim.aim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.logging.CaptureRow
import dev.sangjun.mobileaim.logging.ICaptureLogWriter
import dev.sangjun.mobileaim.logging.ISensorMotionLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * v1: no vision, no pose estimation. The reticle is wherever the user last dragged it (see
 * [onReticleDrag]) — held completely fixed, exactly like a scope's reticle rigidly mounted to the
 * barrel. Nothing here reacts to camera frames or sensors for aiming; [motionLogger] is only for
 * offline shake-test analysis, unrelated to where the reticle is drawn.
 */
class AimViewModel(
    private val settingsRepository: ISettingsRepository,
    private val motionLogger: ISensorMotionLogger,
    private val captureLogWriter: ICaptureLogWriter,
) : ViewModel() {
    init {
        motionLogger.start()
    }

    val uiState: StateFlow<AimUiState> = settingsRepository.settings
        .map { AimUiState(it.reticleXFraction, it.reticleYFraction) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AimUiState())

    /** [xFraction]/[yFraction] are 0..1 across the displayed camera preview. */
    fun onReticleDrag(xFraction: Float, yFraction: Float) {
        settingsRepository.updateReticlePosition(xFraction.coerceIn(0f, 1f), yFraction.coerceIn(0f, 1f))
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
