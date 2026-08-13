package dev.sangjun.mobileaim.feature.aim

import dev.sangjun.mobileaim.feature.geometry.Point2Px
import org.junit.Assert.assertEquals
import org.junit.Test

class PointSmootherTest {
    @Test
    fun `first sample passes through unchanged`() {
        val smoother = PointSmoother(alpha = 0.25f)
        val result = smoother.smooth(Point2Px(100f, 200f))
        assertEquals(100f, result.x, 0f)
        assertEquals(200f, result.y, 0f)
    }

    @Test
    fun `second sample blends toward raw by alpha`() {
        val smoother = PointSmoother(alpha = 0.25f)
        smoother.smooth(Point2Px(0f, 0f))
        val result = smoother.smooth(Point2Px(100f, 0f))
        assertEquals(25f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun `reset drops history so next sample passes through unchanged`() {
        val smoother = PointSmoother(alpha = 0.25f)
        smoother.smooth(Point2Px(0f, 0f))
        smoother.smooth(Point2Px(100f, 0f))
        smoother.reset()
        val result = smoother.smooth(Point2Px(500f, 500f))
        assertEquals(500f, result.x, 0f)
        assertEquals(500f, result.y, 0f)
    }

    @Test
    fun `converges toward raw value over repeated identical samples`() {
        val smoother = PointSmoother(alpha = 0.25f)
        var result = smoother.smooth(Point2Px(0f, 0f))
        repeat(50) { result = smoother.smooth(Point2Px(100f, 0f)) }
        assertEquals(100f, result.x, 0.01f)
    }
}
