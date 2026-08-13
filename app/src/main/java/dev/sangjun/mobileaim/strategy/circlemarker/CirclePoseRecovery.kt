package dev.sangjun.mobileaim.strategy.circlemarker

import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.OpenCvPoseEstimationService
import kotlin.math.abs
import kotlin.math.sqrt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Recovers a circle's 3D position + plane normal from its detected image ellipse.
 *
 * Both the normal and position are derived here from first principles (the cone-of-rays equation
 * a circle's conic represents), not ported from any fiducial-marker library's source. An initial
 * attempt at the normal followed a formula transcribed from a secondary citation (Ulrich et al.,
 * "Real Time Fiducial Marker Localisation System with Full 6 DOF Pose Estimation", 2023, eq. 2,
 * itself citing Forsyth 1991 / Safaee-Rad et al. 1992) — that transcription turned out to be
 * wrong (see git history), caught by testing an off-axis synthetic circle; [candidatesFrom]'s
 * `termA*q0 ± termB*q2` combination is instead verified by hand against two independent synthetic
 * cases (on-axis-tilted and off-axis-untilted circles).
 *
 * The position formula is **not** the `x = s3(r/√(−λ0λ2))·n` form some presentations of this
 * result use — that expression is only correct when the circle's centre happens to lie exactly
 * along its own normal's ray from the camera (verified analytically and by synthetic test: it
 * collapses badly for an off-axis or off-normal-line circle, e.g. a marker that's tilted but
 * still sits near the middle of frame). [recoverPosition] instead solves the plane-intersection
 * equations directly from the cone equation the conic itself represents
 * (`Q = h²I − h(NCᵀ+CNᵀ) + (|C|²−r²)NNᵀ`, for `C = hN + C_perp`) — a from-scratch derivation.
 *
 * A circle alone only constrains 5 of 6 pose DOF (position + plane normal); rotation about the
 * normal is unobservable and left to the caller. The decomposition is two-fold ambiguous — the
 * same ellipse is consistent with two mirrored circle planes — so [recover] returns every
 * physically-plausible candidate (front-of-camera, facing the camera) for the caller to
 * disambiguate, the same shape as
 * [dev.sangjun.mobileaim.strategy.checkermarker.OpenCvPoseEstimationService]'s candidate list.
 */
object CirclePoseRecovery {
    /**
     * [conic] is the ellipse's pixel-space conic matrix (see [ConicMath.ellipseToConic]).
     * [radiusMm] is the marker circle's real physical radius.
     */
    fun recover(conic: Array<DoubleArray>, intrinsics: CameraIntrinsics, radiusMm: Double): List<CirclePoseCandidate> {
        val q = normalizeByIntrinsics(conic, intrinsics)
        val qMat = Mat(3, 3, CvType.CV_64F).apply { for (i in 0..2) put(i, 0, *q[i]) }
        val eigenvalues = Mat()
        val eigenvectors = Mat()
        return try {
            if (!Core.eigen(qMat, eigenvalues, eigenvectors)) return emptyList()
            // OpenCV's Core.eigen documents descending order for a symmetric input, with each row
            // of `eigenvectors` the eigenvector for the eigenvalue at the same row of `eigenvalues`.
            val lambda = DoubleArray(3) { eigenvalues.get(it, 0)[0] }
            val vectors = Array(3) { row -> DoubleArray(3) { col -> eigenvectors.get(row, col)[0] } }
            candidatesFrom(lambda, vectors, q, radiusMm)
        } finally {
            qMat.release()
            eigenvalues.release()
            eigenvectors.release()
        }
    }

    /** `Q = Kᵀ C K` — removes the camera's intrinsics so the conic is expressed in normalized ray coordinates. */
    private fun normalizeByIntrinsics(conic: Array<DoubleArray>, intrinsics: CameraIntrinsics): Array<DoubleArray> {
        val k = arrayOf(
            doubleArrayOf(intrinsics.fx, 0.0, intrinsics.cx),
            doubleArrayOf(0.0, intrinsics.fy, intrinsics.cy),
            doubleArrayOf(0.0, 0.0, 1.0),
        )
        return multiply(multiply(transpose(k), conic), k)
    }

