package dev.sangjun.mobileaim.app.camera

/**
 * Enough of a camera's field of view to convert between a screen-space pixel offset and a
 * real-world angle (standard pinhole camera model: `angle = atan(pixels / focalLengthYPx)`).
 *
 * [referenceHeightPx]/[referenceWidthPx] are not the sensor's raw resolution — they're how many
 * sensor pixels are actually visible on screen, in the *same* coordinate system [focalLengthYPx]
 * was measured in. `PreviewView`'s `FILL_CENTER` scale type center-crops the sensor buffer to fill
 * a differently-shaped screen (see [dev.sangjun.mobileaim.app.camera.CameraPreview]), so the raw
 * sensor height overstates what's visible by roughly the crop factor — using it directly here
 * would make every angle-to-pixel conversion wrong by that same factor. [focalLengthYPx] itself
 * needs no such correction: a pixel-unit focal length is invariant to cropping, only to a change
 * of reference resolution (see [dev.sangjun.mobileaim.app.camera.CameraFovProvider]).
 *
 * [zoomRatio] is the digital zoom ratio actually applied by CameraX (`CameraInfo.zoomState`) when
 * this was published — distinct from the `FILL_CENTER` crop above. Zooming crops-and-magnifies the
 * sensor, which narrows the field of view by exactly this factor; callers converting an angle to a
 * screen fraction (see [dev.sangjun.mobileaim.aim.clicksToFraction]) must multiply [focalLengthYPx]
 * by it, or the conversion will silently assume 1x regardless of the actual zoom.
 */
data class CameraFov(
    val focalLengthYPx: Float,
    val referenceHeightPx: Float,
    val referenceWidthPx: Float,
    val zoomRatio: Float = 1f,
)
