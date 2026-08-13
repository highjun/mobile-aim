package dev.sangjun.mobileaim.feature.pose

import kotlin.math.sqrt

/**
 * Picks the physically correct member of a set of solvePnP candidate poses using an absolute
 * "which way is up" reference (the phone's gravity sensor), instead of relying on reprojection
 * error alone — which cannot tell the true pose apart from its up/down-flipped twin on a
 * near-fronto-parallel planar target (see [RotationMath] for why Rodrigues vectors alone don't
 * resolve this either).
 *
 * The marker is rigidly mounted to the same rig as the phone and normally held upright, so the
 * board's local -Y axis (see [dev.sangjun.mobileaim.config.MarkerConfig] —
 * `org.opencv.objdetect.CharucoBoard`'s native chessboard-corner frame has +Y running *down* the
 * printed board, confirmed on-device rather than assumed) should point roughly away from the
 * ground in the real world. A candidate whose rotation implies the opposite is the flipped twin,
 * not the true pose.
 *
 * [gravityCamera] must already be expressed in the camera's optical frame (X right, Y down, Z
 * forward through the lens) and point "up" (away from the ground) — the same direction Android's
 * TYPE_GRAVITY sensor reports in the phone's own frame, just rotated into the camera frame. That
 * rotation is the caller's responsibility (see feature/motion).
 */
internal object GravityPoseDisambiguator {

    /** The board's local "up" direction: its -Y axis (native chessboard-corner frame is Y-down). */
    private val MARKER_UP = doubleArrayOf(0.0, -1.0, 0.0)

    /**
     * Returns the element of [candidates] whose implied marker "up" direction is most aligned
     * with [gravityCamera], or null if [candidates] is empty.
     */
    fun select(candidates: List<PoseEstimate>, gravityCamera: DoubleArray): PoseEstimate? =
        candidates.maxByOrNull { alignmentScore(it, gravityCamera) }

    /**
     * Cosine similarity between [candidate]'s implied marker "up" direction (its local +Y axis
     * rotated into camera frame) and [gravityCamera]: 1.0 means the candidate is upright, -1.0
     * means it is upside down. 0.0 if either vector is degenerate (zero length).
     */
    fun alignmentScore(candidate: PoseEstimate, gravityCamera: DoubleArray): Double {
        val markerUpInCamera = RotationMath.rotateVectorByRvec(candidate.rvec, MARKER_UP)
        return cosineSimilarity(markerUpInCamera, gravityCamera)
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val normA = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val normB = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        if (normA < EPSILON || normB < EPSILON) return 0.0
        return dot / (normA * normB)
    }

    private const val EPSILON = 1e-12
}
