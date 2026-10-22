package dev.sangjun.mobileaim.app.recording

import kotlin.math.max

/** Encoded video frame size in pixels — always a multiple of [RecordingSizeAlignment] on both sides. */
data class VideoSize(val width: Int, val height: Int)

/** The MediaRecorder settings a logging session's screen recording uses — see [screenRecordingConfig]. */
data class ScreenRecordingConfig(
    val size: VideoSize,
    val frameRate: Int,
    val videoBitRate: Int,
    val audioSamplingRate: Int,
    val audioBitRate: Int,
    val audioChannels: Int,
)

/** 60fps: matches the camera's pinned AE frame rate (see [dev.sangjun.mobileaim.app.camera.AimCaptureRequestOptions]), so the video doesn't drop preview frames. */
const val RecordingFrameRate = 60

/** ~16 Mbps: enough for 60fps at the S25 Ultra's native 3120x1440 without the reticle's thin lines smearing into blocks. */
const val RecordingVideoBitRate = 16_000_000

/** 48 kHz mono AAC — gunshots are the only thing the audio track is for, so stereo buys nothing. */
const val RecordingAudioSamplingRate = 48_000
const val RecordingAudioBitRate = 128_000
const val RecordingAudioChannels = 1

/** H.264 codes 16x16 macroblocks; some hardware encoders reject (or green-pad) sizes that aren't a multiple of it. */
const val RecordingSizeAlignment = 16

/** Each step down when the encoder rejects a size — keeps the aspect ratio, ~20% fewer pixels per side. */
private const val DownscaleStep = 0.8

/** Enough steps to get from any phone panel down to well under 720p. */
private const val MaxDownscaleSteps = 8

/**
 * Picks the largest [RecordingSizeAlignment]-aligned size at the display's own aspect ratio that
 * the encoder accepts ([isSizeSupported]), starting from the display's real size
 * ([displayWidth]x[displayHeight], already in the current orientation — the app runs landscape).
 * Native size first so the recording is a pixel-for-pixel copy of what the shooter saw; only
 * scaled down if the encoder can't take it. Falls back to the smallest size tried if nothing fits —
 * MediaRecorder's `prepare()` then reports the real error.
 */
fun chooseRecordingSize(
    displayWidth: Int,
    displayHeight: Int,
    isSizeSupported: (width: Int, height: Int) -> Boolean,
): VideoSize {
    require(displayWidth > 0 && displayHeight > 0) { "display size must be positive: ${displayWidth}x$displayHeight" }
    var scale = 1.0
    var candidate = alignedSize(displayWidth, displayHeight, scale)
    repeat(MaxDownscaleSteps) {
        if (isSizeSupported(candidate.width, candidate.height)) return candidate
        scale *= DownscaleStep
        candidate = alignedSize(displayWidth, displayHeight, scale)
    }
    return candidate
}

/**
 * The full recording config for a [displayWidth]x[displayHeight] screen. [maxVideoBitRate] is the
 * encoder's reported ceiling (null if unknown) — [RecordingVideoBitRate] is clamped to it.
 */
fun screenRecordingConfig(
    displayWidth: Int,
    displayHeight: Int,
    maxVideoBitRate: Int?,
    isSizeSupported: (width: Int, height: Int) -> Boolean,
): ScreenRecordingConfig = ScreenRecordingConfig(
    size = chooseRecordingSize(displayWidth, displayHeight, isSizeSupported),
    frameRate = RecordingFrameRate,
    videoBitRate = maxVideoBitRate?.let { minOf(RecordingVideoBitRate, it) } ?: RecordingVideoBitRate,
    audioSamplingRate = RecordingAudioSamplingRate,
    audioBitRate = RecordingAudioBitRate,
    audioChannels = RecordingAudioChannels,
)

private fun alignedSize(width: Int, height: Int, scale: Double): VideoSize = VideoSize(
    width = alignDown((width * scale).toInt()),
    height = alignDown((height * scale).toInt()),
)

private fun alignDown(value: Int): Int = max(RecordingSizeAlignment, value / RecordingSizeAlignment * RecordingSizeAlignment)
