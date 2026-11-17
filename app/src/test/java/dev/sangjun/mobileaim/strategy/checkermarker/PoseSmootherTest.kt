package dev.sangjun.mobileaim.strategy.checkermarker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseSmootherTest {

    @Test
    fun `first sample passes through unchanged`() {
        val smoother = PoseSmoother(alpha = 0.15f)
        val rvec = doubleArrayOf(0.3, -0.2, 0.1)
        val result = smoother.smooth(PoseEstimate(rvec, doubleArrayOf(10.0, 20.0, 300.0)))

        assertEquals(0.0, RotationMath.angleBetweenDegrees(rvec, result.rvec), 1e-6)
        assertArrayEquals(doubleArrayOf(10.0, 20.0, 300.0), result.tvec, 1e-9)
    }

    @Test
    fun `translation blends toward raw by alpha`() {
        val smoother = PoseSmoother(alpha = 0.2f)
        val rvec = doubleArrayOf(0.1, 0.0, 0.0)
        smoother.smooth(PoseEstimate(rvec, doubleArrayOf(0.0, 0.0, 0.0)))
        val result = smoother.smooth(PoseEstimate(rvec, doubleArrayOf(100.0, 0.0, 0.0)))

        assertArrayEquals(doubleArrayOf(20.0, 0.0, 0.0), result.tvec, 1e-6)
    }

    @Test
    fun `reset drops history so next sample passes through unchanged`() {
        val smoother = PoseSmoother(alpha = 0.15f)
        smoother.smooth(PoseEstimate(doubleArrayOf(0.1, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0)))
        smoother.smooth(PoseEstimate(doubleArrayOf(0.2, 0.0, 0.0), doubleArrayOf(50.0, 0.0, 0.0)))
        smoother.reset()

        val rvec = doubleArrayOf(0.0, 0.5, 0.0)
        val result = smoother.smooth(PoseEstimate(rvec, doubleArrayOf(5.0, 5.0, 5.0)))
        assertEquals(0.0, RotationMath.angleBetweenDegrees(rvec, result.rvec), 1e-6)
        assertArrayEquals(doubleArrayOf(5.0, 5.0, 5.0), result.tvec, 1e-9)
    }

    /**
     * The regression that mattered: a near-frontal marker sits at a rotation angle of ~pi, where
     * `pi about n` and `pi about -n` are the same rotation. A motionless marker can therefore report
     * alternating antipodal Rodrigues vectors. Component-wise averaging collapses those toward the
     * identity rotation; the smoothed pose must instead stay put.
     */
    @Test
    fun `alternating antipodal rvecs at pi do not collapse toward identity`() {
        val smoother = PoseSmoother(alpha = 0.15f)
        val a = doubleArrayOf(Math.PI, 0.0, 0.0)
        val b = doubleArrayOf(-Math.PI, 0.0, 0.0) // same rotation, opposite representation

        var result = smoother.smooth(PoseEstimate(a, doubleArrayOf(0.0, 0.0, 300.0)))
        repeat(30) { i ->
            result = smoother.smooth(
                PoseEstimate(if (i % 2 == 0) b else a, doubleArrayOf(0.0, 0.0, 300.0)),
            )
        }

        // Must still represent the original 180-degree rotation, not drift toward identity.
        assertEquals(
            "smoothed rotation drifted away from the true pose",
            0.0,
            RotationMath.angleBetweenDegrees(a, result.rvec),
            1e-6,
        )
        assertTrue(
            "smoothed rotation collapsed toward identity (angle ${result.rvec.joinToString()})",
            RotationMath.angleBetweenDegrees(doubleArrayOf(0.0, 0.0, 0.0), result.rvec) > 179.0,
        )
    }

    @Test
    fun `converges toward a new orientation over repeated samples`() {
        val smoother = PoseSmoother(alpha = 0.3f)
        val start = doubleArrayOf(0.0, 0.0, 0.0)
        val target = doubleArrayOf(0.0, 0.6, 0.0)

        smoother.smooth(PoseEstimate(start, doubleArrayOf(0.0, 0.0, 0.0)))
        var result = PoseEstimate(start, doubleArrayOf(0.0, 0.0, 0.0))
        repeat(80) { result = smoother.smooth(PoseEstimate(target, doubleArrayOf(0.0, 0.0, 0.0))) }

        assertEquals(0.0, RotationMath.angleBetweenDegrees(target, result.rvec), 1e-3)
    }

    @Test
    fun `current is null before the first sample`() {
        val smoother = PoseSmoother(alpha = 0.15f)
        assertEquals(null, smoother.current)
    }

    @Test
    fun `current is null after reset`() {
        val smoother = PoseSmoother(alpha = 0.15f)
        smoother.smooth(PoseEstimate(doubleArrayOf(0.1, 0.0, 0.0), doubleArrayOf(1.0, 2.0, 3.0)))
        smoother.reset()
        assertEquals(null, smoother.current)
    }

    @Test
    fun `current matches the last smoothed value without consuming a new sample`() {
        val smoother = PoseSmoother(alpha = 0.2f)
        val first = smoother.smooth(PoseEstimate(doubleArrayOf(0.1, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0)))
        val second = smoother.smooth(PoseEstimate(doubleArrayOf(0.4, 0.0, 0.0), doubleArrayOf(100.0, 0.0, 0.0)))

        val readBack = smoother.current
        requireNotNull(readBack)
        assertEquals(0.0, RotationMath.angleBetweenDegrees(second.rvec, readBack.rvec), 1e-9)
        assertArrayEquals(second.tvec, readBack.tvec, 1e-9)

        // Reading current again must not itself change state (no hidden mutation).
        val readAgain = smoother.current
        requireNotNull(readAgain)
        assertArrayEquals(readBack.tvec, readAgain.tvec, 1e-9)
        assertEquals(0.0, RotationMath.angleBetweenDegrees(readBack.rvec, readAgain.rvec), 1e-9)

        // Sanity: readBack really did differ from the first sample (proves it tracked the update).
        assertTrue(RotationMath.angleBetweenDegrees(first.rvec, readBack.rvec) > 0.0)
    }

    @Test
    fun `smoothing lags a step change instead of jumping`() {
        val smoother = PoseSmoother(alpha = 0.1f)
        val start = doubleArrayOf(0.0, 0.0, 0.0)
        val jumped = doubleArrayOf(0.0, 1.0, 0.0)

        smoother.smooth(PoseEstimate(start, doubleArrayOf(0.0, 0.0, 0.0)))
        val result = smoother.smooth(PoseEstimate(jumped, doubleArrayOf(0.0, 0.0, 0.0)))

        val movedBy = RotationMath.angleBetweenDegrees(start, result.rvec)
        val remaining = RotationMath.angleBetweenDegrees(jumped, result.rvec)
        assertTrue("should have moved some of the way, moved=$movedBy", movedBy > 0.0)
        assertTrue("should not have arrived in one step, remaining=$remaining", remaining > movedBy)
    }
}
