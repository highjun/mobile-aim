package dev.sangjun.mobileaim.feature.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rig is normally held upright, camera facing the board dead-on plus a small tilt (up to
 * ~15 degrees). Identity is "facing dead-on" here because the board's native chessboard-corner
 * frame is X-right/Y-down/Z-away-from-camera — the same axes the camera itself uses — so no
 * rotation is needed to align them (see [dev.sangjun.mobileaim.config.MarkerConfig]'s class doc
 * for the on-device derivation). In that pose the board's local -Y axis (its printed "up") lands
 * at (0, -1, 0) in camera frame, since the camera's own +Y points down the image.
 */
private val UPRIGHT_FACING_RVEC = doubleArrayOf(0.0, 0.0, 0.0)

/** Straight "up" (away from the ground) in camera frame, for a phone held with no roll or pitch. */
private val GRAVITY_STRAIGHT_UP = doubleArrayOf(0.0, -1.0, 0.0)

class GravityPoseDisambiguatorTest {

    @Test
    fun `alignmentScore is perfect for an exactly upright pose and its negation for the flip`() {
        val trueScore = GravityPoseDisambiguator.alignmentScore(
            PoseEstimate(UPRIGHT_FACING_RVEC, doubleArrayOf(0.0, 0.0, 300.0)),
            GRAVITY_STRAIGHT_UP,
        )
        assertEquals(1.0, trueScore, 1e-9)

        val flippedRvec = RotationMath.composeRvec(UPRIGHT_FACING_RVEC, doubleArrayOf(Math.PI, 0.0, 0.0))
        val flippedScore = GravityPoseDisambiguator.alignmentScore(
            PoseEstimate(flippedRvec, doubleArrayOf(0.0, 0.0, 300.0)),
            GRAVITY_STRAIGHT_UP,
        )
        assertEquals(-1.0, flippedScore, 1e-9)
    }

    @Test
    fun `select picks the upright candidate over its up-down-flipped twin when held near vertical`() {
        // A believable small tilt away from dead-on-facing: ~9 degrees combined.
        val trueRvec = RotationMath.composeRvec(
            UPRIGHT_FACING_RVEC,
            doubleArrayOf(Math.toRadians(6.0), Math.toRadians(5.0), Math.toRadians(2.0)),
        )
        val truePose = PoseEstimate(trueRvec, doubleArrayOf(5.0, 10.0, 320.0))
        // The genuine planar-pose ambiguity twin: same viewing tilt, marker's up/down (and
        // front/back) flipped — this is what solvePnPGeneric actually reports as the other root.
        val flippedRvec = RotationMath.composeRvec(trueRvec, doubleArrayOf(Math.PI, 0.0, 0.0))
        val flippedPose = PoseEstimate(flippedRvec, doubleArrayOf(5.0, 10.0, 320.0))

        val selected = GravityPoseDisambiguator.select(listOf(truePose, flippedPose), GRAVITY_STRAIGHT_UP)
        assertSame(truePose, selected)
    }

    @Test
    fun `select is order independent`() {
        val trueRvec = RotationMath.composeRvec(
            UPRIGHT_FACING_RVEC,
            doubleArrayOf(Math.toRadians(-4.0), Math.toRadians(8.0), Math.toRadians(-1.0)),
        )
        val truePose = PoseEstimate(trueRvec, doubleArrayOf(-3.0, -7.0, 280.0))
        val flippedPose = PoseEstimate(
            RotationMath.composeRvec(trueRvec, doubleArrayOf(Math.PI, 0.0, 0.0)),
            doubleArrayOf(-3.0, -7.0, 280.0),
        )

        assertSame(truePose, GravityPoseDisambiguator.select(listOf(flippedPose, truePose), GRAVITY_STRAIGHT_UP))
    }

    @Test
    fun `select still picks the true pose when the phone itself is held with a slight forward tilt`() {
        // Phone pitched forward ~12 degrees: gravity in camera frame picks up a +Z component.
        val tiltRadians = Math.toRadians(12.0)
        val gravityTilted = doubleArrayOf(0.0, -Math.cos(tiltRadians), Math.sin(tiltRadians))

        // The marker pose consistent with that same physical tilt (rig is rigid, phone and marker
        // tilt together), plus a small extra sighting tilt.
        val trueRvec = RotationMath.composeRvec(
            UPRIGHT_FACING_RVEC,
            doubleArrayOf(-tiltRadians, Math.toRadians(3.0), 0.0),
        )
        val truePose = PoseEstimate(trueRvec, doubleArrayOf(2.0, 4.0, 350.0))
        val flippedPose = PoseEstimate(
            RotationMath.composeRvec(trueRvec, doubleArrayOf(Math.PI, 0.0, 0.0)),
            doubleArrayOf(2.0, 4.0, 350.0),
        )

        val selected = GravityPoseDisambiguator.select(listOf(truePose, flippedPose), gravityTilted)
        assertSame(truePose, selected)
    }

    @Test
    fun `select passes through a single candidate untouched`() {
        val onlyPose = PoseEstimate(UPRIGHT_FACING_RVEC, doubleArrayOf(0.0, 0.0, 300.0))
        assertSame(onlyPose, GravityPoseDisambiguator.select(listOf(onlyPose), GRAVITY_STRAIGHT_UP))
    }

    @Test
    fun `select returns null for an empty candidate list`() {
        assertNull(GravityPoseDisambiguator.select(emptyList(), GRAVITY_STRAIGHT_UP))
    }

    @Test
    fun `alignmentScore is symmetric zero-crossing for a marker viewed edge-on to gravity`() {
        // Marker "up" pointing sideways relative to gravity: neither candidate is more plausible
        // than the other from gravity alone (both perpendicular), but the score must stay finite.
        val edgeOnRvec = doubleArrayOf(Math.PI / 2, 0.0, 0.0) // marker up rotates into +Z
        val score = GravityPoseDisambiguator.alignmentScore(
            PoseEstimate(edgeOnRvec, doubleArrayOf(0.0, 0.0, 300.0)),
            GRAVITY_STRAIGHT_UP,
        )
        assertEquals(0.0, score, 1e-9)
        assertTrue(score.isFinite())
    }
}
