package dev.sangjun.mobileaim.aim

import kotlin.math.PI
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
        val angleRad = clicks * MoaPerClick * (PI / 180.0 / 60.0)
        val expected = (focalLengthPx * tan(angleRad) / referenceLengthPx).toFloat()

        assertEquals(expected, clicksToFraction(clicks, focalLengthPx, referenceLengthPx), 1e-6f)
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
}