    private fun candidatesFrom(
        lambda: DoubleArray,
        vectors: Array<DoubleArray>,
        q: Array<DoubleArray>,
        radiusMm: Double,
    ): List<CirclePoseCandidate> {
        val l0 = lambda[0]
        val l1 = lambda[1]
        val l2 = lambda[2]
        // A real ellipse's normalized conic has this eigenvalue signature (two positive, one
        // negative); anything else means a degenerate fit, not a genuine ambiguity to resolve.
        if (!(l0 >= l1 && l1 > 0.0 && l2 < 0.0)) return emptyList()

        val q0 = vectors[0]
        val q2 = vectors[2]
        // Dimensionless weights (eigenvalue *ratios*, not the eigenvalues themselves — Q's own
        // absolute scale is arbitrary, and multiplying by raw eigenvalues here was an earlier,
        // hand-verified-wrong attempt: it worked by coincidence on an on-axis test case and badly
        // miscombined q0/q2 off-axis). Verified by hand against two independent synthetic circles
        // (on-axis-tilted and off-axis-untilted) that `termA*q0 ± termB*q2` (sign enumerated below)
        // reproduces the true normal exactly; not derived from any external source.
        val termA = sqrt((l0 - l1) / (l0 - l2))
        val termB = sqrt((l1 - l2) / (l0 - l2))

        val candidates = mutableListOf<CirclePoseCandidate>()
        for (s1 in SIGNS) {
            for (s2 in SIGNS) {
                val rawNormal = DoubleArray(3) { i -> s1 * q0[i] * termA + s2 * q2[i] * termB }
                val normal = normalizeVector(rawNormal)
                // The printed marker's visible face must turn back toward the camera, not away.
                if (normal[2] >= 0.0) continue
                val position = recoverPosition(normal, q, radiusMm) ?: continue
                candidates += CirclePoseCandidate(positionMm = position, normal = normal)
            }
        }
        return candidates
    }

    /**
     * Solves for the circle centre given its (already known) plane normal, by decomposing
     * `C = hN + C_perp` (depth along the normal, plus an in-plane offset) directly from the cone
     * equation `Q = k[h²I − h(NCᵀ+CNᵀ) + (|C|²−r²)NNᵀ]` (k an unknown overall scale, since a
     * conic's matrix is only defined up to scale). Projecting that equation onto `N` and onto the
     * plane perpendicular to `N` gives closed-form expressions for `k`, `h`, and `C_perp` — see
     * the class doc; this is worked out from the cone equation itself, not any external source.
     */
    private fun recoverPosition(normal: DoubleArray, q: Array<DoubleArray>, radiusMm: Double): DoubleArray? {
        val qn = matVec(q, normal)
        val qNN = dot(normal, qn)
        val perpQN = DoubleArray(3) { qn[it] - qNN * normal[it] }
        val perpQNNormSq = dot(perpQN, perpQN)

        val e = perpendicularUnit(normal)
        val a = dot(e, matVec(q, e)) // = k*h^2, independent of which perpendicular e is chosen

        val k = (perpQNNormSq / a - qNN) / (radiusMm * radiusMm)
        val hSquared = a / k
        if (!hSquared.isFinite() || hSquared < 0.0) return null

        val h = sqrt(hSquared)
        for (hSign in SIGNS) {
            val signedH = hSign * h
            val cPerp = DoubleArray(3) { -perpQN[it] / (k * signedH) }
            val position = DoubleArray(3) { i -> signedH * normal[i] + cPerp[i] }
            if (position[2] > 0.0) return position
        }
        return null
    }

    private fun perpendicularUnit(n: DoubleArray): DoubleArray {
        val arbitrary = if (abs(n[2]) < 0.9) doubleArrayOf(0.0, 0.0, 1.0) else doubleArrayOf(1.0, 0.0, 0.0)
        val projection = dot(arbitrary, n)
        val perp = DoubleArray(3) { arbitrary[it] - projection * n[it] }
        return normalizeVector(perp)
    }

    private fun normalizeVector(v: DoubleArray): DoubleArray {
        val norm = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (norm > 1e-12) DoubleArray(3) { v[it] / norm } else v
    }

    private fun matVec(m: Array<DoubleArray>, v: DoubleArray): DoubleArray =
        DoubleArray(3) { i -> (0..2).sumOf { j -> m[i][j] * v[j] } }

    private fun dot(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun transpose(m: Array<DoubleArray>): Array<DoubleArray> = Array(3) { i -> DoubleArray(3) { j -> m[j][i] } }

    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { k -> a[i][k] * b[k][j] } } }

    private val SIGNS = intArrayOf(1, -1)
}
