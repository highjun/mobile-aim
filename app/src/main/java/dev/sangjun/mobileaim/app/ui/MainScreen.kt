package dev.sangjun.mobileaim.app.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.sangjun.mobileaim.app.sensor.ShakeMonitor
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.ZoomState
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sangjun.mobileaim.aim.AimOverlay
import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.app.camera.CameraPreview
import dev.sangjun.mobileaim.app.settings.ZoomLevels
import dev.sangjun.mobileaim.utils.fileTimestampNow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * `MobileAim`, the shared subfolder name every piece of MobileAim output lands under. Where that
 * subfolder actually lives differs by media type on API 29+, though, because of a MediaStore
 * restriction we don't control: `MediaStore.Images`/`MediaStore.Video` only accept
 * [Environment.DIRECTORY_PICTURES]/[Environment.DIRECTORY_MOVIES] (or DCIM) as the *top-level*
 * `RELATIVE_PATH` segment — anything else throws `IllegalArgumentException: Primary directory
 * MobileAim not allowed for content://media/external/...` at save time, which CameraX swallows into
 * its error callback, so the previous "one unified `MobileAim/{images,videos,sensors}` root" attempt
 * silently failed to save every photo/video (no crash, no toast, no file). So: photos land in
 * `Pictures/$MediaRoot`, videos in `Movies/$MediaRoot` — both required top-level roots, with
 * `MediaRoot` as the sub-brand folder inside them. Sensor CSVs (see
 * [dev.sangjun.mobileaim.logging.CaptureLogWriter]/[dev.sangjun.mobileaim.logging.SensorMotionLogger])
 * go through `MediaStore.Downloads` instead, which has no such restriction, so they keep the fully
 * custom `MobileAim/sensors` path.
 */
private const val MediaRoot = "MobileAim"

