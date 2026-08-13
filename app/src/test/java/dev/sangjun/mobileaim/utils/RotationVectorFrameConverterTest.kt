package dev.sangjun.mobileaim.utils

import dev.sangjun.mobileaim.strategy.checkermarker.RotationMath
import org.junit.Assert.assertEquals
import org.junit.Test

class RotationVectorFrameConverterTest {

    @Test
    fun `identity device rotation stays identity in camera frame, for every rotationDegrees`() {
        val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        for (rotationDegrees in listOf(0, 90, 180, 270)) {
            val camera = RotationVectorFrameConverter.toCameraFrame(identity, rotationDegrees)
            val cameraRvec = RotationMath.quaternionToRvec(camera)
            assertEquals(
                "rotationDegrees=$rotationDegrees",
                0.0,
                RotationMath.angleDegrees(cameraRvec),
                1e-9,
            )
        }
    }

    @Test
    fun `conjugation preserves rotation angle for an arbitrary device rotation`() {
        // A representative "the device tilted a bit" rotation.
        val deviceRvec = doubleArrayOf(
            Math.toRadians(12.0),
            Math.toRadians(-7.0),
            Math.toRadians(4.0),
        )
        val deviceQuaternion = RotationMath.rvecToQuaternion(deviceRvec)
        val originalAngle = RotationMath.angleDegrees(deviceRvec)

        for (rotationDegrees in listOf(0, 90, 180, 270)) {
            val camera = RotationVectorFrameConverter.toCameraFrame(deviceQuaternion, rotationDegrees)
            val cameraAngle = RotationMath.angleDegrees(RotationMath.quaternionToRvec(camera))
            assertEquals("rotationDegrees=$rotationDegrees", originalAngle, cameraAngle, 1e-6)
        }
    }

    @Test
    fun `a device rotation about the flip's own axis is unchanged at rotationDegrees 0`() {
        // The flip step is a 180-degree rotation about the device's local X axis. A device
        // rotation about that same axis commutes with the flip, so conjugating it leaves both the
        // angle *and* the axis unchanged when there is no additional in-plane rotation.
        val deviceRvec = doubleArrayOf(Math.toRadians(90.0), 0.0, 0.0)
        val deviceQuaternion = RotationMath.rvecToQuaternion(deviceRvec)

        val camera = RotationVectorFrameConverter.toCameraFrame(deviceQuaternion, rotationDegrees = 0)
        val cameraRvec = RotationMath.quaternionToRvec(camera)

        assertEquals(0.0, RotationMath.angleBetweenDegrees(deviceRvec, cameraRvec), 1e-6)
    }

    @Test
    fun `output is always a unit quaternion`() {
        val deviceQuaternion = RotationMath.normalize(
            RotationMath.rvecToQuaternion(doubleArrayOf(0.3, -0.5, 0.2)),
        )
        for (rotationDegrees in listOf(0, 90, 180, 270, 360, -90)) {
            val camera = RotationVectorFrameConverter.toCameraFrame(deviceQuaternion, rotationDegrees)
            val norm = Math.sqrt(camera[0] * camera[0] + camera[1] * camera[1] + camera[2] * camera[2] + camera[3] * camera[3])
            assertEquals("rotationDegrees=$rotationDegrees", 1.0, norm, 1e-9)
        }
    }
}
