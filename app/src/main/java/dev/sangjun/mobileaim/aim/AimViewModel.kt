package dev.sangjun.mobileaim.aim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sangjun.mobileaim.app.camera.CameraFov
import dev.sangjun.mobileaim.app.settings.DefaultAimProfiles
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.logging.CaptureRow
import dev.sangjun.mobileaim.logging.ICaptureLogWriter
import dev.sangjun.mobileaim.logging.ISensorMotionLogger
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * v1: no vision, no pose estimation, no real-time camera frame processing. Each
 * [dev.sangjun.mobileaim.app.settings.AimProfile] holds its own bore-sighted zero — dragged onto a
 * laser bore-sighter (see [onReticleDrag]), exactly like a scope's reticle rigidly mounted to the
 * barrel — plus click adjustments dialed in after a confirmation shot (see [onCreepUp]/
 * [onCreepDown]/[onCreepLeft]/[onCreepRight]). [motionLogger] is unrelated to either — only for
 * offline shake-test analysis.
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

    /** Set from [onCameraFovAvailable] whenever the preview's stream/layout changes — null until the first publish, and click offsets fall back to 0 (raw zero only) rather than guessing. */
    private val cameraFovFlow = MutableStateFlow<CameraFov?>(null)

    val uiState: StateFlow<AimUiState> = combine(
        settingsRepository.settings,
        isLoggingActiveFlow,
        cameraFovFlow,
    ) { settings, isLoggingActive, cameraFov ->
        val profile = settings.activeProfile
        val elevationOffset = cameraFov?.let { clicksToFraction(profile.elevationClicks, it.focalLengthYPx, it.referenceHeightPx) } ?: 0f
        val windageOffset = cameraFov?.let { clicksToFraction(profile.windageClicks, it.focalLengthYPx, it.referenceWidthPx) } ?: 0f

        AimUiState(
            reticleXFraction = profile.reticleXFraction,
            reticleYFraction = profile.reticleYFraction,
            // Elevation clicks move the reticle up the screen (smaller Y); windage clicks move it right (larger X) — see MoaClicks.kt.
            effectiveReticleXFraction = (profile.reticleXFraction + windageOffset).coerceIn(0f, 1f),
            effectiveReticleYFraction = (profile.reticleYFraction - elevationOffset).coerceIn(0f, 1f),
            elevationClicks = profile.elevationClicks,
            windageClicks = profile.windageClicks,
            isLoggingActive = isLoggingActive,
            brightness = settings.brightness,
            profiles = settings.profiles,
            activeProfileId = settings.activeProfileId,
            zeroDistanceM = profile.zeroDistanceM,
            mpbrBandM = computeMpbrBand(profile.zeroDistanceM, profile.muzzleVelocityMps, profile.hobCm, profile.targetRadiusCm),
            isCreepAvailable = cameraFov != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AimUiState())

    /** [xFraction]/[yFraction] are 0..1 across the displayed camera preview. Edits the active profile's raw zero. */
    fun onReticleDrag(xFraction: Float, yFraction: Float) {
        settingsRepository.updateReticlePosition(xFraction.coerceIn(0f, 1f), yFraction.coerceIn(0f, 1f))
    }

    /** Moves the reticle up by one click ([MoaPerClick] MOA) — see [AimUiState.effectiveReticleYFraction]. */
    fun onCreepUp() = settingsRepository.updateClicks(elevationDelta = 1, windageDelta = 0)

    /** Moves the reticle down by one click. */
    fun onCreepDown() = settingsRepository.updateClicks(elevationDelta = -1, windageDelta = 0)

    /** Moves the reticle left by one click. */
    fun onCreepLeft() = settingsRepository.updateClicks(elevationDelta = 0, windageDelta = -1)

    /** Moves the reticle right by one click. */
    fun onCreepRight() = settingsRepository.updateClicks(elevationDelta = 0, windageDelta = 1)

    /** Switches which saved profile is active. */
    fun onSelectProfile(profileId: String) = settingsRepository.setActiveProfile(profileId)

    /**
     * Creates a new profile — seeded from the "화기" defaults (ballistics are meaningless until
     * the user edits them, but a profile needs *some* starting values) with an auto-generated
     * name ("새 프로파일", "새 프로파일 2", ...) to avoid needing a text prompt just to create one.
     * Returns the new profile's id so the caller (the profile sheet) can immediately open its
     * editor for renaming/configuring.
     */
    fun onAddProfile(): String {
        val existingNames = settingsRepository.settings.value.profiles.map { it.name }.toSet()
        var name = "새 프로파일"
        var suffix = 2
        while (name in existingNames) {
            name = "새 프로파일 $suffix"
            suffix++
        }
        val newProfile = DefaultAimProfiles.first().copy(id = UUID.randomUUID().toString(), name = name)
        settingsRepository.addProfile(newProfile)
        return newProfile.id
    }

    fun onRenameProfile(profileId: String, name: String) = settingsRepository.renameProfile(profileId, name)

    fun onDeleteProfile(profileId: String) = settingsRepository.deleteProfile(profileId)

    fun onUpdateProfileBallistics(profileId: String, muzzleVelocityMps: Float, zeroDistanceM: Float, hobCm: Float, targetRadiusCm: Float) =
        settingsRepository.updateProfileBallistics(profileId, muzzleVelocityMps, zeroDistanceM, hobCm, targetRadiusCm)

    /** Called whenever the preview's stream/layout changes (see [dev.sangjun.mobileaim.app.camera.CameraPreview]'s `onCameraFovReady`). */
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
     * [dev.sangjun.mobileaim.app.ui.MainScreen]'s shutter button) so the two can be matched up
     * later. Uses the *effective* reticle position (zero + clicks), not the raw zero — a shot log
     * should reflect where the shooter was actually aiming.
     */
    fun logCaptureSnapshot(captureId: String) {
        val state = uiState.value
        captureLogWriter.append(
            CaptureRow(
                captureId = captureId,
                timestampMillis = System.currentTimeMillis(),
                reticleXFraction = state.effectiveReticleXFraction,
                reticleYFraction = state.effectiveReticleYFraction,
                accel = motionLogger.latestAccel.value,
                gyro = motionLogger.latestGyro.value,
            ),
        )
    }
}
