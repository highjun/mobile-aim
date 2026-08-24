package dev.sangjun.mobileaim.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Auto-estimates the camera's vertical [CameraFov] from Camera2 hardware characteristics — no
 * marker detection, no calibration shot, no OpenCV. Ported (simplified) from the deprecated v0
 * vision pipeline's `Camera2IntrinsicsProvider`: that one produced a full camera matrix mapped
 * into a rotated/cropped analysis frame for `solvePnP`, which is more than this needs. v1 has no
 * `ImageAnalysis` frame to map into — it only needs *one* consistent (focal length, reference
 * dimension) pair to convert a physical drop into a screen-fraction offset (see
 * [dev.sangjun.mobileaim.aim.computeDropOffsetFraction]), so this skips the rotation/frame-size
 * handling entirely.
 *
 * Assumes the common case of a rear camera with `SENSOR_ORIENTATION = 90` (true for the large
 * majority of Android phones): with the phone held in either landscape orientation (this app is
 * landscape-only), the raw sensor buffer's width/height already line up with the displayed
 * screen's width/height, so the sensor's reported pixel-array height can be used directly as the
 * on-screen vertical reference without an explicit axis swap. Any residual error from this (or
 * from `PreviewView`'s center-crop scaling) gets absorbed into the manual gun's configured muzzle
 * velocity — see [dev.sangjun.mobileaim.app.settings.MarkerSettings].
 */
class CameraFovProvider(context: Context) {
    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)

    fun getFov(cameraId: String): CameraFov? = try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        fromIntrinsicCalibration(characteristics) ?: fromSensorApproximation(characteristics)
    } catch (error: Exception) {
        Log.w(TAG, "Failed to resolve camera FOV for camera $cameraId", error)
        null
    }

    /** [CameraCharacteristics.LENS_INTRINSIC_CALIBRATION] = [fx, fy, cx, cy, skew] when the device reports it (uncommon). */
    private fun fromIntrinsicCalibration(characteristics: CameraCharacteristics): CameraFov? {
        val calibration = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) ?: return null
        if (calibration.size < 4) return null
        val referenceRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
            ?: characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: return null
        if (referenceRect.height() <= 0) return null
        return CameraFov(focalLengthYPx = calibration[1], referenceHeightPx = referenceRect.height())
    }

    /** Fallback: focal length (mm) × sensor pixel height / sensor physical height (mm) — always available. */
    private fun fromSensorApproximation(characteristics: CameraCharacteristics): CameraFov? {
        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val focalLengthMm = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: return null
        if (physicalSize.height <= 0f) return null

        val sensorHeight = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            ?.height?.takeIf { it > 0 } ?: return null

        return CameraFov(
            focalLengthYPx = focalLengthMm * sensorHeight / physicalSize.height,
            referenceHeightPx = sensorHeight,
        )
    }

    private companion object {
        const val TAG = "MobileAimCameraFov"
    }
}
