package dev.sangjun.mobileaim.feature.whycon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ConicMathTest {
    @Test
    fun `boundary points of an axis-aligned ellipse satisfy the conic equation`() {
        val ellipse = RotatedEllipse(cx = 50.0, cy = 30.0, a = 20.0, b = 10.0, angleRad = 0.0)
        val conic = ConicMath.ellipseToConic(ellipse)
        for (deg in 0 until 360 step 5) {
            val t = Math.toRadians(deg.toDouble())
            val x = ellipse.cx + ellipse.a * cos(t)
            val y = ellipse.cy + ellipse.b * sin(t)
            assertEquals("angle=$deg", 0.0, ConicMath.evaluate(conic, x, y), 1e-9)
        }
    }

    @Test
    fun `boundary points of a rotated, offset ellipse satisfy the conic equation`() {
        val ellipse = RotatedEllipse(cx = -15.0, cy = 200.0, a = 40.0, b = 12.0, angleRad = Math.toRadians(37.0))
        val conic = ConicMath.ellipseToConic(ellipse)
        val cosA = cos(ellipse.angleRad)
        val sinA = sin(ellipse.angleRad)
        for (deg in 0 until 360 step 5) {
            val t = Math.toRadians(deg.toDouble())
            val lx = ellipse.a * cos(t)
            val ly = ellipse.b * sin(t)
            val x = ellipse.cx + lx * cosA - ly * sinA
            val y = ellipse.cy + lx * sinA + ly * cosA
            assertEquals("angle=$deg", 0.0, ConicMath.evaluate(conic, x, y), 1e-6)
        }
    }

    @Test
    fun `center point does not satisfy the conic equation`() {
        val ellipse = RotatedEllipse(cx = 10.0, cy = 10.0, a = 5.0, b = 3.0, angleRad = 0.0)
        val conic = ConicMath.ellipseToConic(ellipse)
        assertTrue(ConicMath.evaluate(conic, ellipse.cx, ellipse.cy) < 0.0)
    }

    @Test
    fun `points well outside the ellipse do not satisfy the conic equation`() {
        val ellipse = RotatedEllipse(cx = 0.0, cy = 0.0, a = 20.0, b = 10.0, angleRad = 0.0)
        val conic = ConicMath.ellipseToConic(ellipse)
        assertTrue(ConicMath.evaluate(conic, 1000.0, 1000.0) > 0.0)
    }
}
