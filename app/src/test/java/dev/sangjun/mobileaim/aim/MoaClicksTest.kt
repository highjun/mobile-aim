package dev.sangjun.mobileaim.aim

import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Test

class MoaClicksTest {

    @Test
    fun `zero clicks yields zero offset`() {
        assertEquals(0f, clicksToFraction(clicks = 0, focalLengthPx = 1000f, referenceLengthPx = 1000f), 0f)
    }

    @Test
    fun `matches hand-computed offset for 6 clicks`() {
        val clicks = 6
        val focalLengthPx = 1000f
        val referenceLengthPx = 1080f
        val angleRad = clicks * 0.008 / 25.0
        val expected = (focalLengthPx * tan(angleRad) / referenceLengthPx).toFloat()

        assertEquals(expected, clicksToFraction(clicks, focalLengthPx, referenceLengthPx), 1e-6f)
    }

    @Test
    fun `one click is 8mm at 25m`() {
        // A pinhole with focal length f puts an 8mm offset at 25m at f * 8 / 25000 px.
        val focalLengthPx = 2807f
        val offsetPx = clicksToFraction(clicks = 1, focalLengthPx = focalLengthPx, referenceLengthPx = 1f)

        assertEquals(focalLengthPx * 8f / 25_000f, offsetPx, 1e-3f)
    }

    @Test
    fun `is an odd function of click count`() {
        val positive = clicksToFraction(clicks = 6, focalLengthPx = 1000f, referenceLengthPx = 1080f)
        val negative = clicksToFraction(clicks = -6, focalLengthPx = 1000f, referenceLengthPx = 1080f)

        assertEquals(-positive, negative, 1e-6f)
    }

    @Test
    fun `scales linearly with focal length`() {
        val base = clicksToFraction(clicks = 4, focalLengthPx = 500f, referenceLengthPx = 1080f)
        val doubled = clicksToFraction(clicks = 4, focalLengthPx = 1000f, referenceLengthPx = 1080f)

        assertEquals(base * 2f, doubled, 1e-6f)
    }

    @Test
    fun `non-positive reference length yields zero offset`() {
        assertEquals(0f, clicksToFraction(clicks = 4, focalLengthPx = 1000f, referenceLengthPx = 0f), 0f)
    }

    @Test
    fun `click size follows the zeroing target square`() {
        val focalLengthPx = 2807f
        val k1a = clicksToFraction(clicks = 1, focalLengthPx = focalLengthPx, referenceLengthPx = 1f, radiansPerClick = radiansPerClick(ZeroingTarget.K1A.squareMm))

        assertEquals(focalLengthPx * 10f / 25_000f, k1a, 1e-3f)
        assertEquals(radiansPerClick(DefaultClickSizeMm), RadiansPerClick, 0f)
    }
}
