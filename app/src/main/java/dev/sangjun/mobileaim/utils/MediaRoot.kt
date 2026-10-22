package dev.sangjun.mobileaim.utils

import android.os.Environment

/**
 * `MobileAim`, the shared subfolder name every piece of MobileAim output lands under. Where that
 * subfolder actually lives differs by media type on API 29+, though, because of a MediaStore
 * restriction we don't control: `MediaStore.Images`/`MediaStore.Video` only accept
 * [Environment.DIRECTORY_PICTURES]/[Environment.DIRECTORY_MOVIES] (or DCIM) as the *top-level*
 * `RELATIVE_PATH` segment — anything else throws `IllegalArgumentException: Primary directory
 * MobileAim not allowed for content://media/external/...` at save time, which the old CameraX
 * recorder swallowed into its error callback, so the previous "one unified
 * `MobileAim/{images,videos,sensors}` root" attempt silently failed to save every photo/video (no
 * crash, no toast, no file). So: photos land in `Pictures/$MediaRoot`, videos in
 * `Movies/$MediaRoot` — both required top-level roots, with `MediaRoot` as the sub-brand folder
 * inside them. Sensor CSVs (see [dev.sangjun.mobileaim.logging.CaptureLogWriter]/
 * [dev.sangjun.mobileaim.logging.SensorMotionLogger]) go through `MediaStore.Downloads` instead,
 * which has no such restriction, so they keep the fully custom `MobileAim/sensors` path.
 */
const val MediaRoot = "MobileAim"

/** `RELATIVE_PATH` for logging videos on API 29+ — `Movies/MobileAim`, see [MediaRoot]. */
val VideoRelativePath: String = "${Environment.DIRECTORY_MOVIES}/$MediaRoot"

/** API < 29 fallback folder for logging videos, relative to the app's external-files directory (no `RELATIVE_PATH` there). */
const val LegacyVideoSubdir = "$MediaRoot/videos"

/** `video_<id>.mp4`, with [id] from [fileTimestampNow] — one per logging session. */
fun videoFileName(id: String): String = "video_$id.mp4"
