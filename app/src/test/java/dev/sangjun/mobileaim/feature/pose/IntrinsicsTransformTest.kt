package dev.sangjun.mobileaim.feature.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntrinsicsTransformTest {

    @Test
    fun `matching aspect ratio scales uniformly with no crop`() {
        // 4032x3024 (4:3) sensor -> 640x480 (4:3) frame: pure 1/6.3 scale, nothing cropped.
        val result = IntrinsicsTransform.scaleToFrame(
            sensorFx = 3200.0, sensorFy = 3200.0,
            sensorCx = 2016.0, sensorCy = 1512.0,
            sensorWidth = 4032, sensorHeight = 3024,
            frameWidth = 640, frameHeight = 480,
        )
        val expectedScale = 640.0 / 4032.0
        assertEquals(3200.0 * expectedScale, result.fx, 1e-9)
        assertEquals(3200.0 * expectedScale, result.fy, 1e-9)
        assertEquals(320.0, result.cx, 1e-9)
        assertEquals(240.0, result.cy, 1e-9)
    }

    @Test
    fun `mismatched aspect ratio keeps fx and fy equal instead of skewing them`() {
        // 4:3 sensor -> 16:9 frame. A naive per-axis scale would make fx != fy here.
        val result = IntrinsicsTransform.scaleToFrame(
            sensorFx = 3200.0, sensorFy = 3200.0,
            sensorCx = 2016.0, sensorCy = 1512.0,
            sensorWidth = 4032, sensorHeight = 3024,
            frameWidth = 1280, frameHeight = 720,
        )
        assertEquals("fx and fy must stay equal for square pixels", result.fx, result.fy, 1e-9)
        // Centre of the sensor stays the centre of the cropped frame.
        assertEquals(640.0, result.cx, 1e-9)
        assertEquals(360.0, result.cy, 1e-9)
    }

    @Test
    fun `rotating by 90 swaps focal axes and moves principal point`() {
        val source = CameraIntrinsics(
            fx = 1000.0, fy = 900.0, cx = 320.0, cy = 200.0,
            referenceWidth = 640, referenceHeight = 480,
        )
        val rotated = IntrinsicsTransform.rotate(source, 90, 640, 480)

        assertEquals(900.0, rotated.fx, 1e-9)
        assertEquals(1000.0, rotated.fy, 1e-9)
        assertEquals(480.0 - 200.0, rotated.cx, 1e-9)
        assertEquals(320.0, rotated.cy, 1e-9)
        assertEquals(480, rotated.referenceWidth)
        assertEquals(640, rotated.referenceHeight)
    }

    @Test
    fun `rotating by 180 mirrors principal point and keeps focal axes`() {
        val source = CameraIntrinsics(
            fx = 1000.0, fy = 900.0, cx = 320.0, cy = 200.0,
            referenceWidth = 640, referenceHeight = 480,
        )
        val rotated = IntrinsicsTransform.rotate(source, 180, 640, 480)

        assertEquals(1000.0, rotated.fx, 1e-9)
        assertEquals(900.0, rotated.fy, 1e-9)
        assertEquals(320.0, rotated.cx, 1e-9)
        assertEquals(280.0, rotated.cy, 1e-9)
        assertEquals(640, rotated.referenceWidth)
        assertEquals(480, rotated.referenceHeight)
    }

    @Test
    fun `rotating by 270 swaps focal axes the other way`() {
        val source = CameraIntrinsics(
            fx = 1000.0, fy = 900.0, cx = 320.0, cy = 200.0,
            referenceWidth = 640, referenceHeight = 480,
        )
        val rotated = IntrinsicsTransform.rotate(source, 270, 640, 480)

        assertEquals(900.0, rotated.fx, 1e-9)
        assertEquals(1000.0, rotated.fy, 1e-9)
        assertEquals(200.0, rotated.cx, 1e-9)
        assertEquals(640.0 - 320.0, rotated.cy, 1e-9)
        assertEquals(480, rotated.referenceWidth)
        assertEquals(640, rotated.referenceHeight)
    }

    @Test
    fun `rotating four times by 90 returns to the original`() {
        val source = CameraIntrinsics(
            fx = 1000.0, fy = 900.0, cx = 320.0, cy = 200.0,
            referenceWidth = 640, referenceHeight = 480,
        )
        var current = source
        repeat(4) {
            current = IntrinsicsTransform.rotate(current, 90, current.referenceWidth, current.referenceHeight)
        }
        assertEquals(source.fx, current.fx, 1e-9)
        assertEquals(source.fy, current.fy, 1e-9)
        assertEquals(source.cx, current.cx, 1e-9)
        assertEquals(source.cy, current.cy, 1e-9)
        assertEquals(source.referenceWidth, current.referenceWidth)
        assertEquals(source.referenceHeight, current.referenceHeight)
    }

    @Test
    fun `rotation of zero is a no-op`() {
        val source = CameraIntrinsics(
            fx = 1000.0, fy = 900.0, cx = 320.0, cy = 200.0,
            referenceWidth = 640, referenceHeight = 480,
        )
        assertEquals(source, IntrinsicsTransform.rotate(source, 0, 640, 480))
    }

    @Test
    fun `swapsAxes is true only for quarter turns`() {
        assertFalse(IntrinsicsTransform.swapsAxes(0))
        assertTrue(IntrinsicsTransform.swapsAxes(90))
        assertFalse(IntrinsicsTransform.swapsAxes(180))
        assertTrue(IntrinsicsTransform.swapsAxes(270))
        assertFalse(IntrinsicsTransform.swapsAxes(360))
    }
}
