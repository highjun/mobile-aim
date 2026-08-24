package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.camera.CameraFov
import kotlin.math.atan
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BallisticDropTest {

    private val fov = CameraFov(focalLengthYPx = 1000f, referenceHeightPx = 1000)

    @Test
    fun `matches hand-computed drop at 5m and 60 m per s`() {
        val distance = 5f
        val velocity = 60f
        val timeOfFlight = distance / velocity
        val dropMeters = 0.5 * 9.8 * timeOfFlight * timeOfFlight
        val dropAngle = atan(dropMeters / distance)
        val expectedFraction = (fov.focalLengthYPx * tan(dropAngle) / fov.referenceHeightPx).toFloat()

        assertEquals(expectedFraction, computeDropOffsetFraction(distance, velocity, fov), 1e-6f)
    }

    @Test
    fun `offset shrinks as velocity increases`() {
        val slow = computeDropOffsetFraction(distanceMeters = 5f, velocityMps = 60f, fov = fov)
        val fast = computeDropOffsetFraction(distanceMeters = 5f, velocityMps = 300f, fov = fov)

        assertTrue("faster projectile should need less compensation", fast < slow)
    }

    @Test
    fun `offset grows with distance for a fixed slow velocity`() {
        val near = computeDropOffsetFraction(distanceMeters = 5f, velocityMps = 60f, fov = fov)
        val far = computeDropOffsetFraction(distanceMeters = 10f, velocityMps = 60f, fov = fov)

        assertTrue("more time in flight should mean more drop", far > near)
    }

    @Test
    fun `firearm-like velocity is close to negligible at 5m`() {
        // ~300 m/s, roughly a slow pistol round — real firearms should compute to a tiny offset.
        val fraction = computeDropOffsetFraction(distanceMeters = 5f, velocityMps = 300f, fov = fov)

        assertTrue("expected a near-zero offset for a fast projectile", fraction < 0.001f)
    }

    @Test
    fun `non-positive distance or velocity yields no offset`() {
        assertEquals(0f, computeDropOffsetFraction(distanceMeters = 0f, velocityMps = 60f, fov = fov), 0f)
        assertEquals(0f, computeDropOffsetFraction(distanceMeters = 5f, velocityMps = 0f, fov = fov), 0f)
        assertEquals(0f, computeDropOffsetFraction(distanceMeters = -1f, velocityMps = 60f, fov = fov), 0f)
    }
}
