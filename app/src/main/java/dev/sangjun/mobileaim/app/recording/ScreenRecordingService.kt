package dev.sangjun.mobileaim.app.recording

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.sangjun.mobileaim.MainActivity
import dev.sangjun.mobileaim.R
import dev.sangjun.mobileaim.utils.LegacyVideoSubdir
import dev.sangjun.mobileaim.utils.MediaRoot
import dev.sangjun.mobileaim.utils.VideoRelativePath
import dev.sangjun.mobileaim.utils.fileTimestampNow
import dev.sangjun.mobileaim.utils.videoFileName
import java.io.File
import java.io.FileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle of [ScreenRecordingService]'s one recording, as seen by [dev.sangjun.mobileaim.app.ui.MainScreen]. */
enum class ScreenRecordingState {
    /** Nothing recording — never started, finished, or failed to start. */
    Idle,

    /** [ScreenRecordingService.start] was called; the service hasn't set up the recorder yet. */
    Starting,

    Recording,
}

/**
 * Records the whole screen — camera preview *and* every overlay on it (reticle, shake bar, header),
 * exactly what the shooter saw — plus microphone audio (gunshots) for one logging session, as
 * `video_<timestamp>.mp4` in `Movies/$MediaRoot` (see [MediaRoot]). A CameraX `VideoCapture` only
 * ever sees raw sensor frames, so it can't show where the reticle sat during a shot.
 *
 * A foreground service of type `mediaProjection` because Android 10+ only hands out a
 * [MediaProjection] to one, and Android 14+ additionally requires [startForeground] with that type
 * *before* [MediaProjectionManager.getMediaProjection] and a fresh consent result per session (the
 * token can't be reused). [dev.sangjun.mobileaim.app.ui.MainScreen] gets that consent, then calls
 * [start]; [stop] (logging button, app leaving the foreground) finalizes the file. If the system
 * revokes the projection (the "stop sharing" chip, another app starting a projection), the
 * [MediaProjection.Callback] finalizes the same way, and [state] going back to [ScreenRecordingState.Idle]
 * tells MainScreen to end the logging session too.
 */
class ScreenRecordingService : Service() {

