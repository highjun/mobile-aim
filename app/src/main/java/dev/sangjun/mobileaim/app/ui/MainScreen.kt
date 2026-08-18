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
import dev.sangjun.mobileaim.app.camera.CameraPreview
import java.io.File

@Composable
fun MainScreen(
    aimViewModel: AimViewModel,
    hasCameraPermission: Boolean,
    onCameraError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val onImageCaptureReady = remember { { capture: ImageCapture -> imageCapture = capture } }
    val uiState by aimViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(
            hasCameraPermission = hasCameraPermission,
            onImageCaptureReady = onImageCaptureReady,
            onCameraError = onCameraError,
        )

        AimOverlay(
            uiState = uiState,
            onReticleDrag = aimViewModel::onReticleDrag,
            onCaptureClick = {
                capturePhoto(context, imageCapture) { captureId -> aimViewModel.logCaptureSnapshot(captureId) }
            },
            modifier = Modifier.fillMaxSize(),
        )
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
