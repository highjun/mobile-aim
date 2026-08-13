package dev.sangjun.mobileaim.feature.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class RotationMathTest {

    @Test
    fun `round trips rvec through quaternion`() {
        val samples = listOf(
            doubleArrayOf(0.0, 0.0, 0.0),
            doubleArrayOf(0.1, 0.0, 0.0),
            doubleArrayOf(0.0, -0.4, 0.3),
            doubleArrayOf(1.2, 0.5, -0.9),
            doubleArrayOf(Math.PI, 0.0, 0.0),
            doubleArrayOf(0.0, Math.PI, 0.0),
        )
        for (rvec in samples) {
            val back = RotationMath.quaternionToRvec(RotationMath.rvecToQuaternion(rvec))
            assertEquals(
                "round trip changed the rotation for ${rvec.joinToString()}",
                0.0,
                RotationMath.angleBetweenDegrees(rvec, back),
                1e-6,
            )
        }
    }

    @Test
    fun `identity rotation maps to identity quaternion`() {
        val q = RotationMath.rvecToQuaternion(doubleArrayOf(0.0, 0.0, 0.0))
        assertEquals(1.0, q[0], 1e-12)
        assertEquals(0.0, q[1], 1e-12)
        assertEquals(0.0, q[2], 1e-12)
        assertEquals(0.0, q[3], 1e-12)
    }

    @Test
    fun `antipodal rvecs at pi describe the same rotation`() {
        val a = doubleArrayOf(Math.PI, 0.0, 0.0)
        val b = doubleArrayOf(-Math.PI, 0.0, 0.0)
        assertEquals(0.0, RotationMath.angleBetweenDegrees(a, b), 1e-6)
    }

    @Test
    fun `hemisphere alignment flips a quaternion pointing the other way`() {
        val reference = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        val candidate = doubleArrayOf(-0.99, 0.1, 0.0, 0.0)
        val aligned = RotationMath.alignHemisphere(reference, candidate)
        assertTrue("alignment should have flipped the sign", aligned[0] > 0.0)
    }

    @Test
    fun `hemisphere alignment leaves an already aligned quaternion untouched`() {
        val reference = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        val candidate = doubleArrayOf(0.99, 0.1, 0.0, 0.0)
        val aligned = RotationMath.alignHemisphere(reference, candidate)
        assertEquals(candidate[0], aligned[0], 1e-12)
    }

    @Test
    fun `nlerp between antipodal representations stays at the shared rotation`() {
        // Both describe a 180-degree rotation about X; blending must not travel toward identity.
        val a = RotationMath.normalize(RotationMath.rvecToQuaternion(doubleArrayOf(Math.PI, 0.0, 0.0)))
        val b = RotationMath.normalize(RotationMath.rvecToQuaternion(doubleArrayOf(-Math.PI, 0.0, 0.0)))
        val mid = RotationMath.nlerp(a, b, 0.5)
        val rvec = RotationMath.quaternionToRvec(mid)
        assertEquals(
            180.0,
            RotationMath.angleBetweenDegrees(doubleArrayOf(0.0, 0.0, 0.0), rvec),
            1e-6,
        )
    }

    @Test
    fun `nlerp returns a unit quaternion`() {
        val a = RotationMath.normalize(RotationMath.rvecToQuaternion(doubleArrayOf(0.2, 0.1, 0.0)))
        val b = RotationMath.normalize(RotationMath.rvecToQuaternion(doubleArrayOf(-0.3, 0.7, 0.2)))
        val mid = RotationMath.nlerp(a, b, 0.35)
        val norm = sqrt(mid[0] * mid[0] + mid[1] * mid[1] + mid[2] * mid[2] + mid[3] * mid[3])
        assertEquals(1.0, norm, 1e-12)
    }

    @Test
    fun `angleBetweenDegrees measures a known separation`() {
        val a = doubleArrayOf(0.0, 0.0, 0.0)
        val b = doubleArrayOf(0.0, 0.0, Math.toRadians(30.0))
        assertEquals(30.0, RotationMath.angleBetweenDegrees(a, b), 1e-6)
    }

    @Test
    fun `angleDegrees reads the magnitude a rvec encodes`() {
        assertEquals(0.0, RotationMath.angleDegrees(doubleArrayOf(0.0, 0.0, 0.0)), 1e-9)
        assertEquals(
            90.0,
            RotationMath.angleDegrees(doubleArrayOf(Math.toRadians(90.0), 0.0, 0.0)),
            1e-9,
        )
    }

    @Test
    fun `rotateVectorByRvec rotates a known vector about the x axis`() {
        val quarterTurn = doubleArrayOf(Math.toRadians(90.0), 0.0, 0.0)
        val rotated = RotationMath.rotateVectorByRvec(quarterTurn, doubleArrayOf(0.0, 1.0, 0.0))
        assertEquals(0.0, rotated[0], 1e-9)
        assertEquals(0.0, rotated[1], 1e-9)
        assertEquals(1.0, rotated[2], 1e-9)
    }

    @Test
    fun `rotateVectorByRvec of a half turn flips the vector`() {
        val halfTurn = doubleArrayOf(Math.PI, 0.0, 0.0)
        val rotated = RotationMath.rotateVectorByRvec(halfTurn, doubleArrayOf(0.0, 1.0, 0.0))
        assertEquals(0.0, rotated[0], 1e-9)
        assertEquals(-1.0, rotated[1], 1e-9)
        assertEquals(0.0, rotated[2], 1e-9)
    }

    @Test
    fun `rotateVectorByRvec leaves a vector on the rotation axis untouched`() {
        val someRotation = doubleArrayOf(0.3, -0.2, 0.6)
        val onAxis = RotationMath.rotateVectorByRvec(someRotation, someRotation.copyOf())
        for (i in 0..2) {
            assertEquals(someRotation[i], onAxis[i], 1e-9)
        }
    }

    @Test
    fun `clampAngleDegrees leaves a rotation within the cap untouched`() {
        val small = doubleArrayOf(Math.toRadians(5.0), 0.0, 0.0)
        val clamped = RotationMath.clampAngleDegrees(small, 20.0)
        assertEquals(small[0], clamped[0], 1e-12)
        assertEquals(small[1], clamped[1], 1e-12)
        assertEquals(small[2], clamped[2], 1e-12)
    }

    @Test
    fun `clampAngleDegrees caps an oversized rotation at exactly the limit, keeping its axis`() {
        val big = doubleArrayOf(Math.toRadians(80.0), 0.0, 0.0)
        val clamped = RotationMath.clampAngleDegrees(big, 20.0)
        assertEquals(20.0, RotationMath.angleDegrees(clamped), 1e-9)
        assertEquals(0.0, clamped[1], 1e-12)
        assertEquals(0.0, clamped[2], 1e-12)
    }

    @Test
    fun `clampAngleDegrees on an off-axis rotation preserves direction`() {
        val big = doubleArrayOf(Math.toRadians(50.0), Math.toRadians(30.0), Math.toRadians(-10.0))
        val clamped = RotationMath.clampAngleDegrees(big, 20.0)
        assertEquals(20.0, RotationMath.angleDegrees(clamped), 1e-9)
        val originalAngle = RotationMath.angleDegrees(big)
        for (i in 0..2) {
            assertEquals(big[i] / originalAngle, clamped[i] / 20.0, 1e-9)
        }
    }

    @Test
    fun `clampAngleDegrees leaves the identity rotation untouched`() {
        val identity = doubleArrayOf(0.0, 0.0, 0.0)
        val clamped = RotationMath.clampAngleDegrees(identity, 20.0)
        assertEquals(0.0, clamped[0], 1e-12)
        assertEquals(0.0, clamped[1], 1e-12)
        assertEquals(0.0, clamped[2], 1e-12)
    }

    @Test
    fun `relativeRvec and composeRvec are inverses of each other`() {
        val from = doubleArrayOf(Math.toRadians(10.0), Math.toRadians(-5.0), 0.0)
        val to = doubleArrayOf(Math.toRadians(20.0), Math.toRadians(15.0), Math.toRadians(8.0))
        val delta = RotationMath.relativeRvec(from, to)
        val recomposed = RotationMath.composeRvec(from, delta)
        assertEquals(0.0, RotationMath.angleBetweenDegrees(to, recomposed), 1e-6)
    }

    @Test
    fun `relativeRvec of a rotation against itself is the identity`() {
        val rvec = doubleArrayOf(Math.toRadians(12.0), Math.toRadians(-7.0), Math.toRadians(4.0))
        val delta = RotationMath.relativeRvec(rvec, rvec)
        assertEquals(0.0, RotationMath.angleDegrees(delta), 1e-6)
    }

    @Test
    fun `relativeRvec measures a known angular separation`() {
        val from = doubleArrayOf(0.0, 0.0, 0.0)
        val to = doubleArrayOf(Math.toRadians(15.0), 0.0, 0.0)
        val delta = RotationMath.relativeRvec(from, to)
        assertEquals(15.0, RotationMath.angleDegrees(delta), 1e-6)
    }
}