    /** Everything one recording owns — released together by [finishRecording]. */
    private class Session(
        val projection: MediaProjection,
        val projectionCallback: MediaProjection.Callback,
        val recorder: MediaRecorder,
        val virtualDisplay: VirtualDisplay,
        val output: VideoOutput,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: Session? = null

    /** True while [finishRecording]'s background teardown runs — [state] stays [ScreenRecordingState.Recording] until it's done. */
    private var isFinalizing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStart -> handleStart(intent)
            ActionStop -> finishRecording()
            // Restarted by the system with a null intent: the consent token died with the old
            // process, so there's nothing to resume.
            else -> stopSelfAndReset()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        finishRecording()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        // Must come first — before getMediaProjection on Android 14+, and within the
        // startForegroundService() deadline on every version.
        ServiceCompat.startForeground(
            this,
            NotificationId,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0,
        )
        if (session != null) return

        val resultCode = intent.getIntExtra(ExtraResultCode, Activity.RESULT_CANCELED)
        val resultData = IntentCompat.getParcelableExtra(intent, ExtraResultData, Intent::class.java)
        val displayWidth = intent.getIntExtra(ExtraDisplayWidth, 0)
        val displayHeight = intent.getIntExtra(ExtraDisplayHeight, 0)
        val densityDpi = intent.getIntExtra(ExtraDensityDpi, 0)
        if (resultCode != Activity.RESULT_OK || resultData == null || displayWidth <= 0 || displayHeight <= 0) {
            Log.w(Tag, "Missing consent result or display size; not recording")
            stopSelfAndReset()
            return
        }

        var output: VideoOutput? = null
        var recorder: MediaRecorder? = null
        var projection: MediaProjection? = null
        var virtualDisplay: VirtualDisplay? = null
        try {
            val config = screenRecordingConfig(displayWidth, displayHeight, avcEncoderMaxBitRate(), ::isAvcSizeSupported)
            val withAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            output = openVideoOutput(this, videoFileName(fileTimestampNow()))
            recorder = buildRecorder(config, withAudio, output.fileDescriptor)

            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection unavailable")
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    // Also fires for our own projection.stop() in finishRecording — by then
                    // `session` is already null, so this is a no-op there.
                    mainHandler.post { finishRecording() }
                }
            }
            // Android 14+ throws from createVirtualDisplay unless a callback is registered first.
            projection.registerCallback(callback, mainHandler)
            virtualDisplay = projection.createVirtualDisplay(
                "MobileAimLogging",
                config.size.width,
                config.size.height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null,
            ) ?: error("VirtualDisplay unavailable")
            recorder.start()
            session = Session(projection, callback, recorder, virtualDisplay, output)
            mutableState.value = ScreenRecordingState.Recording
            Log.i(Tag, "Recording ${config.size.width}x${config.size.height}@${config.frameRate} audio=$withAudio → ${output.displayLocation}")
        } catch (error: Exception) {
            Log.w(Tag, "Failed to start screen recording", error)
            virtualDisplay?.release()
            recorder?.release()
            projection?.stop()
            output?.discard(this)
            Toast.makeText(this, "화면 녹화 시작 실패: ${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            stopSelfAndReset()
        }
    }

    /**
     * Stops the recorder and finalizes the file, then stops the service. The slow part
     * ([MediaRecorder.stop] flushing the encoders, the MediaStore update) runs off the main thread
     * so tapping "정지" doesn't hitch the preview. Safe to call repeatedly.
     */
    private fun finishRecording() {
        if (isFinalizing) return
        val current = session
        session = null
        if (current == null) {
            stopSelfAndReset()
            return
        }
        isFinalizing = true
        Thread({
            val saved = try {
                current.recorder.stop()
                true
            } catch (error: RuntimeException) {
                // Thrown when no frame ever reached the encoder (e.g. stopped right after starting).
                Log.w(Tag, "MediaRecorder.stop failed; discarding the file", error)
                false
            }
            current.recorder.release()
            current.virtualDisplay.release()
            current.projection.unregisterCallback(current.projectionCallback)
            current.projection.stop()
            if (saved) current.output.publish(this) else current.output.discard(this)
            mainHandler.post {
                val message = if (saved) {
                    "동영상 저장됨: ${current.output.displayLocation}\n(센서 로그는 $MediaRoot/sensors 에 저장됨)"
                } else {
                    "동영상 저장 실패: 녹화된 화면이 없습니다"
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                // Only now, so a quick stop→start can't race the old session's teardown (MainScreen
                // won't start a new one until state is Idle again).
                isFinalizing = false
                stopSelfAndReset()
            }
        }, "MobileAimRecordingFinalize").start()
    }

    private fun stopSelfAndReset() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        mutableState.value = ScreenRecordingState.Idle
    }

    /**
     * Video from the virtual display's surface, audio from [MediaRecorder.AudioSource.CAMCORDER]
     * (the mic the HAL tunes for video recording — on most phones the one next to the rear
     * camera, facing downrange) when [withAudio]; video-only otherwise.
     */
    private fun buildRecorder(config: ScreenRecordingConfig, withAudio: Boolean, fileDescriptor: FileDescriptor): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                if (withAudio) setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(fileDescriptor)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(config.size.width, config.size.height)
                setVideoFrameRate(config.frameRate)
                setVideoEncodingBitRate(config.videoBitRate)
                if (withAudio) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(config.audioSamplingRate)
                    setAudioEncodingBitRate(config.audioBitRate)
                    setAudioChannels(config.audioChannels)
                }
                prepare()
            }
        } catch (error: Exception) {
            recorder.release()
            throw error
        }
        return recorder
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(NotificationChannelId, "로깅", NotificationManager.IMPORTANCE_LOW).apply {
                description = "로깅 중 화면 녹화 상태"
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.ic_stat_logging)
            .setContentTitle("로깅 중")
            .setContentText("화면과 소리를 녹화하고 있습니다")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val Tag = "MobileAimRecording"
        private const val NotificationChannelId = "logging"
        private const val NotificationId = 1
        private const val ActionStart = "dev.sangjun.mobileaim.action.START_SCREEN_RECORDING"
        private const val ActionStop = "dev.sangjun.mobileaim.action.STOP_SCREEN_RECORDING"
        private const val ExtraResultCode = "resultCode"
        private const val ExtraResultData = "resultData"
        private const val ExtraDisplayWidth = "displayWidth"
        private const val ExtraDisplayHeight = "displayHeight"
        private const val ExtraDensityDpi = "densityDpi"

        private val mutableState = MutableStateFlow(ScreenRecordingState.Idle)

        /** Process-wide, since there's only ever one recording — MainScreen ends the logging session when this drops back to [ScreenRecordingState.Idle]. */
        val state: StateFlow<ScreenRecordingState> = mutableState.asStateFlow()

        /**
         * Starts recording with the screen-capture consent result ([resultCode]/[resultData] from
         * [MediaProjectionManager.createScreenCaptureIntent]) at the display's real size
         * ([displayWidth]x[displayHeight] in the current orientation). Must be called from the
         * foreground (Android 12+ forbids starting a foreground service from the background).
         */
        fun start(context: Context, resultCode: Int, resultData: Intent, displayWidth: Int, displayHeight: Int, densityDpi: Int) {
            mutableState.value = ScreenRecordingState.Starting
            val intent = Intent(context, ScreenRecordingService::class.java)
                .setAction(ActionStart)
                .putExtra(ExtraResultCode, resultCode)
                .putExtra(ExtraResultData, resultData)
                .putExtra(ExtraDisplayWidth, displayWidth)
                .putExtra(ExtraDisplayHeight, displayHeight)
                .putExtra(ExtraDensityDpi, densityDpi)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Finalizes the current recording. Sent as a command rather than `stopService()` so it
         * queues behind a still-pending [start] instead of killing the service before it reached
         * `startForeground` (which crashes the app). No-op when nothing is recording.
         */
        fun stop(context: Context) {
            if (mutableState.value == ScreenRecordingState.Idle) return
            try {
                context.startService(Intent(context, ScreenRecordingService::class.java).setAction(ActionStop))
            } catch (error: IllegalStateException) {
                // Background start refused — only possible if the service is already gone, in
                // which case there's nothing left to stop.
                Log.w(Tag, "Stop command refused; service not running", error)
                mutableState.value = ScreenRecordingState.Idle
            }
        }

        /** MediaRecorder picks the first H.264 encoder in the codec list — the hardware one on real devices — so its limits are the ones that count. */
        private fun avcEncoderCapabilities(): MediaCodecInfo.VideoCapabilities? =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .firstOrNull { info -> info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } }
                ?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                ?.videoCapabilities

        private fun avcEncoderMaxBitRate(): Int? = avcEncoderCapabilities()?.bitrateRange?.upper

        /** Unknown capabilities count as supported — MediaRecorder's `prepare()` is the real arbiter then. */
        private fun isAvcSizeSupported(width: Int, height: Int): Boolean =
            avcEncoderCapabilities()?.isSizeSupported(width, height) ?: true
    }
}

