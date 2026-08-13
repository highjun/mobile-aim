package dev.sangjun.mobileaim.app.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    hasCameraPermission: Boolean,
    focusInput: FocusInput,
    onFrame: (ImageProxy) -> Unit,
    onCameraReady: (cameraId: String) -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val focusPolicy = remember { FocusPolicy() }
    var focusController by remember { mutableStateOf<CameraFocusController?>(null) }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(cameraExecutor) {
        onDispose { cameraExecutor.shutdown() }
    }

    DisposableEffect(hasCameraPermission, previewView, lifecycleOwner, cameraExecutor, onFrame) {
        var cameraProvider: ProcessCameraProvider? = null

        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        // Unset, CameraX falls back to a low default (~640x480) — too coarse to
                        // resolve the ChArUco board's small embedded-tag bits (~1.2-2mm each) at
                        // the rig's 200-400mm working distance. 1280x720 is a reasoned starting
                        // point (see plan doc), not a guess: high enough for sub-pixel corner
                        // precision, not so high it risks falling behind on per-frame OpenCV work.
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val analysisBuilder = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                        attachFocusDiagnostics(analysisBuilder)
                        val imageAnalysis = analysisBuilder
                            .build()
                            .also { analysis ->
                                var loggedResolution = false
                                analysis.setAnalyzer(cameraExecutor) { image ->
                                    if (!loggedResolution) {
                                        Log.i(TAG, "Analysis resolution: ${image.width}x${image.height}")
                                        loggedResolution = true
                                    }
                                    onFrame(image)
                                }
                            }

                        val imageCapture = ImageCapture.Builder().build()

                        provider.unbindAll()
                        val camera: Camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                            imageCapture,
                        )
                        focusController = CameraFocusController(
                            cameraControl = camera.cameraControl,
                            analysisUseCase = imageAnalysis,
                            callbackExecutor = ContextCompat.getMainExecutor(context),
                        )
                        onImageCaptureReady(imageCapture)

                        resolveBackCameraId(context)?.let { cameraId ->
                            logFocusCapabilities(context, cameraId)
                            onCameraReady(cameraId)
                        }
                    } catch (error: Exception) {
                        onCameraError(error)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

        onDispose {
            focusController = null
            cameraProvider?.unbindAll()
        }
    }

    // Autofocus is driven off the aim state rather than the camera's own view of the scene: only
    // the detector knows where the board is, and only it knows when the board is genuinely gone.
    // FocusPolicy decides whether that warrants touching focus at all — usually it doesn't.
    LaunchedEffect(focusInput, focusController) {
        val controller = focusController ?: return@LaunchedEffect
        val command = focusPolicy.onInput(focusInput, SystemClock.elapsedRealtime()) ?: return@LaunchedEffect
        controller.execute(command, focusInput.frameWidth, focusInput.frameHeight) { successful ->
            focusPolicy.onAcquireResult(successful)
        }
    }
}

/**
 * Logs autofocus state transitions coming back from the capture session. Purely observational —
 * AF cannot be exercised on an emulator, so real-device logs are the only evidence available for
 * whether focus is hunting, holding, or physically unable to reach the board's distance.
 */
@OptIn(ExperimentalCamera2Interop::class)
private fun attachFocusDiagnostics(builder: ImageAnalysis.Builder) {
    var lastAfState: Int? = null
    var lastLensState: Int? = null
    Camera2Interop.Extender(builder).setSessionCaptureCallback(
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                val lensState = result.get(CaptureResult.LENS_STATE)
                if (afState == lastAfState && lensState == lastLensState) return
                lastAfState = afState
                lastLensState = lensState
                val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                Log.i(
                    TAG,
                    "AF state=${afStateName(afState)} lens=${lensStateName(lensState)} " +
                        "focusDistance=${focusDistance}D",
                )
            }
        },
    )
}

private fun afStateName(state: Int?): String = when (state) {
    null -> "unreported"
    CameraMetadata.CONTROL_AF_STATE_INACTIVE -> "INACTIVE"
    CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN -> "PASSIVE_SCAN"
    CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "PASSIVE_FOCUSED"
    CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN -> "ACTIVE_SCAN"
    CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED -> "FOCUSED_LOCKED"
    CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "NOT_FOCUSED_LOCKED"
    CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "PASSIVE_UNFOCUSED"
    else -> "unknown($state)"
}

private fun lensStateName(state: Int?): String = when (state) {
    null -> "unreported"
    CameraMetadata.LENS_STATE_STATIONARY -> "STATIONARY"
    CameraMetadata.LENS_STATE_MOVING -> "MOVING"
    else -> "unknown($state)"
}

/**
 * One-shot record of what this device's lens can actually do. `minFocusDistance` is in diopters
 * (1/metres), so the rig's 200-400mm working range needs at least 2.5-5.0 there; a 0 means a fixed
 * focus lens, and an uncalibrated distance scale means any absolute focus value is device-specific.
 */
private fun logFocusCapabilities(context: Context, cameraId: String) {
    try {
        val characteristics = context.getSystemService(CameraManager::class.java)
            .getCameraCharacteristics(cameraId)
        val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val calibration = characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        Log.i(
            TAG,
            "Focus capabilities: minFocusDistance=${minFocusDistance}D calibration=$calibration " +
                "hardwareLevel=$hardwareLevel afModes=${afModes?.joinToString()}",
        )
    } catch (error: Exception) {
        Log.w(TAG, "Failed to read focus capabilities", error)
    }
}

private fun resolveBackCameraId(context: Context): String? {
    return try {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        cameraManager.cameraIdList.firstOrNull { id ->
            val facing = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (error: Exception) {
        Log.w(TAG, "Failed to resolve back camera id", error)
        null
    }
}

private const val TAG = "MobileAimCamera"
