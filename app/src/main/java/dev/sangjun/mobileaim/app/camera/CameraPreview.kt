package dev.sangjun.mobileaim.app.camera

import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/** v1: plain preview + shutter/video capture use cases — no `ImageAnalysis`, since there's no CV frame consumer anymore. */
@Composable
fun CameraPreview(
    hasCameraPermission: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit,
    onCameraFovReady: (CameraFov) -> Unit,
    onCameraError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(hasCameraPermission, previewView, lifecycleOwner) {
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
                        val imageCapture = ImageCapture.Builder().build()
                        val recorder = Recorder.Builder().build()
                        val videoCapture = VideoCapture.withOutput(recorder)

                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            videoCapture,
                        )
                        onImageCaptureReady(imageCapture)
                        onVideoCaptureReady(videoCapture)

                        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
                        CameraFovProvider(context).getFov(cameraId)?.let(onCameraFovReady)
                    } catch (error: Exception) {
                        onCameraError(error)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

        onDispose {
            cameraProvider?.unbindAll()
        }
    }
}
