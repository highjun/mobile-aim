package dev.sangjun.mobileaim.app.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.runtime.LaunchedEffect
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
import dev.sangjun.mobileaim.utils.fileTimestampNow
import java.io.File

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
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val onImageCaptureReady = remember { { capture: ImageCapture -> imageCapture = capture } }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val onVideoCaptureReady = remember { { capture: VideoCapture<Recorder> -> videoCapture = capture } }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isProfileSheetOpen by remember { mutableStateOf(false) }
    val uiState by aimViewModel.uiState.collectAsState()

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
            onImageCaptureReady = onImageCaptureReady,
            onVideoCaptureReady = onVideoCaptureReady,
            onCameraFovReady = aimViewModel::onCameraFovAvailable,
            onCameraError = onCameraError,
        )

        AimOverlay(
            uiState = uiState,
            onReticleDrag = aimViewModel::onReticleDrag,
            onToggleLogging = aimViewModel::toggleLogging,
            onCaptureClick = {
                capturePhoto(context, imageCapture) { captureId -> aimViewModel.logCaptureSnapshot(captureId) }
            },
            isFocusMode = isFocusMode,
            onToggleFocusMode = onToggleFocusMode,
            onBrightnessChange = aimViewModel::onBrightnessChange,
            isProfileSheetOpen = isProfileSheetOpen,
            onOpenProfileSheet = { isProfileSheetOpen = true },
            onDismissProfileSheet = { isProfileSheetOpen = false },
            onSelectProfile = aimViewModel::onSelectProfile,
            onAddProfile = aimViewModel::onAddProfile,
            onRenameProfile = aimViewModel::onRenameProfile,
            onDeleteProfile = aimViewModel::onDeleteProfile,
            onUpdateProfileBallistics = aimViewModel::onUpdateProfileBallistics,
            onCreepUp = aimViewModel::onCreepUp,
            onCreepDown = aimViewModel::onCreepDown,
            onCreepLeft = aimViewModel::onCreepLeft,
            onCreepRight = aimViewModel::onCreepRight,
            onBoresightUp = aimViewModel::onBoresightUp,
            onBoresightDown = aimViewModel::onBoresightDown,
            onBoresightLeft = aimViewModel::onBoresightLeft,
            onBoresightRight = aimViewModel::onBoresightRight,
            modifier = Modifier.fillMaxSize(),
        )
    }
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
 * Saves a frame as `capture_<id>.jpg`, using the same [id] as the row
 * [dev.sangjun.mobileaim.aim.AimViewModel.logCaptureSnapshot] writes to `captures.csv` — that
 * shared id is how a photo and its sensor/pose snapshot get matched up after the fact. Only calls
 * [onSaved] once the file has actually landed, so a snapshot never references a missing photo.
 *
 * Lands in the shared `Pictures/$MediaRoot` collection (via MediaStore) on API 29+ — see
 * [MediaRoot]'s doc for why it's `Pictures/` and not a fully custom top-level folder.
 * On older API levels MediaStore's `RELATIVE_PATH` isn't supported, so it falls back to a
 * `MobileAim/images` subfolder inside the app's own external-files directory. Either way, a toast
 * reports exactly where it landed once the save completes.
 */
private fun capturePhoto(context: Context, imageCapture: ImageCapture?, onSaved: (id: String) -> Unit) {
    val capture = imageCapture ?: return
    val id = fileTimestampNow()
    val fileName = "capture_$id.jpg"
    val outputOptions: ImageCapture.OutputFileOptions
    val displayLocation: String
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$MediaRoot")
        }
        outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        ).build()
        displayLocation = "${Environment.DIRECTORY_PICTURES}/$MediaRoot/$fileName"
    } else {
        val dir = File(context.getExternalFilesDir(null), "$MediaRoot/images").apply { mkdirs() }
        outputOptions = ImageCapture.OutputFileOptions.Builder(File(dir, fileName)).build()
        displayLocation = "Android/data/${context.packageName}/files/$MediaRoot/images/$fileName"
    }
    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Toast.makeText(context, "사진 저장됨: $displayLocation", Toast.LENGTH_SHORT).show()
                onSaved(id)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.w("MobileAimCapture", "Failed to save capture photo", exception)
                Toast.makeText(context, "사진 저장 실패: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        },
    )
}

/**
 * Starts recording camera video (no audio — the logging session only needs to visually confirm
 * the reticle stays put during a shake test, and skipping audio avoids needing RECORD_AUDIO) as
 * `video_<timestamp>.mp4`.
 *
 * Lands in the shared `Movies/$MediaRoot` collection (via MediaStore) on API 29+, same rationale
 * and fallback as [capturePhoto]. The session's raw IMU CSV logs land alongside it under
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
