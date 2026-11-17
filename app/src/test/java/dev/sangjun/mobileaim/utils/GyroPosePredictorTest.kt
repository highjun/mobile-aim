package dev.sangjun.mobileaim.utils

import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.strategy.checkermarker.RotationMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GyroPosePredictorTest {

    private val identityQuaternion = doubleArrayOf(1.0, 0.0, 0.0, 0.0)

    @Test
    fun `no device rotation change leaves the pose exactly unchanged`() {
        val pose = PoseEstimate(doubleArrayOf(0.1, -0.2, 0.05), doubleArrayOf(10.0, 20.0, 300.0))
        val deviceQuaternion = RotationMath.rvecToQuaternion(doubleArrayOf(0.4, 0.1, -0.3))

        val predicted = GyroPosePredictor.predict(pose, deviceQuaternion, deviceQuaternion)

        assertEquals(0.0, RotationMath.angleBetweenDegrees(pose.rvec, predicted.rvec), 1e-9)
        assertArrayEquals(pose.tvec, predicted.tvec, 1e-9)
    }

    @Test
    fun `translation is carried through unchanged regardless of rotation`() {
        val pose = PoseEstimate(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(5.0, -8.0, 350.0))
        val newDeviceQuaternion = RotationMath.rvecToQuaternion(doubleArrayOf(Math.toRadians(20.0), 0.0, 0.0))

        val predicted = GyroPosePredictor.predict(pose, identityQuaternion, newDeviceQuaternion)

        assertArrayEquals(pose.tvec, predicted.tvec, 1e-9)
    }

    @Test
    fun `predicting forward then back with old and new swapped returns to the original pose`() {
        val pose = PoseEstimate(doubleArrayOf(0.2, -0.1, 0.15), doubleArrayOf(0.0, 0.0, 300.0))
        val qOld = RotationMath.rvecToQuaternion(doubleArrayOf(Math.toRadians(-8.0), Math.toRadians(3.0), 0.0))
        val qNew = RotationMath.rvecToQuaternion(doubleArrayOf(Math.toRadians(12.0), Math.toRadians(-6.0), Math.toRadians(4.0)))

        val predictedForward = GyroPosePredictor.predict(pose, qOld, qNew)
        val predictedBack = GyroPosePredictor.predict(predictedForward, qNew, qOld)

        assertEquals(0.0, RotationMath.angleBetweenDegrees(pose.rvec, predictedBack.rvec), 1e-6)
    }

    @Test
    fun `predicted pose diverges from the original by exactly the device's own angular change`() {
        val pose = PoseEstimate(doubleArrayOf(0.05, 0.02, -0.01), doubleArrayOf(0.0, 0.0, 300.0))
        val deviceDeltaRvec = doubleArrayOf(Math.toRadians(10.0), Math.toRadians(-5.0), Math.toRadians(2.0))
        val qOld = identityQuaternion
        val qNew = RotationMath.rvecToQuaternion(deviceDeltaRvec)

        val predicted = GyroPosePredictor.predict(pose, qOld, qNew)

        val expectedAngle = RotationMath.angleDegrees(deviceDeltaRvec)
        val actualAngle = RotationMath.angleBetweenDegrees(pose.rvec, predicted.rvec)
        assertEquals(expectedAngle, actualAngle, 1e-6)
    }
}
