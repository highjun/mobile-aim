package dev.sangjun.mobileaim.aim

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BallisticDropTest {

    @Test
    fun `matches hand-computed band when HOB exceeds target radius`() {
        // HOB (12cm) > target radius (10cm), so e(0) starts outside the band — both edges come
        // from the positive roots of e(d) = -R, not the d=0 shortcut.
        val zeroDistance = 5f
        val velocity = 61f
        val hobCm = 12f
        val targetRadiusCm = 10f

        val g = 9.8
        val hobM = hobCm / 100.0
        val targetRadiusM = targetRadiusCm / 100.0
        val k = g / (2.0 * velocity * velocity)
        val theta = (hobM + k * zeroDistance * zeroDistance) / zeroDistance
        val c = hobM - targetRadiusM
        val discriminant = theta * theta - 4.0 * k * c
        val sqrtDiscriminant = sqrt(discriminant)
        val expectedNear = ((theta - sqrtDiscriminant) / (2.0 * k)).toFloat()
        val expectedFar = ((theta + sqrtDiscriminant) / (2.0 * k)).toFloat()

        val band = computeMpbrBand(zeroDistance, velocity, hobCm, targetRadiusCm)

        assertTrue("expected a band, got null", band != null)
        assertEquals(expectedNear, band!!.start, 1e-3f)
        assertEquals(expectedFar, band.endInclusive, 1e-3f)
    }

    @Test
    fun `band starts at zero when HOB is within target radius`() {
        // HOB (4cm) < target radius (10cm): e(0) = -HOB is already inside [-R, R].
        val band = computeMpbrBand(zeroDistanceM = 5f, velocityMps = 61f, hobCm = 4f, targetRadiusCm = 10f)

        assertTrue("expected a band, got null", band != null)
        assertEquals(0f, band!!.start, 0f)
        assertTrue("far edge should be beyond the zero distance", band.endInclusive > 5f)
    }

    @Test
    fun `returns null when the trajectory peak exceeds the target radius`() {
        // A 61 m/s toy gun zeroed way out at 200m rises far more than 5cm at its midpoint.
        val band = computeMpbrBand(zeroDistanceM = 200f, velocityMps = 61f, hobCm = 4f, targetRadiusCm = 5f)

        assertNull(band)
    }

    @Test
    fun `firearm reference values comfortably cover the 100 to 250m combat range`() {
        // docs/선행기술-도트사이트.md: K2C1 (920 m/s), HOB 4cm, IPSC half-height 38cm, zeroed at
        // its own MPBR (~362m) — claims 100~250m is fully inside the resulting band.
        val band = computeMpbrBand(zeroDistanceM = 362f, velocityMps = 920f, hobCm = 4f, targetRadiusCm = 38f)

        assertTrue("expected a band, got null", band != null)
        assertTrue("near edge should be at or before 100m", band!!.start <= 100f)
        assertTrue("far edge should be at or beyond 250m", band.endInclusive >= 250f)
    }

    @Test
    fun `non-positive inputs yield no band`() {
        assertNull(computeMpbrBand(zeroDistanceM = 0f, velocityMps = 61f, hobCm = 4f, targetRadiusCm = 10f))
        assertNull(computeMpbrBand(zeroDistanceM = 5f, velocityMps = 0f, hobCm = 4f, targetRadiusCm = 10f))
        assertNull(computeMpbrBand(zeroDistanceM = 5f, velocityMps = 61f, hobCm = 4f, targetRadiusCm = 0f))
        assertNull(computeMpbrBand(zeroDistanceM = 5f, velocityMps = 61f, hobCm = -1f, targetRadiusCm = 10f))
    }
}
