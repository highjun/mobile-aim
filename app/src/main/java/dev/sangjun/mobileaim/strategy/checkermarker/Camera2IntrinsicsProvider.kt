package dev.sangjun.mobileaim.strategy.checkermarker

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Auto-estimates camera intrinsics from Camera2 characteristics.
 *
 * Prefers [CameraCharacteristics.LENS_INTRINSIC_CALIBRATION] when the device reports it, falling
 * back to an approximation from sensor physical size + focal length. Either way the result is
 * sensor-referenced, so it is then mapped into the analysis frame's coordinate system by
 * [IntrinsicsTransform] — centre-crop scaling first, then the frame's rotation.
 */
class Camera2IntrinsicsProvider(context: Context) : ICameraIntrinsicsProvider {
    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)

    override fun getIntrinsics(
        cameraId: String,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
    ): CameraIntrinsics? {
        if (frameWidth <= 0 || frameHeight <= 0) return null
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // The caller's frame is post-rotation; the sensor emitted it pre-rotation, so undo the
            // axis swap before scaling against sensor dimensions.
            val bufferWidth = if (IntrinsicsTransform.swapsAxes(rotationDegrees)) frameHeight else frameWidth
            val bufferHeight = if (IntrinsicsTransform.swapsAxes(rotationDegrees)) frameWidth else frameHeight

            val scaled = fromIntrinsicCalibration(characteristics, bufferWidth, bufferHeight)
                ?: fromSensorApproximation(characteristics, bufferWidth, bufferHeight)
                ?: return null

            IntrinsicsTransform.rotate(scaled, rotationDegrees, bufferWidth, bufferHeight)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to resolve camera intrinsics for camera $cameraId", error)
            null
        }
    }

    private fun fromIntrinsicCalibration(
        characteristics: CameraCharacteristics,
        bufferWidth: Int,
        bufferHeight: Int,
    ): CameraIntrinsics? {
        val calibration = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) ?: return null
        if (calibration.size < 4) return null
        val referenceRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
            ?: characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: return null
        if (referenceRect.width() <= 0 || referenceRect.height() <= 0) return null

        // calibration = [fx, fy, cx, cy, skew], referenced to the active array above.
        return IntrinsicsTransform.scaleToFrame(
            sensorFx = calibration[0].toDouble(),
            sensorFy = calibration[1].toDouble(),
            sensorCx = calibration[2].toDouble(),
            sensorCy = calibration[3].toDouble(),
            sensorWidth = referenceRect.width(),
            sensorHeight = referenceRect.height(),
            frameWidth = bufferWidth,
            frameHeight = bufferHeight,
        )
    }

    private fun fromSensorApproximation(
        characteristics: CameraCharacteristics,
        bufferWidth: Int,
        bufferHeight: Int,
    ): CameraIntrinsics? {
        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val focalLengthMm = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()?.toDouble() ?: return null
        if (physicalSize.width <= 0f || physicalSize.height <= 0f) return null

        val pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val sensorWidth = pixelArray?.width?.takeIf { it > 0 } ?: bufferWidth
        val sensorHeight = pixelArray?.height?.takeIf { it > 0 } ?: bufferHeight

        // Focal length in pixels at full sensor resolution, then mapped down like the calibrated path.
        return IntrinsicsTransform.scaleToFrame(
            sensorFx = focalLengthMm * sensorWidth / physicalSize.width,
            sensorFy = focalLengthMm * sensorHeight / physicalSize.height,
            sensorCx = sensorWidth / 2.0,
            sensorCy = sensorHeight / 2.0,
            sensorWidth = sensorWidth,
            sensorHeight = sensorHeight,
            frameWidth = bufferWidth,
            frameHeight = bufferHeight,
        )
    }

    private companion object {
        const val TAG = "MobileAimIntrinsics"
    }
}
