package dev.sangjun.mobileaim.app.camera

import android.hardware.camera2.CaptureRequest
import android.view.View
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.hypot

/**
 * v1: plain preview + video capture use case — no `ImageAnalysis`, since there's no CV frame
 * consumer anymore. No `ImageCapture` either — the "캡처" button saves a `PixelCopy` of the whole
 * screen instead (see [dev.sangjun.mobileaim.app.ui.MainScreen]'s `captureScreenshot`), so there's
 * no raw-sensor-frame use case to bind here.
 *
 * Every camera correction that moves or delays the image relative to the phone body is turned off
 * on the preview — see [AimCaptureRequestOptions].
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreview(
    hasCameraPermission: Boolean,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit,
    onCameraReady: (Camera) -> Unit,
    onCameraFovReady: (CameraFov) -> Unit,
    onCameraError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // PERFORMANCE (the default) prefers backing the preview with a SurfaceView, which
            // devices often composite via a separate hardware overlay for power savings — that
            // path bypasses the window's normal GPU-rendered buffer entirely, so a `PixelCopy` of
            // the Window (see MainScreen's captureScreenshot) reads back black where the camera
            // feed should be. COMPATIBLE forces a TextureView instead, which is just an ordinary
            // GL texture in the view hierarchy and always shows up in that capture.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(hasCameraPermission, previewView, lifecycleOwner) {
        var cameraProvider: ProcessCameraProvider? = null
        var focalLengthYPx: Float? = null
        var currentZoomRatio = 1f
        var zoomStateLiveData: LiveData<ZoomState>? = null

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
                    zoomRatio = currentZoomRatio,
                ),
            )
        }

        val streamStateObserver = Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING) publishFov()
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamStateObserver)
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishFov() }
        previewView.addOnLayoutChangeListener(layoutListener)

        // Republished separately from the stream/layout triggers above: applying a zoom crops the
        // sensor without touching the view's layout or the stream state, so neither of those fires
        // on its own. `zoomState` only reflects the ratio CameraX actually settled on (may differ
        // slightly from what was requested), which is exactly what a screen-fraction conversion needs.
        val zoomStateObserver = Observer<ZoomState> { zoomState ->
            currentZoomRatio = zoomState.zoomRatio
            publishFov()
        }

        fun bind(provider: ProcessCameraProvider) {
            val previewBuilder = Preview.Builder()
            val extender = Camera2Interop.Extender(previewBuilder)
            for ((key, value) in AimCaptureRequestOptions) extender.setRequestOption(key, value)
            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val videoCapture = VideoCapture.withOutput(Recorder.Builder().build())

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                videoCapture,
            )
            onVideoCaptureReady(videoCapture)
            onCameraReady(camera)

            zoomStateLiveData?.removeObserver(zoomStateObserver)
            zoomStateLiveData = camera.cameraInfo.zoomState.also {
                it.observe(lifecycleOwner, zoomStateObserver)
            }

            val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
            focalLengthYPx = CameraFovProvider(context).getFocalLengthYPx(cameraId)
            publishFov()
        }

        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        bind(provider)
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
            zoomStateLiveData?.removeObserver(zoomStateObserver)
            cameraProvider?.unbindAll()
        }
    }
}

/**
 * Camera settings for a dot sight, requested on the preview through Camera2Interop. The phone is
 * rigidly mounted to the barrel and the reticle is fixed on screen, so anything that moves the
 * image relative to the phone body breaks the zero, and anything that adds latency lags the aim:
 * - OIS/EIS off: optical and electronic stabilization both shift the image against the body.
 * - AF off, focus at infinity (0 diopters): no focus hunting when the gun swings to a new target.
 * - 60fps, fast noise reduction/edge enhancement: the lowest-latency processing the device offers.
 * Not pinned to one physical lens: Galaxy S25 Ultra's HAL refuses that (the session never gets the
 * pinned lens's metadata), which is why each zoom level keeps its own zero instead.
 */
val AimCaptureRequestOptions: List<Pair<CaptureRequest.Key<*>, Any>> = listOf(
    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE to CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF,
    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE to CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
    CaptureRequest.CONTROL_AF_MODE to CaptureRequest.CONTROL_AF_MODE_OFF,
    CaptureRequest.LENS_FOCUS_DISTANCE to 0f,
    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE to android.util.Range(60, 60),
    CaptureRequest.NOISE_REDUCTION_MODE to CaptureRequest.NOISE_REDUCTION_MODE_FAST,
    CaptureRequest.EDGE_MODE to CaptureRequest.EDGE_MODE_FAST,
)

/** [AimCaptureRequestOptions] mixes value types, so its keys come in star-projected — the pairs are built key-with-matching-value, which makes the cast safe. */
@OptIn(ExperimentalCamera2Interop::class)
@Suppress("UNCHECKED_CAST")
private fun Camera2Interop.Extender<*>.setRequestOption(key: CaptureRequest.Key<*>, value: Any) {
    setCaptureRequestOption(key as CaptureRequest.Key<Any>, value)
}
