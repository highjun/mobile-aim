package dev.sangjun.mobileaim.app.ui

import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.sangjun.mobileaim.aim.AimOverlay
import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.aim.PoseMemoryPolicy
import dev.sangjun.mobileaim.app.camera.CameraPreview
import dev.sangjun.mobileaim.app.camera.FocusInput
import dev.sangjun.mobileaim.app.settings.SettingsDialog
import dev.sangjun.mobileaim.utils.Point2Px
import java.io.File

@Composable
fun MainScreen(
    aimViewModel: AimViewModel,
    hasCameraPermission: Boolean,
    onCameraError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val onFrame = remember(aimViewModel) { aimViewModel::onFrame }
    val onCameraReady = remember(aimViewModel) { aimViewModel::onCameraReady }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val onImageCaptureReady = remember { { capture: ImageCapture -> imageCapture = capture } }
    val uiState by aimViewModel.uiState.collectAsState()
    val settingsState by aimViewModel.settingsState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val focusInput = FocusInput(
        detected = uiState.isMarkerDetected,
        // Already debounced by PoseMemoryPolicy — non-null only once the miss has gone on long
        // enough that the pose itself was given up on, not on every dropped frame.
        lostLong = uiState.errorMessage != null,
        boardCenterPx = uiState.markerCorners.centroidOrNull(),
        frameWidth = uiState.frameWidth,
        frameHeight = uiState.frameHeight,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(
            hasCameraPermission = hasCameraPermission,
            focusInput = focusInput,
            onFrame = onFrame,
            onCameraReady = onCameraReady,
            onImageCaptureReady = onImageCaptureReady,
            onCameraError = onCameraError,
        )

        AimOverlay(
            uiState = uiState,
            strategy = settingsState.strategy,
            onRangeChange = aimViewModel::setRangeMeters,
            onSettingsClick = { showSettings = true },
            onStrategyChange = aimViewModel::setStrategy,
            onXYOffsetChange = { xMm, yMm ->
                aimViewModel.setXOffsetMm(xMm)
                aimViewModel.setYOffsetMm(yMm)
            },
            onBoardSizeChange = aimViewModel::setBoardSizeMm,
            onZ0Change = aimViewModel::setZ0MetersLive,
            onZeroClick = aimViewModel::onZeroButtonPressed,
            onCaptureClick = {
                capturePhoto(context, imageCapture) { captureId -> aimViewModel.logCaptureSnapshot(captureId) }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showSettings) {
            SettingsDialog(
                current = settingsState,
                onSave = { boardSizeMm, xOffsetMm, yOffsetMm, z0Meters ->
                    aimViewModel.updateBoardSizeMm(boardSizeMm)
                    aimViewModel.updateXOffsetMm(xOffsetMm)
                    aimViewModel.updateYOffsetMm(yOffsetMm)
                    aimViewModel.updateZ0Meters(z0Meters)
                    showSettings = false
                },
                onDismiss = { showSettings = false },
            )
        }
    }
}

/**
 * Saves a frame to `sessions/capture_<id>.jpg`, using the same [id] as the row
 * [dev.sangjun.mobileaim.aim.AimViewModel.logCaptureSnapshot] writes to `captures.csv` —
 * that shared id is how a photo and its sensor/pose snapshot get matched up after the fact. Only
 * calls [onSaved] once the file has actually landed, so a snapshot never references a missing photo.
 */
private fun capturePhoto(context: android.content.Context, imageCapture: ImageCapture?, onSaved: (id: String) -> Unit) {
    val capture = imageCapture ?: return
    val id = System.currentTimeMillis().toString()
    val dir = File(context.getExternalFilesDir(null), "sessions").apply { mkdirs() }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(File(dir, "capture_$id.jpg")).build()
    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved(id)

            override fun onError(exception: ImageCaptureException) {
                Log.w("MobileAimCapture", "Failed to save capture photo", exception)
            }
        },
    )
}

/** Centre of the detected board corners, for aiming autofocus at where the board actually is. */
private fun List<Point2Px>.centroidOrNull(): Point2Px? {
    if (isEmpty()) return null
    return Point2Px(x = sumOf { it.x.toDouble() }.toFloat() / size, y = sumOf { it.y.toDouble() }.toFloat() / size)
}
