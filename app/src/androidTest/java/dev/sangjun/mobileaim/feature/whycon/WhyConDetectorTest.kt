package dev.sangjun.mobileaim.feature.whycon

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sangjun.mobileaim.config.WhyConConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/** Validates [WhyConDetector] against synthetic rendered markers (drawn with [Imgproc.circle], not real camera noise). */
@RunWith(AndroidJUnit4::class)
class WhyConDetectorTest {
    private val detector = WhyConDetector()

    @Before
    fun setUp() {
        assertTrue("OpenCV native library must load", OpenCVLoader.initLocal())
    }

    @Test
    fun detectsConcentricMarkerAtKnownCenterAndRadius() {
        val gray = renderMarker(centerX = 320.0, centerY = 240.0, outerRadius = 80.0)
        try {
            val ellipse = detector.detect(gray)
            requireNotNull(ellipse) { "expected marker to be detected" }
            assertEquals("cx", 320.0, ellipse.cx, 2.0)
            assertEquals("cy", 240.0, ellipse.cy, 2.0)
            assertEquals("radius a", 80.0, ellipse.a, 2.0)
            assertEquals("radius b", 80.0, ellipse.b, 2.0)
        } finally {
            gray.release()
        }
    }

    @Test
    fun detectsMarkerOffCenter() {
        val gray = renderMarker(centerX = 150.0, centerY = 100.0, outerRadius = 50.0)
        try {
            val ellipse = detector.detect(gray)
            requireNotNull(ellipse) { "expected marker to be detected" }
            assertEquals("cx", 150.0, ellipse.cx, 2.0)
            assertEquals("cy", 100.0, ellipse.cy, 2.0)
        } finally {
            gray.release()
        }
    }

    @Test
    fun returnsNullWhenNoMarkerPresent() {
        val gray = Mat(480, 640, CvType.CV_8UC1, Scalar(255.0))
        try {
            assertNull(detector.detect(gray))
        } finally {
            gray.release()
        }
    }

    @Test
    fun returnsNullForASingleUnpairedCircle() {
        val gray = Mat(480, 640, CvType.CV_8UC1, Scalar(255.0))
        try {
            Imgproc.circle(gray, Point(320.0, 240.0), 80, Scalar(0.0), -1)
            assertNull(detector.detect(gray))
        } finally {
            gray.release()
        }
    }

    /** White background, black ring `[outerRadius*ratio, outerRadius]`, white centre disc. */
    private fun renderMarker(centerX: Double, centerY: Double, outerRadius: Double): Mat {
        val gray = Mat(480, 640, CvType.CV_8UC1, Scalar(255.0))
        val center = Point(centerX, centerY)
        Imgproc.circle(gray, center, outerRadius.toInt(), Scalar(0.0), -1)
        val innerRadius = (outerRadius * WhyConConfig.INNER_OUTER_RADIUS_RATIO).toInt()
        Imgproc.circle(gray, center, innerRadius, Scalar(255.0), -1)
        return gray
    }
}
