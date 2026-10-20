package dev.sangjun.mobileaim.aim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sangjun.mobileaim.app.camera.CameraFov
import dev.sangjun.mobileaim.app.settings.AimProfile
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.app.settings.ReticleSize
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
 * [dev.sangjun.mobileaim.app.settings.AimProfile] is one gun, holding a separate zero per zoom
 * level — dragged onto a laser bore-sighter's dot (see [onReticleDrag]), exactly like a scope's
 * reticle rigidly mounted to the barrel, then fine-tuned with clicks after a confirmation group
 * (see [onCreepUp]/[onCreepDown]/[onCreepLeft]/[onCreepRight]). [motionLogger] is unrelated to
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

    /** Set from [onCameraFovAvailable] whenever the preview's stream/layout changes — null until the first publish, and click offsets fall back to 0 (raw zero only) rather than guessing. */
    private val cameraFovFlow = MutableStateFlow<CameraFov?>(null)

    /** Unsaved adjusting-mode zero edits — see [ZeroDrafts], [saveZeroDrafts], [discardZeroDrafts]. */
    private val zeroDraftsFlow = MutableStateFlow(ZeroDrafts())

    val uiState: StateFlow<AimUiState> = combine(
        settingsRepository.settings,
        isLoggingActiveFlow,
        cameraFovFlow,
        zeroDraftsFlow,
    ) { settings, isLoggingActive, cameraFov, drafts ->
        // The header's selected zoom level (see onZoomChange) — persisted in settings; MainScreen
        // applies it to the bound camera, and cameraFov.zoomRatio reports what CameraX settled on.
        val zoomLevel = settings.zoomLevel
        val zero = drafts.zeroFor(settings.activeProfileId, zoomLevel, settings.activeProfile.zeroFor(zoomLevel))
        val reticleXFraction = zero?.xFraction ?: 0.5f
        val reticleYFraction = zero?.yFraction ?: 0.5f
        val elevationClicks = zero?.elevationClicks ?: 0
        val windageClicks = zero?.windageClicks ?: 0
        // Clicks are angle-native (see RadiansPerClick), so only the *current* zoom's effective focal length
        // matters for converting them to a screen fraction — see CameraFov.zoomRatio's KDoc.
        val elevationOffset = cameraFov?.let { clicksToFraction(elevationClicks, it.focalLengthYPx * it.zoomRatio, it.referenceHeightPx) } ?: 0f
        val windageOffset = cameraFov?.let { clicksToFraction(windageClicks, it.focalLengthYPx * it.zoomRatio, it.referenceWidthPx) } ?: 0f

        AimUiState(
            reticleXFraction = reticleXFraction,
            reticleYFraction = reticleYFraction,
            // Elevation clicks move the reticle up the screen (smaller Y); windage clicks move it right (larger X) — see MoaClicks.kt.
            effectiveReticleXFraction = (reticleXFraction + windageOffset).coerceIn(0f, 1f),
            effectiveReticleYFraction = (reticleYFraction - elevationOffset).coerceIn(0f, 1f),
            elevationClicks = elevationClicks,
            windageClicks = windageClicks,
            isLoggingActive = isLoggingActive,
            brightness = settings.brightness,
            profiles = settings.profiles,
            activeProfileId = settings.activeProfileId,
            isCreepAvailable = cameraFov != null,
            zoomLevel = zoomLevel,
            isZeroed = zero != null,
            isZeroUnsaved = drafts.isUnsaved(settings.activeProfileId, zoomLevel),
            hasUnsavedZeros = !drafts.isEmpty,
            enabledZoomLevels = settings.enabledZoomLevels,
            reticleSize = settings.reticleSize,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AimUiState())

    /**
     * [xFraction]/[yFraction] are 0..1 across the displayed camera preview — becomes the current
     * zoom's unsaved zero, resetting its clicks (see [ZeroDrafts.drag]).
     */
    fun onReticleDrag(xFraction: Float, yFraction: Float) {
        val settings = settingsRepository.settings.value
        zeroDraftsFlow.value = zeroDraftsFlow.value.drag(settings.activeProfileId, settings.zoomLevel, xFraction.coerceIn(0f, 1f), yFraction.coerceIn(0f, 1f))
    }

    /** Moves the reticle up by one click ([RadiansPerClick]) — unsaved until [saveZeroDrafts]; see [AimUiState.effectiveReticleYFraction]. */
    fun onCreepUp() = click(elevationDelta = 1, windageDelta = 0)

    /** Moves the reticle down by one click. */
    fun onCreepDown() = click(elevationDelta = -1, windageDelta = 0)

    /** Moves the reticle left by one click. */
    fun onCreepLeft() = click(elevationDelta = 0, windageDelta = -1)

    /** Moves the reticle right by one click. */
    fun onCreepRight() = click(elevationDelta = 0, windageDelta = 1)

    private fun click(elevationDelta: Int, windageDelta: Int) {
        val settings = settingsRepository.settings.value
        val saved = settings.activeProfile.zeroFor(settings.zoomLevel)
        zeroDraftsFlow.value = zeroDraftsFlow.value.click(settings.activeProfileId, settings.zoomLevel, saved, elevationDelta, windageDelta)
    }

    /** Writes every unsaved zero (any profile, any zoom) to settings and clears the drafts. */
    fun saveZeroDrafts() {
        for ((key, zero) in zeroDraftsFlow.value.zeros) settingsRepository.saveZero(key.first, key.second, zero)
        zeroDraftsFlow.value = ZeroDrafts()
    }

    /** Drops every unsaved zero — the saved zeros show again. */
    fun discardZeroDrafts() {
        zeroDraftsFlow.value = ZeroDrafts()
    }

    /** Switches which saved profile is active. */
    fun onSelectProfile(profileId: String) = settingsRepository.setActiveProfile(profileId)

    /**
     * Creates a new profile with no zeros yet and an auto-generated name ("새 프로파일",
     * "새 프로파일 2", ...) to avoid needing a text prompt just to create one.
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
        val newProfile = AimProfile(id = UUID.randomUUID().toString(), name = name)
        settingsRepository.addProfile(newProfile)
        return newProfile.id
    }

    fun onRenameProfile(profileId: String, name: String) = settingsRepository.renameProfile(profileId, name)

    fun onDeleteProfile(profileId: String) = settingsRepository.deleteProfile(profileId)

    /** Called whenever the preview's stream/layout changes (see [dev.sangjun.mobileaim.app.camera.CameraPreview]'s `onCameraFovReady`). */
    fun onCameraFovAvailable(fov: CameraFov) {
        cameraFovFlow.value = fov
    }

    /** Selects a zoom level (1..25) from the header and persists it — [dev.sangjun.mobileaim.app.ui.MainScreen] reacts to [uiState]'s [AimUiState.zoomLevel] to actually apply it to the bound camera. */
    fun onZoomChange(zoomLevel: Int) = settingsRepository.updateZoomLevel(zoomLevel)

    /** Which zoom levels the header's zoom picker lists. */
    fun onEnabledZoomLevelsChange(zoomLevels: List<Int>) = settingsRepository.updateEnabledZoomLevels(zoomLevels)

    /** Steps the reticle size small → medium → large → small. */
    fun onCycleReticleSize() {
        val sizes = ReticleSize.entries
        settingsRepository.updateReticleSize(sizes[(settingsRepository.settings.value.reticleSize.ordinal + 1) % sizes.size])
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

