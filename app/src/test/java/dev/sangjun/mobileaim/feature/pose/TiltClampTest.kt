package dev.sangjun.mobileaim.feature.pose

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises [RotationMath.clampRelativeToReference] the way
 * [OpenCvPoseEstimationService] uses it: as a final safety-net cap on a resolved pose's tilt.
 */
class TiltClampTest {

    // The rig's "facing dead-on" reference is identity, because the ChArUco board's native
    // chessboard-corner frame already shares the camera's own axes — see MarkerConfig's class doc
    // for the on-device derivation. Same value as OpenCvPoseEstimationService's private constant.
    private val canonical = doubleArrayOf(0.0, 0.0, 0.0)

    @Test
    fun `leaves a pose within the operational tilt untouched`() {
        val withinLimit = doubleArrayOf(Math.toRadians(10.0), Math.toRadians(5.0), 0.0)
        val clamped = RotationMath.clampRelativeToReference(withinLimit, canonical, 20.0)
        assertEquals(withinLimit[0], clamped[0], 1e-9)
        assertEquals(withinLimit[1], clamped[1], 1e-9)
        assertEquals(withinLimit[2], clamped[2], 1e-9)
    }

    @Test
    fun `caps an oversized tilt at exactly the clamp limit`() {
        val oversized = doubleArrayOf(Math.toRadians(35.0), 0.0, 0.0)
        val clamped = RotationMath.clampRelativeToReference(oversized, canonical, 20.0)
        assertEquals(20.0, RotationMath.angleBetweenDegrees(canonical, clamped), 1e-6)
    }

    @Test
    fun `preserves tilt axis direction when clamping`() {
        val oversized = doubleArrayOf(Math.toRadians(20.0), Math.toRadians(20.0), Math.toRadians(-10.0))
        val clamped = RotationMath.clampRelativeToReference(oversized, canonical, 15.0)

        val relativeOriginal = RotationMath.relativeRvec(canonical, oversized)
        val relativeClamped = RotationMath.relativeRvec(canonical, clamped)
        val originalAngle = RotationMath.angleDegrees(relativeOriginal)
        val clampedAngle = RotationMath.angleDegrees(relativeClamped)

        assertEquals(15.0, clampedAngle, 1e-6)
        for (i in 0..2) {
            assertEquals(relativeOriginal[i] / originalAngle, relativeClamped[i] / clampedAngle, 1e-9)
        }
    }

    @Test
    fun `leaves the exact clamp boundary untouched`() {
        val exactlyAtLimit = doubleArrayOf(Math.toRadians(20.0), 0.0, 0.0)
        val clamped = RotationMath.clampRelativeToReference(exactlyAtLimit, canonical, 20.0)
        assertEquals(20.0, RotationMath.angleBetweenDegrees(canonical, clamped), 1e-6)
    }

    @Test
    fun `clamping relative to a non-identity reference still works`() {
        val reference = doubleArrayOf(Math.toRadians(90.0), 0.0, 0.0)
        val oversized = RotationMath.composeRvec(reference, doubleArrayOf(Math.toRadians(35.0), 0.0, 0.0))
        val clamped = RotationMath.clampRelativeToReference(oversized, reference, 20.0)
        assertEquals(20.0, RotationMath.angleBetweenDegrees(reference, clamped), 1e-6)
    }
}