@Composable
fun MainScreen(
    aimViewModel: AimViewModel,
    hasCameraPermission: Boolean,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    onCameraError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val onVideoCaptureReady = remember { { capture: VideoCapture<Recorder> -> videoCapture = capture } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val onCameraReady = remember { { boundCamera: Camera -> camera = boundCamera } }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isProfileSheetOpen by remember { mutableStateOf(false) }
    val uiState by aimViewModel.uiState.collectAsState()

    // Muzzle shake for the on-screen indicator — listens only while the screen is resumed.
    val shakeMonitor = remember { ShakeMonitor(context) }
    val shake by shakeMonitor.reading.collectAsState()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> shakeMonitor.start()
                Lifecycle.Event.ON_PAUSE -> shakeMonitor.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shakeMonitor.stop()
        }
    }

    // Applies the header's chosen zoom level to the actual bound camera — clamped to what the
    // hardware reports, in case a device's max zoom ratio is below 8x.
    val currentZoomLevel by rememberUpdatedState(uiState.zoomLevel)
    LaunchedEffect(uiState.zoomLevel, camera) {
        camera?.applyZoomLevel(uiState.zoomLevel)
    }
    // Re-applied every time the camera (re)opens: CameraX resets zoom to 1x whenever the camera
    // closes (screen off, app switch — ZoomControl.setActive(false)), while the zoom level and the
    // Camera object stay the same, so the effect above wouldn't run again and the header would keep
    // showing e.g. "8x" over a 1x picture.
    DisposableEffect(camera, lifecycleOwner) {
        val boundCamera = camera ?: return@DisposableEffect onDispose {}
        val cameraState = boundCamera.cameraInfo.cameraState
        val observer = Observer<CameraState> { state ->
            if (state.type == CameraState.Type.OPEN) boundCamera.applyZoomLevel(currentZoomLevel)
        }
        cameraState.observe(lifecycleOwner, observer)
        onDispose { cameraState.removeObserver(observer) }
    }

    // The camera's real zoom ceiling (10x on Galaxy S25 Ultra) — the zoom picker can't go past it.
    var maxZoomLevel by remember { mutableStateOf(ZoomLevels.last) }
    DisposableEffect(camera, lifecycleOwner) {
        val zoomState = camera?.cameraInfo?.zoomState ?: return@DisposableEffect onDispose {}
        val observer = Observer<ZoomState> { maxZoomLevel = it.maxZoomRatio.toInt().coerceIn(ZoomLevels.first, ZoomLevels.last) }
        zoomState.observe(lifecycleOwner, observer)
        onDispose { zoomState.removeObserver(observer) }
    }

    // Force the manual brightness onto the host Activity's window; setting an explicit 0..1 value
    // is what disables the system's auto-brightness for this app (see AimUiState.brightness).
    LaunchedEffect(uiState.brightness) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = uiState.brightness.coerceIn(0.05f, 1f)
        }
    }

    // The logging session's on/off state lives in AimViewModel (see AimUiState.isLoggingActive);
    // this just reacts to it to drive the CameraX Recording object, which can't live in the
    // ViewModel since it's tied to the bound VideoCapture use case.
    LaunchedEffect(uiState.isLoggingActive, videoCapture) {
        val capture = videoCapture ?: return@LaunchedEffect
        if (uiState.isLoggingActive) {
            if (activeRecording != null) return@LaunchedEffect
            activeRecording = startVideoRecording(context, capture)
        } else {
            activeRecording?.stop()
            activeRecording = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(
            hasCameraPermission = hasCameraPermission,
            onVideoCaptureReady = onVideoCaptureReady,
            onCameraReady = onCameraReady,
            onCameraFovReady = aimViewModel::onCameraFovAvailable,
            onCameraError = onCameraError,
        )

        AimOverlay(
            uiState = uiState,
            onReticleDrag = aimViewModel::onReticleDrag,
            onToggleLogging = aimViewModel::toggleLogging,
            onCaptureClick = {
                val window = context.findActivity()?.window
                if (window != null) {
                    captureScreenshot(context, window, coroutineScope) { captureId ->
                        aimViewModel.logCaptureSnapshot(captureId)
                    }
                }
            },
            isFocusMode = isFocusMode,
            onToggleFocusMode = onToggleFocusMode,
            onBrightnessChange = aimViewModel::onBrightnessChange,
            onZoomChange = aimViewModel::onZoomChange,
            isProfileSheetOpen = isProfileSheetOpen,
            onOpenProfileSheet = { isProfileSheetOpen = true },
            onDismissProfileSheet = { isProfileSheetOpen = false },
            onSelectProfile = aimViewModel::onSelectProfile,
            onAddProfile = aimViewModel::onAddProfile,
            onRenameProfile = aimViewModel::onRenameProfile,
            onDeleteProfile = aimViewModel::onDeleteProfile,
            onCreepUp = aimViewModel::onCreepUp,
            onCreepDown = aimViewModel::onCreepDown,
            onCreepLeft = aimViewModel::onCreepLeft,
            onCreepRight = aimViewModel::onCreepRight,
            onSaveZeroDrafts = aimViewModel::saveZeroDrafts,
            onDiscardZeroDrafts = aimViewModel::discardZeroDrafts,
            onEnabledZoomLevelsChange = aimViewModel::onEnabledZoomLevelsChange,
            onCycleReticleSize = aimViewModel::onCycleReticleSize,
            maxZoomLevel = maxZoomLevel,
            onClickSizeChange = aimViewModel::onClickSizeChange,
            shake = shake,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun Camera.applyZoomLevel(zoomLevel: Int) {
    val zoomState = cameraInfo.zoomState.value ?: return
    cameraControl.setZoomRatio(zoomLevel.toFloat().coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio))
}

/** Walks the [Context] wrapper chain to the hosting [Activity] (needed to reach its window). */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Saves the whole window's current pixels as `capture_<id>.png` — includes whatever [AimOverlay]
 * is drawing on top of the camera feed (reticle, header, creep pad), unlike the raw sensor frame
 * a CameraX `ImageCapture` would give. Uses the same [id] as the row
 * [dev.sangjun.mobileaim.aim.AimViewModel.logCaptureSnapshot] writes to `captures.csv` — that
 * shared id is how a screenshot and its sensor/pose snapshot get matched up after the fact. Only
 * calls [onSaved] once the file has actually landed, so a snapshot never references a missing
 * screenshot.
 *
 * `PixelCopy` (not `View.drawToBitmap()`) because [dev.sangjun.mobileaim.app.camera.CameraPreview]'s
 * `PreviewView` defaults to `ImplementationMode.PERFORMANCE`, which can back the preview with a
 * `SurfaceView` — a plain view-hierarchy draw can't read that surface's pixels, but `PixelCopy` can.
 * The actual PNG encode/write happens off the main thread via [coroutineScope]; `PixelCopy` itself
 * delivers its result on the main looper, which is cheap enough to not need a dedicated thread.
 */
private fun captureScreenshot(
    context: Context,
    window: Window,
    coroutineScope: CoroutineScope,
    onSaved: (id: String) -> Unit,
) {
    val decorView = window.decorView
    val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        window,
        bitmap,
        { copyResult ->
            if (copyResult != PixelCopy.SUCCESS) {
                Log.w("MobileAimCapture", "Failed to copy screen pixels: result=$copyResult")
                Toast.makeText(context, "스크린샷 저장 실패", Toast.LENGTH_LONG).show()
                return@request
            }
            coroutineScope.launch(Dispatchers.IO) {
                val id = fileTimestampNow()
                val fileName = "capture_$id.png"
                val (outputStream, displayLocation) = openScreenshotOutputStream(context, fileName)
                outputStream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "스크린샷 저장됨: $displayLocation", Toast.LENGTH_SHORT).show()
                    onSaved(id)
                }
            }
        },
        Handler(Looper.getMainLooper()),
    )
}

