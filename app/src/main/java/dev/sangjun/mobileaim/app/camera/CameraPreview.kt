package dev.sangjun.mobileaim.app.camera

import android.view.View
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
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
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.hypot

/**
 * v1: plain preview + video capture use case — no `ImageAnalysis`, since there's no CV frame
 * consumer anymore. No `ImageCapture` either — the "캡처" button saves a `PixelCopy` of the whole
 * screen instead (see [dev.sangjun.mobileaim.app.ui.MainScreen]'s `captureScreenshot`), so there's
 * no raw-sensor-frame use case to bind here.
 */
@Composable
fun CameraPreview(
    hasCameraPermission: Boolean,
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
        var focalLengthYPx: Float? = null

        // FILL_CENTER crops the sensor buffer to fill the (differently-shaped) screen — see
        // [CameraFov]'s KDoc. `sensorToViewTransform` bakes in that crop, the SENSOR_ORIENTATION
        // rotation, and the stream's aspect crop all at once, so republishing on every
        // stream/layout change (rather than once at bind time, when the transform isn't ready yet
        // anyway) keeps [CameraFov] correct across rotation.
        fun publishFov() {
            val fy = focalLengthYPx ?: return
            val matrix = previewView.sensorToViewTransform ?: return
            val unitY = floatArrayOf(0f, 1f)
            matrix.mapVectors(unitY)
            val scale = hypot(unitY[0], unitY[1])
            if (scale <= 0f) return
            onCameraFovReady(
                CameraFov(
                    focalLengthYPx = fy,
                    referenceHeightPx = previewView.height / scale,
                    referenceWidthPx = previewView.width / scale,
                ),
            )
        }

        val streamStateObserver = Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING) publishFov()
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamStateObserver)
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishFov() }
        previewView.addOnLayoutChangeListener(layoutListener)

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
                        val recorder = Recorder.Builder().build()
                        val videoCapture = VideoCapture.withOutput(recorder)

                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            videoCapture,
                        )
                        onVideoCaptureReady(videoCapture)

                        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
                        focalLengthYPx = CameraFovProvider(context).getFocalLengthYPx(cameraId)
                        publishFov()
                    } catch (error: Exception) {
                        onCameraError(error)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

        onDispose {
            previewView.removeOnLayoutChangeListener(layoutListener)
            previewView.previewStreamState.removeObserver(streamStateObserver)
            cameraProvider?.unbindAll()
        }
    }
}