/**
 * Where one recording is being written: a pending MediaStore row on API 29+ (hidden from the
 * gallery until [publish] clears `IS_PENDING`, so a half-written file never shows up), or a plain
 * file in the app's external-files directory below that, same fallback as the screenshots.
 */
private class VideoOutput(
    val fileDescriptor: FileDescriptor,
    val displayLocation: String,
    private val parcelFileDescriptor: ParcelFileDescriptor,
    private val uri: Uri?,
    private val file: File?,
) {
    fun publish(context: Context) {
        parcelFileDescriptor.close()
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
    }

    fun discard(context: Context) {
        parcelFileDescriptor.close()
        if (uri != null) context.contentResolver.delete(uri, null, null)
        file?.delete()
    }
}

private fun openVideoOutput(context: Context, fileName: String): VideoOutput {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, VideoRelativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("MediaStore rejected video insert")
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "rw") ?: error("MediaStore returned no file descriptor")
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
        VideoOutput(pfd.fileDescriptor, "$VideoRelativePath/$fileName", pfd, uri, null)
    } else {
        val dir = File(context.getExternalFilesDir(null), LegacyVideoSubdir).apply { mkdirs() }
        val file = File(dir, fileName)
        val pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        )
        VideoOutput(pfd.fileDescriptor, "Android/data/${context.packageName}/files/$LegacyVideoSubdir/$fileName", pfd, null, file)
    }
}
