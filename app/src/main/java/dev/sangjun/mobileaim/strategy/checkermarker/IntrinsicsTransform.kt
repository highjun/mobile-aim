package dev.sangjun.mobileaim.strategy.checkermarker

import kotlin.math.max

/**
 * Pure transformations taking sensor-referenced intrinsics to the coordinate frame our analysis
 * Mats actually live in. Kept free of Android/OpenCV types so the (easy-to-get-subtly-wrong) math
 * can be unit tested on the JVM — see IntrinsicsTransformTest.
 */
object IntrinsicsTransform {

    /**
     * Rescales intrinsics from the sensor's active array to an analysis buffer of
     * [frameWidth]x[frameHeight], assuming the pipeline scales uniformly and centre-crops the
     * excess (which is what CameraX does when aspect ratios differ).
     *
     * A per-axis scale would only be right when the aspect ratios happen to match; when they don't
     * it silently skews fx against fy and corrupts every projection.
     */
    fun scaleToFrame(
        sensorFx: Double,
        sensorFy: Double,
        sensorCx: Double,
        sensorCy: Double,
        sensorWidth: Int,
        sensorHeight: Int,
        frameWidth: Int,
        frameHeight: Int,
    ): CameraIntrinsics {
        require(sensorWidth > 0 && sensorHeight > 0 && frameWidth > 0 && frameHeight > 0) {
            "sensor and frame dimensions must be positive"
        }
        val scale = max(
            frameWidth.toDouble() / sensorWidth,
            frameHeight.toDouble() / sensorHeight,
        )
        val cropX = (sensorWidth * scale - frameWidth) / 2.0
        val cropY = (sensorHeight * scale - frameHeight) / 2.0
        return CameraIntrinsics(
            fx = sensorFx * scale,
            fy = sensorFy * scale,
            cx = sensorCx * scale - cropX,
            cy = sensorCy * scale - cropY,
            referenceWidth = frameWidth,
            referenceHeight = frameHeight,
        )
    }

    /**
     * Reorients intrinsics to match a buffer that has been rotated clockwise by [rotationDegrees]
     * (what `ImageProxy.imageInfo.rotationDegrees` reports and what our converter applies to the
     * bitmap). [frameWidth]/[frameHeight] describe the buffer *before* rotation.
     *
     * Without this, a 90 degree rotation leaves fx paired with the wrong image axis and puts the
     * principal point in the wrong corner.
     */
    fun rotate(
        intrinsics: CameraIntrinsics,
        rotationDegrees: Int,
        frameWidth: Int,
        frameHeight: Int,
    ): CameraIntrinsics = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> intrinsics.copy(
            // (x, y) -> (h - y, x)
            fx = intrinsics.fy,
            fy = intrinsics.fx,
            cx = frameHeight - intrinsics.cy,
            cy = intrinsics.cx,
            referenceWidth = frameHeight,
            referenceHeight = frameWidth,
        )
        180 -> intrinsics.copy(
            // (x, y) -> (w - x, h - y)
            cx = frameWidth - intrinsics.cx,
            cy = frameHeight - intrinsics.cy,
            referenceWidth = frameWidth,
            referenceHeight = frameHeight,
        )
        270 -> intrinsics.copy(
            // (x, y) -> (y, w - x)
            fx = intrinsics.fy,
            fy = intrinsics.fx,
            cx = intrinsics.cy,
            cy = frameWidth - intrinsics.cx,
            referenceWidth = frameHeight,
            referenceHeight = frameWidth,
        )
        else -> intrinsics
    }

    /** True when [rotationDegrees] swaps the buffer's width and height. */
    fun swapsAxes(rotationDegrees: Int): Boolean {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return normalized == 90 || normalized == 270
    }
}
