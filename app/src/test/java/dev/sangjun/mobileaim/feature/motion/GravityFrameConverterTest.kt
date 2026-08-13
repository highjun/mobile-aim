package dev.sangjun.mobileaim.feature.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class GravityFrameConverterTest {

    @Test
    fun `phone flat on its back, screen up, at rotation 0 - gravity reads toward the sky in device frame`() {
        // TYPE_GRAVITY on a phone lying screen-up on a table reads (0,0,+9.8): the sensor "points"
        // out of the screen, away from the ground.
        val deviceFrame = doubleArrayOf(0.0, 0.0, 9.8)
        val camera = GravityFrameConverter.toCameraFrame(deviceFrame, rotationDegrees = 0)
        // The back camera on the underside is pointed at the ground here, so "up" (away from the
        // ground) is behind it: away from the camera, i.e. -Z in camera frame.
        assertEquals(0.0, camera[0], 1e-9)
        assertEquals(0.0, camera[1], 1e-9)
        assertEquals(-9.8, camera[2], 1e-9)
    }

    @Test
    fun `held upright in natural orientation, rotation 0 - device up maps to camera up`() {
        val deviceFrame = doubleArrayOf(0.0, 9.8, 0.0) // device Y-up = "up" when held naturally.
        val camera = GravityFrameConverter.toCameraFrame(deviceFrame, rotationDegrees = 0)
        // Camera frame is Y-down, so "up" is -Y.
        assertEquals(0.0, camera[0], 1e-9)
        assertEquals(-9.8, camera[1], 1e-9)
        assertEquals(0.0, camera[2], 1e-9)
    }

    @Test
    fun `in-plane rotation by 90 degrees rotates x,y and leaves z untouched`() {
        val deviceFrame = doubleArrayOf(1.0, 2.0, 3.0)
        val camera = GravityFrameConverter.toCameraFrame(deviceFrame, rotationDegrees = 90)
        // Step 1 first: (1, -2, -3). Then 90-degree in-plane rotation (x,y) -> (-y, x): (2, 1).
        assertEquals(2.0, camera[0], 1e-9)
        assertEquals(1.0, camera[1], 1e-9)
        assertEquals(-3.0, camera[2], 1e-9)
    }

    @Test
    fun `rotationDegrees 0, 90, 180, 270 preserve the vector's magnitude`() {
        val deviceFrame = doubleArrayOf(0.3, -0.7, 0.5)
        val originalMagnitude = kotlin.math.sqrt(
            deviceFrame[0] * deviceFrame[0] + deviceFrame[1] * deviceFrame[1] + deviceFrame[2] * deviceFrame[2],
        )
        for (rotation in listOf(0, 90, 180, 270)) {
            val camera = GravityFrameConverter.toCameraFrame(deviceFrame, rotation)
            val magnitude = kotlin.math.sqrt(camera[0] * camera[0] + camera[1] * camera[1] + camera[2] * camera[2])
            assertEquals("rotation=$rotation", originalMagnitude, magnitude, 1e-9)
        }
    }

    @Test
    fun `four successive 90 degree rotations return to the same in-plane vector`() {
        val deviceFrame = doubleArrayOf(1.0, 0.0, 0.0)
        val r90 = GravityFrameConverter.toCameraFrame(deviceFrame, 90)
        val r180 = GravityFrameConverter.toCameraFrame(deviceFrame, 180)
        val r270 = GravityFrameConverter.toCameraFrame(deviceFrame, 270)
        val r360 = GravityFrameConverter.toCameraFrame(deviceFrame, 360)
        val r0 = GravityFrameConverter.toCameraFrame(deviceFrame, 0)
        assertEquals(r0[0], r360[0], 1e-9)
        assertEquals(r0[1], r360[1], 1e-9)
        // Sanity: applying the 90-degree formula twice should match the 180-degree formula.
        assertEquals(r180[0], -r90[1], 1e-9)
        assertEquals(r180[1], r90[0], 1e-9)
        // And thrice should match 270.
        assertEquals(r270[0], -r180[1], 1e-9)
        assertEquals(r270[1], r180[0], 1e-9)
    }
}
