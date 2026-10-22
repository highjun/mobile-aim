package dev.sangjun.mobileaim.app.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.sangjun.mobileaim.app.sensor.ShakeMonitor
import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.ZoomState
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
import dev.sangjun.mobileaim.app.recording.ScreenRecordingService
import dev.sangjun.mobileaim.app.recording.ScreenRecordingState
import dev.sangjun.mobileaim.app.settings.ZoomLevels
import dev.sangjun.mobileaim.utils.MediaRoot
import dev.sangjun.mobileaim.utils.fileTimestampNow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var camera by remember { mutableStateOf<Camera?>(null) }
    val onCameraReady = remember { { boundCamera: Camera -> camera = boundCamera } }
    var isProfileSheetOpen by remember { mutableStateOf(false) }
    val uiState by aimViewModel.uiState.collectAsState()

    // Logging = IMU CSV (AimViewModel) + screen recording (ScreenRecordingService). Starting takes
    // up to two system dialogs — the optional microphone/notification permissions, then the
    // screen-capture consent Android shows every time — and the CSV only starts once the consent
    // is accepted, so a cancelled dialog leaves both off.
    var isRequestingLogging by remember { mutableStateOf(false) }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isRequestingLogging = false
        val data = result.data
        val activity = context.findActivity()
        if (result.resultCode != Activity.RESULT_OK || data == null || activity == null) {
            Toast.makeText(context, "화면 녹화를 허용하지 않아 로깅을 시작하지 않았습니다", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val (width, height) = activity.realDisplaySize()
        ScreenRecordingService.start(
            context = context,
            resultCode = result.resultCode,
            resultData = data,
            displayWidth = width,
            displayHeight = height,
            densityDpi = activity.resources.configuration.densityDpi,
        )
        aimViewModel.startLogging()
    }
    val launchScreenCapture = {
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
    // Whatever the user answers here, recording goes ahead — without RECORD_AUDIO it's just
    // video-only, and without POST_NOTIFICATIONS the "로깅 중" notification is merely hidden.
    val loggingPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(context, "마이크 권한이 없어 소리 없이 녹화합니다", Toast.LENGTH_SHORT).show()
        }
        launchScreenCapture()
    }
    val stopLogging = remember(aimViewModel) {
        {
            aimViewModel.stopLogging()
            ScreenRecordingService.stop(context)
        }
    }
    val toggleLogging = {
        when {
            aimViewModel.isLoggingActive -> stopLogging()
            isRequestingLogging -> Unit
            // The previous session's file is still being finalized.
            ScreenRecordingService.state.value != ScreenRecordingState.Idle ->
                Toast.makeText(context, "이전 동영상을 저장하는 중입니다", Toast.LENGTH_SHORT).show()
            else -> {
                isRequestingLogging = true
                val missing = loggingPermissions().filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) launchScreenCapture() else loggingPermissionsLauncher.launch(missing.toTypedArray())
            }
        }
    }

    // Muzzle shake for the on-screen indicator — listens only while the screen is resumed.
    val shakeMonitor = remember { ShakeMonitor(context) }
    val shake by shakeMonitor.reading.collectAsState()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> shakeMonitor.start()
                Lifecycle.Event.ON_PAUSE -> shakeMonitor.stop()
                // Off-screen there's nothing of the sight left to record (the screen recording
                // would show the launcher or the lock screen), so leaving the app ends the whole
                // logging session, CSV included — the old CameraX recording likewise ended when
                // the camera closed on stop.
                Lifecycle.Event.ON_STOP -> stopLogging()
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

    // The recording ended without the logging button (start failed, the system or the user
    // revoked the projection from the status bar, or the Activity was recreated while the service
    // had died): end the CSV session too, so the button never shows "정지" with nothing recording.
    // ScreenRecordingService.start sets Starting synchronously, before startLogging flips the VM,
    // so a session that's just starting never reads as Idle here.
    val recordingState by ScreenRecordingService.state.collectAsState()
    LaunchedEffect(recordingState, uiState.isLoggingActive) {
        if (recordingState == ScreenRecordingState.Idle && aimViewModel.isLoggingActive) aimViewModel.stopLogging()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(
            hasCameraPermission = hasCameraPermission,
            onCameraReady = onCameraReady,
            onCameraFovReady = aimViewModel::onCameraFovAvailable,
            onCameraError = onCameraError,
        )

        AimOverlay(
            uiState = uiState,
            onReticleDrag = aimViewModel::onReticleDrag,
            onToggleLogging = toggleLogging,
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

/** Runtime permissions the logging session asks for (all optional — see the launcher in [MainScreen]). */
private fun loggingPermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * The display's full size in the current orientation (landscape — the Activity is locked to it),
 * including the area under the hidden system bars, so the screen recording is a 1:1 copy of the
 * panel rather than a letterboxed window.
 */
private fun Activity.realDisplaySize(): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.maximumWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        metrics.widthPixels to metrics.heightPixels
    }
}
