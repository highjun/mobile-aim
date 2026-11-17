package dev.sangjun.mobileaim.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Resolves a camera's vertical focal length in pixels — the other half of [CameraFov] (the
 * reference dimensions) comes from [dev.sangjun.mobileaim.app.camera.CameraPreview]'s runtime
 * `PreviewView.getSensorToViewTransform()`, not from here. A pixel-unit focal length is invariant
 * to sensor cropping (only to a change of reference resolution), so whichever of the two sources
 * below is used, it pairs correctly with any reference height/width taken from the same sensor.
 */
class CameraFovProvider(context: Context) {
    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)

    fun getFocalLengthYPx(cameraId: String): Float? = try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        fromIntrinsicCalibration(characteristics) ?: fromSensorApproximation(characteristics)
    } catch (error: Exception) {
        Log.w(TAG, "Failed to resolve focal length for camera $cameraId", error)
        null
    }

    /** [CameraCharacteristics.LENS_INTRINSIC_CALIBRATION] = [fx, fy, cx, cy, skew] when the device reports it (uncommon). */
    private fun fromIntrinsicCalibration(characteristics: CameraCharacteristics): Float? {
        val calibration = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) ?: return null
        if (calibration.size < 4) return null
        return calibration[1]
    }

    /** Fallback: focal length (mm) × sensor pixel height / sensor physical height (mm) — always available. */
    private fun fromSensorApproximation(characteristics: CameraCharacteristics): Float? {
        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val focalLengthMm = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: return null
        if (physicalSize.height <= 0f) return null

        val sensorHeight = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            ?.height?.takeIf { it > 0 } ?: return null

        return focalLengthMm * sensorHeight / physicalSize.height
    }

    private companion object {
        const val TAG = "MobileAimCameraFov"
    }
}
