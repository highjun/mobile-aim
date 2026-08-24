package dev.sangjun.mobileaim.app.camera

/**
 * Enough of a camera's vertical field of view to convert between a screen-space pixel offset and
 * a real-world angle (standard pinhole camera model: `angle = atan(pixels / focalLengthYPx)`).
 * [focalLengthYPx] and [referenceHeightPx] must come from the same coordinate system (whatever
 * resolution they were derived at) — only their *ratio* matters, so the absolute resolution is
 * irrelevant as long as they're consistent with each other.
 */
data class CameraFov(val focalLengthYPx: Float, val referenceHeightPx: Int)
