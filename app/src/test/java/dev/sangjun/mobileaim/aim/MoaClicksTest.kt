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

    @Test
    fun `reprojection is the identity when current zoom matches the zero's zoom`() {
        assertEquals(0.62f, reprojectFractionForZoom(fraction = 0.62f, currentZoomRatio = 2f, zeroZoomRatio = 2f), 1e-6f)
    }

    @Test
    fun `reprojection scales the offset from center by currentZoomRatio over zeroZoomRatio`() {
        // Zeroed at 1x with the reticle 0.1 right of center; viewing at 2x should double that offset.
        val reprojected = reprojectFractionForZoom(fraction = 0.6f, currentZoomRatio = 2f, zeroZoomRatio = 1f)

        assertEquals(0.7f, reprojected, 1e-6f)
    }

    @Test
    fun `reprojection shrinks the offset when viewing below the zero's zoom`() {
        // Zeroed at 4x with the reticle 0.1 left of center; viewing at 1x should quarter that offset.
        val reprojected = reprojectFractionForZoom(fraction = 0.4f, currentZoomRatio = 1f, zeroZoomRatio = 4f)

        assertEquals(0.475f, reprojected, 1e-6f)
    }
}
