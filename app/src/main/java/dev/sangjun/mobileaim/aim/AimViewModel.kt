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

    /** The header's selected zoom chip (see [onZoomChange]) — [dev.sangjun.mobileaim.app.ui.MainScreen] applies it to the bound camera; [cameraFovFlow]'s [CameraFov.zoomRatio] reports back what CameraX actually settled on. */
    private val zoomRatioFlow = MutableStateFlow(1f)

    val uiState: StateFlow<AimUiState> = combine(
        settingsRepository.settings,
        isLoggingActiveFlow,
        cameraFovFlow,
        zoomRatioFlow,
    ) { settings, isLoggingActive, cameraFov, zoomRatio ->
        val profile = settings.activeProfile
        // Clicks are angle-native (MOA), so only the *current* zoom's effective focal length
        // matters for converting them to a screen fraction — see CameraFov.zoomRatio's KDoc.
        val elevationOffset = cameraFov?.let { clicksToFraction(profile.elevationClicks, it.focalLengthYPx * it.zoomRatio, it.referenceHeightPx) } ?: 0f
        val windageOffset = cameraFov?.let { clicksToFraction(profile.windageClicks, it.focalLengthYPx * it.zoomRatio, it.referenceWidthPx) } ?: 0f
        // The raw zero, unlike clicks, is a screen fraction captured at profile.zeroZoomRatio — it
        // only means the same real-world point at another zoom once reprojected around center — see
        // dev.sangjun.mobileaim.app.settings.AimProfile.zeroZoomRatio.
        val currentZoomRatio = cameraFov?.zoomRatio ?: 1f
        val reticleXFraction = reprojectFractionForZoom(profile.reticleXFraction, currentZoomRatio, profile.zeroZoomRatio).coerceIn(0f, 1f)
        val reticleYFraction = reprojectFractionForZoom(profile.reticleYFraction, currentZoomRatio, profile.zeroZoomRatio).coerceIn(0f, 1f)

        AimUiState(
            reticleXFraction = reticleXFraction,
            reticleYFraction = reticleYFraction,
            // Elevation clicks move the reticle up the screen (smaller Y); windage clicks move it right (larger X) — see MoaClicks.kt.
            effectiveReticleXFraction = (reticleXFraction + windageOffset).coerceIn(0f, 1f),
            effectiveReticleYFraction = (reticleYFraction - elevationOffset).coerceIn(0f, 1f),
            elevationClicks = profile.elevationClicks,
            windageClicks = profile.windageClicks,
            isLoggingActive = isLoggingActive,
            brightness = settings.brightness,
            profiles = settings.profiles,
            activeProfileId = settings.activeProfileId,
            isCreepAvailable = cameraFov != null,
            zoomRatio = zoomRatio,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AimUiState())

    /**
     * [xFraction]/[yFraction] are 0..1 across the displayed camera preview, at whatever zoom is
     * currently active — saved as-is, together with that zoom ratio (see
     * [dev.sangjun.mobileaim.app.settings.AimProfile.zeroZoomRatio]), no conversion needed since the
     * dragged fraction already *is* "the fraction at the current zoom". Also resets the active
     * profile's click adjustments to 0 in the same update — see [ISettingsRepository.updateReticlePosition].
     */
    fun onReticleDrag(xFraction: Float, yFraction: Float) {
        settingsRepository.updateReticlePosition(
            xFraction.coerceIn(0f, 1f),
            yFraction.coerceIn(0f, 1f),
            cameraFovFlow.value?.zoomRatio ?: 1f,
        )
    }

    /** Moves the reticle up by one click ([MoaPerClick] MOA) — see [AimUiState.effectiveReticleYFraction]. */
    fun onCreepUp() = settingsRepository.updateClicks(elevationDelta = 1, windageDelta = 0)

    /** Moves the reticle down by one click. */
    fun onCreepDown() = settingsRepository.updateClicks(elevationDelta = -1, windageDelta = 0)

    /** Moves the reticle left by one click. */
    fun onCreepLeft() = settingsRepository.updateClicks(elevationDelta = 0, windageDelta = -1)

    /** Moves the reticle right by one click. */
    fun onCreepRight() = settingsRepository.updateClicks(elevationDelta = 0, windageDelta = 1)

    /**
     * Nudges the raw zero itself by [dxFraction]/[dyFraction] (already expressed at the current
     * zoom — see the `onBoresight*` callers) — for boresighting's arrow pad, an alternative to
     * dragging for finer control. Goes through [ISettingsRepository.updateReticlePosition], the same
     * path [onReticleDrag] uses (including saving the current zoom as the new
     * `zeroZoomRatio`), so it resets [AimUiState.elevationClicks]/[AimUiState.windageClicks] exactly
     * like a drag would (they were dialed in relative to the zero this just moved).
     */
    private fun nudgeRawZero(dxFraction: Float, dyFraction: Float, zoomRatio: Float) {
        val profile = settingsRepository.settings.value.activeProfile
        // The stored zero is a fraction at profile.zeroZoomRatio — bring it to the current zoom
        // first, or adding a current-zoom delta to it (and saving at the current zoom) makes the
        // zero jump whenever the two zooms differ.
        val currentX = reprojectFractionForZoom(profile.reticleXFraction, zoomRatio, profile.zeroZoomRatio)
        val currentY = reprojectFractionForZoom(profile.reticleYFraction, zoomRatio, profile.zeroZoomRatio)
        settingsRepository.updateReticlePosition(
            (currentX + dxFraction).coerceIn(0f, 1f),
            (currentY + dyFraction).coerceIn(0f, 1f),
            zoomRatio,
        )
    }

    /**
     * One click's worth of screen distance at the *current* zoom — [nudgeRawZero]'s delta must be
     * in those terms since the zero it's added to is now saved (and reprojected) per current zoom,
     * not a fixed reference (see [dev.sangjun.mobileaim.app.settings.AimProfile.zeroZoomRatio]).
     */
    private fun oneClickFraction(focalLengthPx: Float, referenceLengthPx: Float, zoomRatio: Float) =
        clicksToFraction(1, focalLengthPx * zoomRatio, referenceLengthPx)

    /** Nudges the raw zero up by one click's worth of screen distance — never touches the click counters (see [nudgeRawZero]). */
    fun onBoresightUp() {
        val fov = cameraFovFlow.value ?: return
        nudgeRawZero(dxFraction = 0f, dyFraction = -oneClickFraction(fov.focalLengthYPx, fov.referenceHeightPx, fov.zoomRatio), zoomRatio = fov.zoomRatio)
    }

    /** Nudges the raw zero down by one click's worth of screen distance. */
    fun onBoresightDown() {
        val fov = cameraFovFlow.value ?: return
        nudgeRawZero(dxFraction = 0f, dyFraction = oneClickFraction(fov.focalLengthYPx, fov.referenceHeightPx, fov.zoomRatio), zoomRatio = fov.zoomRatio)
    }

    /** Nudges the raw zero left by one click's worth of screen distance. */
    fun onBoresightLeft() {
        val fov = cameraFovFlow.value ?: return
        nudgeRawZero(dxFraction = -oneClickFraction(fov.focalLengthYPx, fov.referenceWidthPx, fov.zoomRatio), dyFraction = 0f, zoomRatio = fov.zoomRatio)
    }

    /** Nudges the raw zero right by one click's worth of screen distance. */
    fun onBoresightRight() {
        val fov = cameraFovFlow.value ?: return
        nudgeRawZero(dxFraction = oneClickFraction(fov.focalLengthYPx, fov.referenceWidthPx, fov.zoomRatio), dyFraction = 0f, zoomRatio = fov.zoomRatio)
    }

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

    /** Called whenever the preview's stream/layout changes (see [dev.sangjun.mobileaim.app.camera.CameraPreview]'s `onCameraFovReady`). */
    fun onCameraFovAvailable(fov: CameraFov) {
        cameraFovFlow.value = fov
    }

    /** Selects a zoom chip (1x/2x/4x) from the header — [dev.sangjun.mobileaim.app.ui.MainScreen] reacts to [uiState]'s [AimUiState.zoomRatio] to actually apply it to the bound camera. */
    fun onZoomChange(zoomRatio: Float) {
        zoomRatioFlow.value = zoomRatio
    }

    /** Two-finger pinch on the camera view — multiplies the current zoom by [factor], kept within 1x..[MaxPinchZoom] (the device's own max is clamped again in MainScreen). */
    fun onPinchZoom(factor: Float) {
        zoomRatioFlow.value = (zoomRatioFlow.value * factor).coerceIn(1f, MaxPinchZoom)
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

private const val MaxPinchZoom = 8f
