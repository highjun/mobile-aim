package dev.sangjun.mobileaim.app.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.util.Log
import kotlin.math.abs

/**
 * Debug-only camera diagnostics: does the device actually honor the settings a dot sight needs —
 * a single lens (no switching to a telephoto as zoom goes up, which would move the optical axis
 * and break the zero), OIS/EIS off (stabilization shifts the image relative to the phone body, so
 * a screen-fixed reticle stops matching the barrel), focus locked at infinity (no hunting when
 * the scene changes), and 60fps (less latency)? Requesting them is not proof, so what each frame
 * *reports back* is read from its [CaptureResult] and shown/logged as [CameraDiagSnapshot].
 */
data class CameraDiagSnapshot(
    val activePhysicalId: String?,
    val oisMode: Int?,
    val videoStabilizationMode: Int?,
    val afMode: Int?,
    val focusDistanceDiopters: Float?,
    val fps: Float?,
    val exposureTimeNs: Long?,
    val zoomRatio: Float?,
    val cropRegion: String?,
    /** Settings the bind had to drop (e.g. the physical camera pin), empty when everything was accepted. */
    val droppedSettings: List<String> = emptyList(),
) {
    fun toDisplayText(): String = buildString {
        appendLine("렌즈 ${activePhysicalId ?: "-"}")
        appendLine("OIS ${oisMode.label(OisLabels)} · EIS ${videoStabilizationMode.label(VideoStabilizationLabels)}")
        appendLine("AF ${afMode.label(AfLabels)} · 초점 ${focusDistanceDiopters?.let { "%.2fD".format(it) } ?: "-"}")
        appendLine("${fps?.let { "%.1f".format(it) } ?: "-"}fps · 노출 ${exposureTimeNs?.let { "%.2fms".format(it / 1e6) } ?: "-"}")
        append("줌 ${zoomRatio?.let { "%.2f".format(it) } ?: "-"} · crop ${cropRegion ?: "-"}")
        if (droppedSettings.isNotEmpty()) append("\n거부됨: ${droppedSettings.joinToString()}")
    }

    fun toLogLine(): String =
        "physical=$activePhysicalId ois=${oisMode.label(OisLabels)} eis=${videoStabilizationMode.label(VideoStabilizationLabels)} " +
            "af=${afMode.label(AfLabels)} focusD=$focusDistanceDiopters fps=$fps exposureNs=$exposureTimeNs " +
            "zoom=$zoomRatio crop=$cropRegion dropped=$droppedSettings"
}

private val OisLabels = mapOf(
    CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF to "OFF",
    CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON to "ON",
)
private val VideoStabilizationLabels = mapOf(
    CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF to "OFF",
    CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON to "ON",
    CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION to "PREVIEW",
)
private val AfLabels = mapOf(
    CaptureResult.CONTROL_AF_MODE_OFF to "OFF",
    CaptureResult.CONTROL_AF_MODE_AUTO to "AUTO",
    CaptureResult.CONTROL_AF_MODE_MACRO to "MACRO",
    CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO to "C-VIDEO",
    CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE to "C-PICTURE",
    CaptureResult.CONTROL_AF_MODE_EDOF to "EDOF",
)

private fun Int?.label(labels: Map<Int, String>): String = this?.let { labels[it] ?: it.toString() } ?: "-"

/** The settings diagnostic mode asks for — see [CameraDiagSnapshot]'s KDoc for why each one. */
val DiagnosticCaptureRequestOptions: List<Pair<CaptureRequest.Key<*>, Any>> = listOf(
    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE to CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF,
    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE to CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
    CaptureRequest.CONTROL_AF_MODE to CaptureRequest.CONTROL_AF_MODE_OFF,
    // 0 diopters = infinity — every shooting distance is past the main lens's hyperfocal distance.
    CaptureRequest.LENS_FOCUS_DISTANCE to 0f,
    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE to android.util.Range(60, 60),
    CaptureRequest.NOISE_REDUCTION_MODE to CaptureRequest.NOISE_REDUCTION_MODE_FAST,
    CaptureRequest.EDGE_MODE to CaptureRequest.EDGE_MODE_FAST,
)

/**
 * Picks the physical lens behind a logical multi-camera that the logical camera itself reports as
 * its 1x focal length — the main wide lens, whose digital zoom alone covers the app's 1x–8x range.
 * Null when [logicalFocalLengthMm] is unknown or no physical lens matches it.
 */
fun selectMainPhysicalCameraId(logicalFocalLengthMm: Float?, physicalFocalLengthsMm: Map<String, Float>): String? {
    logicalFocalLengthMm ?: return null
    return physicalFocalLengthsMm.entries
        .filter { abs(it.value - logicalFocalLengthMm) < FocalLengthToleranceMm }
        .minByOrNull { abs(it.value - logicalFocalLengthMm) }
        ?.key
}

private const val FocalLengthToleranceMm = 0.05f

/** Reads [logicalCameraId]'s physical lenses from [CameraManager] and hands them to [selectMainPhysicalCameraId]. */
fun findMainPhysicalCameraId(cameraManager: CameraManager, logicalCameraId: String): String? = try {
    val logical = cameraManager.getCameraCharacteristics(logicalCameraId)
    val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) logical.physicalCameraIds else emptySet()
    val focalLengths = physicalIds.mapNotNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
            ?.let { id to it }
    }.toMap()
    selectMainPhysicalCameraId(logical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull(), focalLengths)
} catch (error: Exception) {
    Log.w(DiagnosticsLogTag, "Failed to read physical cameras of $logicalCameraId", error)
    null
}

/** Every [SampleEveryFrames]th frame, turns the result into a [CameraDiagSnapshot], logs it under [DiagnosticsLogTag], and hands it to [onSnapshot]. */
class DiagnosticCaptureCallback(
    private val droppedSettings: List<String>,
    private val onSnapshot: (CameraDiagSnapshot) -> Unit,
) : CameraCaptureSession.CaptureCallback() {
    private var frameCount = 0

    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        if (frameCount++ % SampleEveryFrames != 0) return
        val snapshot = result.toDiagSnapshot(droppedSettings)
        Log.i(DiagnosticsLogTag, snapshot.toLogLine())
        onSnapshot(snapshot)
    }
}

private const val SampleEveryFrames = 30

const val DiagnosticsLogTag = "MobileAimCamDiag"

private fun CaptureResult.toDiagSnapshot(droppedSettings: List<String>) = CameraDiagSnapshot(
    activePhysicalId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null,
    oisMode = get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE),
    videoStabilizationMode = get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE),
    afMode = get(CaptureResult.CONTROL_AF_MODE),
    focusDistanceDiopters = get(CaptureResult.LENS_FOCUS_DISTANCE),
    fps = get(CaptureResult.SENSOR_FRAME_DURATION)?.takeIf { it > 0 }?.let { 1e9f / it },
    exposureTimeNs = get(CaptureResult.SENSOR_EXPOSURE_TIME),
    zoomRatio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) get(CaptureResult.CONTROL_ZOOM_RATIO) else null,
    cropRegion = get(CaptureResult.SCALER_CROP_REGION)?.toShortString(),
    droppedSettings = droppedSettings,
)
