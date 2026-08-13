package dev.sangjun.mobileaim.strategy.circlemarker

import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

/**
 * Detects a concentric black-ring/white-centre circular marker in a grayscale frame and returns
 * the *outer* ring's fitted ellipse.
 *
 * Pairs candidate circular contours by concentricity + area ratio rather than trusting
 * findContours' parent/child hierarchy directly — for a 3-tone target (background, ring, centre)
 * the hierarchy depth depends on details (does the background itself count as a blob, does the
 * ring touch the frame edge, ...) that are easy to get subtly wrong; scanning a flat contour list
 * for "two circles, same centre, right area ratio" sidesteps all of that.
 */
class WhyConDetector {
    fun detect(gray: Mat): RotatedEllipse? {
        val binary = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        return try {
            Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE)

            val circles = contours.mapNotNull { toCircleCandidate(it) }
            val expectedAreaRatio = WhyConConfig.INNER_OUTER_RADIUS_RATIO * WhyConConfig.INNER_OUTER_RADIUS_RATIO
            for (outer in circles) {
                for (inner in circles) {
                    if (outer === inner || inner.area >= outer.area) continue
                    val centerOffset = hypot(outer.cx - inner.cx, outer.cy - inner.cy)
                    if (centerOffset > WhyConConfig.MAX_CENTER_OFFSET_PX) continue
                    val areaRatio = inner.area / outer.area
                    if (abs(areaRatio - expectedAreaRatio) > expectedAreaRatio * WhyConConfig.AREA_RATIO_TOLERANCE) continue
                    return outer.ellipse
                }
            }
            null
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to analyze camera frame", error)
            null
        } finally {
            binary.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun toCircleCandidate(contour: MatOfPoint): CircleCandidate? {
        val points = contour.toArray()
        if (points.size < MIN_ELLIPSE_FIT_POINTS) return null
        val area = Imgproc.contourArea(contour)
        if (area < WhyConConfig.MIN_CONTOUR_AREA_PX) return null

        val points2f = MatOfPoint2f(*points)
        val perimeter = Imgproc.arcLength(points2f, true)
        if (perimeter <= 0.0) return null
        val circularity = 4.0 * PI * area / (perimeter * perimeter)
        if (circularity < WhyConConfig.MIN_CIRCULARITY) return null

        val rect = Imgproc.fitEllipse(points2f)
        val majorMinor = listOf(rect.size.width, rect.size.height).sortedDescending()
        // fitEllipse's `angle` is the rotation of `size.width`'s axis from horizontal — if
        // `height` is actually the longer axis, the true major-axis angle is perpendicular to it.
        val angleRad = if (rect.size.width >= rect.size.height) {
            Math.toRadians(rect.angle)
        } else {
            Math.toRadians(rect.angle + 90.0)
        }
        val ellipse = RotatedEllipse(
            cx = rect.center.x,
            cy = rect.center.y,
            a = majorMinor[0] / 2.0,
            b = majorMinor[1] / 2.0,
            angleRad = angleRad,
        )
        return CircleCandidate(rect.center.x, rect.center.y, area, ellipse)
    }

    private data class CircleCandidate(val cx: Double, val cy: Double, val area: Double, val ellipse: RotatedEllipse)

    private companion object {
        const val TAG = "MobileAimWhyCon"

        /** [Imgproc.fitEllipse]'s documented minimum point count. */
        const val MIN_ELLIPSE_FIT_POINTS = 5
    }
}
