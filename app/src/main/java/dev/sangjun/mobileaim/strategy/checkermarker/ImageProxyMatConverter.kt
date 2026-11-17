package dev.sangjun.mobileaim.strategy.checkermarker

import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Converts CameraX [ImageProxy] frames (YUV_420_888) into rotation-corrected, single-channel
 * grayscale OpenCV [Mat]s, by copying the Y plane directly.
 *
 * This used to round-trip every frame through JPEG (quality 75) via [android.graphics.YuvImage],
 * inherited from an earlier color-blob-detection pipeline where the loss didn't matter. ArUco/
 * ChArUco corner detection is sub-pixel, so those compression artifacts were corrupting the exact
 * signal it depends on. ArUco/ChArUco detectors only need luminance, so skipping chroma entirely
 * (rather than reconstructing NV21 and converting to BGR) is both simpler and lossless.
 */
class ImageProxyMatConverter {
    fun toGrayMat(image: ImageProxy): Mat {
        val gray = image.toYPlaneMat()
        val rotationDegrees = image.imageInfo.rotationDegrees
        val rotateCode = rotationDegrees.toRotateCode() ?: return gray

        val rotated = Mat()
        Core.rotate(gray, rotated, rotateCode)
        gray.release()
        return rotated
    }

    private fun ImageProxy.toYPlaneMat(): Mat {
        val plane = planes[0]
        val buffer = plane.buffer.duplicate()
        val mat = Mat(height, width, CvType.CV_8UC1)

        if (plane.rowStride == width && plane.pixelStride == 1) {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            mat.put(0, 0, bytes)
            return mat
        }

        val row = ByteArray(width)
        for (y in 0 until height) {
            if (plane.pixelStride == 1) {
                buffer.position(y * plane.rowStride)
                buffer.get(row, 0, width)
            } else {
                for (x in 0 until width) {
                    row[x] = buffer.get(y * plane.rowStride + x * plane.pixelStride)
                }
            }
            mat.put(y, 0, row)
        }
        return mat
    }

    private fun Int.toRotateCode(): Int? = when (((this % 360) + 360) % 360) {
        90 -> Core.ROTATE_90_CLOCKWISE
        180 -> Core.ROTATE_180
        270 -> Core.ROTATE_90_COUNTERCLOCKWISE
        else -> null
    }
}