/**
 * Opens an [OutputStream] for [fileName] under the shared `Pictures/$MediaRoot` collection (via
 * MediaStore) on API 29+ — see [MediaRoot]'s doc for why it's `Pictures/` and not a fully custom
 * top-level folder. On older API levels MediaStore's `RELATIVE_PATH` isn't supported, so it falls
 * back to a `MobileAim/images` subfolder inside the app's own external-files directory. Returns the
 * stream paired with a display-friendly path for the completion toast.
 */
private fun openScreenshotOutputStream(context: Context, fileName: String): Pair<OutputStream, String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$MediaRoot")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("MediaStore rejected screenshot insert")
        context.contentResolver.openOutputStream(uri)!! to "${Environment.DIRECTORY_PICTURES}/$MediaRoot/$fileName"
    } else {
        val dir = File(context.getExternalFilesDir(null), "$MediaRoot/images").apply { mkdirs() }
        FileOutputStream(File(dir, fileName)) to "Android/data/${context.packageName}/files/$MediaRoot/images/$fileName"
    }
}

/**
 * Starts recording camera video (no audio — the logging session only needs to visually confirm
 * the reticle stays put during a shake test, and skipping audio avoids needing RECORD_AUDIO) as
 * `video_<timestamp>.mp4`.
 *
 * Lands in the shared `Movies/$MediaRoot` collection (via MediaStore) on API 29+, same rationale
 * and fallback as [openScreenshotOutputStream]. The session's raw IMU CSV logs land alongside it under
 * `MobileAim/sensors` (see [dev.sangjun.mobileaim.logging.SensorMotionLogger]/[dev.sangjun.mobileaim.logging.CaptureLogWriter]) — the completion toast points there too.
 */
private fun startVideoRecording(context: Context, videoCapture: VideoCapture<Recorder>): Recording {
    val id = fileTimestampNow()
    val fileName = "video_$id.mp4"
    val (pendingRecording, displayLocation) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$MediaRoot")
        }
        val outputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        videoCapture.output.prepareRecording(context, outputOptions) to "${Environment.DIRECTORY_MOVIES}/$MediaRoot/$fileName"
    } else {
        val dir = File(context.getExternalFilesDir(null), "$MediaRoot/videos").apply { mkdirs() }
        val outputOptions = FileOutputOptions.Builder(File(dir, fileName)).build()
        videoCapture.output.prepareRecording(context, outputOptions) to
            "Android/data/${context.packageName}/files/$MediaRoot/videos/$fileName"
    }
    return pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
        if (event is VideoRecordEvent.Finalize) {
            if (event.hasError()) {
                Log.w("MobileAimCapture", "Video recording failed: ${event.error}", event.cause)
                Toast.makeText(context, "동영상 저장 실패: ${event.cause?.message ?: "code ${event.error}"}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    context,
                    "동영상 저장됨: $displayLocation\n(센서 로그는 $MediaRoot/sensors 에 저장됨)",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
