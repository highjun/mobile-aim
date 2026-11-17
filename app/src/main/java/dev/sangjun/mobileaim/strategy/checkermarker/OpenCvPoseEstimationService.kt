package dev.sangjun.mobileaim.strategy.checkermarker

import android.util.Log
import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.Point3Mm
import kotlin.math.sqrt
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3

@Deprecated("v1에서 비전/마커 추적을 제거함 — 수동 드래그 조준은 aim/AimViewModel 참고")
class OpenCvPoseEstimationService : IPoseEstimationService {

    /** Last accepted pose, used to break ties between near-equivalent planar solutions. */
    private var previousPose: PoseEstimate? = null

    override fun estimatePose(
        objectPoints: List<Point3Mm>,
        imagePoints: List<Point2Px>,
        intrinsics: CameraIntrinsics,
        gravityCamera: DoubleArray?,
    ): PoseEstimate? {
        if (objectPoints.size != imagePoints.size || objectPoints.size < MIN_CORRESPONDENCES) return null

        val matchedObjectPoints = objectPoints.map { it.toCvPoint3() }
        val matchedImagePoints = imagePoints.map { Point(it.x.toDouble(), it.y.toDouble()) }

        val objectMat = MatOfPoint3f().apply { fromList(matchedObjectPoints) }
        val imageMat = MatOfPoint2f().apply { fromList(matchedImagePoints) }
        val cameraMatrix = intrinsics.toCameraMatrix()
        val distCoeffs = MatOfDouble(*intrinsics.distCoeffs)

        return try {
            // SQPNP only: it was accurate to ~1e-5 px on every synthetic view tested, including
            // frontal ones where SOLVEPNP_IPPE_SQUARE degenerates on a *squarely* viewed marker
            // (returns rotation vectors of magnitude ~1e8 — measured on synthetic frontal views).
            // IPPE/IPPE_SQUARE are also hard-limited to exactly 4 planar points, so they cannot
            // consume the ChArUco board's variable-count corner correspondences at all. A planar
            // point set still carries a genuine two-fold pose ambiguity; that is resolved below by
            // physical plausibility and, when available, gravity — not by the solver itself.
            val rvecs = mutableListOf<Mat>()
            val tvecs = mutableListOf<Mat>()
            val plausible = mutableListOf<Candidate>()
            val allSane = mutableListOf<Candidate>()
            try {
                val count = Calib3d.solvePnPGeneric(
                    objectMat, imageMat, cameraMatrix, distCoeffs, rvecs, tvecs, false, Calib3d.SOLVEPNP_SQPNP,
                )
                for (i in 0 until count) {
                    val rvecArr = DoubleArray(3).also { rvecs[i].get(0, 0, it) }
                    val tvecArr = DoubleArray(3).also { tvecs[i].get(0, 0, it) }
                    if (!isNumericallySane(rvecArr, tvecArr)) continue

                    val error = reprojectionError(
                        objectMat, imageMat, cameraMatrix, distCoeffs, rvecs[i], tvecs[i],
                    )
                    if (error.isNaN()) continue

                    val candidate = Candidate(PoseEstimate(rvecArr, tvecArr), error)
                    allSane += candidate

                    // The board has to be in front of the camera, facing it (front/printed side
                    // visible), and roughly where the rig is expected to put it; the mirror branch
                    // and other numerical debris violate one of those.
                    if (tvecArr[2] <= 0.0 || !facesCamera(rvecArr) || !isPlausibleDistance(tvecArr)) continue
                    plausible += candidate
                }
            } catch (error: Exception) {
                Log.w(TAG, "solvePnPGeneric failed", error)
            } finally {
                rvecs.forEach { it.release() }
                tvecs.forEach { it.release() }
            }

            val chosen = choose(plausible, gravityCamera) ?: choose(allSane, gravityCamera)
            val clamped = chosen?.let { clampTilt(it) }
            if (clamped != null) previousPose = clamped
            clamped
        } finally {
            objectMat.release()
            imageMat.release()
            cameraMatrix.release()
        }
    }

    private class Candidate(val pose: PoseEstimate, val error: Double)

    /**
     * Picks a candidate, preferring gravity-implied plausibility over a marginally lower
     * reprojection error, and temporal continuity when gravity is unavailable.
     *
     * Near-frontal views leave the two planar branches almost tied on reprojection error, so a
     * strict argmin can alternate between them frame to frame. Among candidates within
     * [ERROR_TOLERANCE_FACTOR] of the best: when a gravity reading is available, the one whose
     * implied board "up" points furthest from the ground wins outright — that is a real physical
     * answer, not a heuristic, so it takes priority. Otherwise we fall back to the pose closest in
     * orientation to the one accepted last frame, to avoid flicker.
     */
    private fun choose(candidates: List<Candidate>, gravityCamera: DoubleArray?): PoseEstimate? {
        if (candidates.isEmpty()) return null
        val bestError = candidates.minOf { it.error }
        val contenders = candidates.filter {
            it.error <= bestError * ERROR_TOLERANCE_FACTOR + ERROR_TOLERANCE_FLOOR_PX
        }
        if (gravityCamera != null) {
            contenders.maxByOrNull {
                GravityPoseDisambiguator.alignmentScore(it.pose, gravityCamera)
            }?.let { return it.pose }
        }
        val reference = previousPose
            ?: return contenders.minByOrNull { it.error }?.pose
        return contenders.minByOrNull {
            RotationMath.angleBetweenDegrees(reference.rvec, it.pose.rvec)
        }?.pose
    }

    /** Forgets the previous pose so a re-acquisition is not biased toward a stale orientation. */
    override fun reset() {
        previousPose = null
    }

    /**
     * Rejects solver blow-ups. A canonical Rodrigues vector encodes an angle in radians, so its
     * magnitude can never exceed pi — anything larger (IPPE has been observed returning ~1e8) is
     * numerical garbage, not a pose.
     */
    private fun isNumericallySane(rvec: DoubleArray, tvec: DoubleArray): Boolean {
        if (rvec.any { !it.isFinite() } || tvec.any { !it.isFinite() }) return false
        val angle = sqrt(rvec[0] * rvec[0] + rvec[1] * rvec[1] + rvec[2] * rvec[2])
        return angle <= Math.PI + 1e-3
    }

    /**
     * True when the board's front/printed face is turned toward the camera. The board's native
     * chessboard-corner frame has local +Z pointing *away* from the camera through the back of the
     * board when facing it dead-on (see [MarkerConfig]'s class doc), so that axis rotated into
     * camera frame should land on the camera's own +Z (forward) — i.e. R[2][2] > 0. This is the
     * opposite sign from this project's old single-tag ArUco convention, which had local +Z toward
     * the camera.
     */
    private fun facesCamera(rvec: DoubleArray): Boolean {
        val rvecMat = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *rvec) }
        val rmat = Mat()
        return try {
            Calib3d.Rodrigues(rvecMat, rmat)
            rmat.get(2, 2)[0] > 0.0
        } catch (error: Exception) {
            Log.w(TAG, "Rodrigues failed", error)
            false
        } finally {
            rvecMat.release()
            rmat.release()
        }
    }

    /**
     * True unless [tvec]'s distance is *far* outside the rig's expected 200-400mm working range —
     * padded generously so it only rejects clearly-wrong candidates (numerical debris), not merely
     * atypical but real distances.
     */
    private fun isPlausibleDistance(tvec: DoubleArray): Boolean {
        val distanceMm = sqrt(tvec[0] * tvec[0] + tvec[1] * tvec[1] + tvec[2] * tvec[2])
        val range = MarkerConfig.EXPECTED_DISTANCE_RANGE_MM
        return distanceMm >= range.start * DISTANCE_PLAUSIBILITY_MARGIN_LOW &&
            distanceMm <= range.endInclusive * DISTANCE_PLAUSIBILITY_MARGIN_HIGH
    }

    /**
     * Caps the resolved pose's tilt, measured relative to [CANONICAL_FACING_RVEC], at
     * [MarkerConfig.MAX_TILT_CLAMP_DEGREES]. This is a safety net for residual numerical error,
     * not the primary defence — that is [facesCamera]/[isPlausibleDistance]/gravity selection above.
     */
    private fun clampTilt(pose: PoseEstimate): PoseEstimate {
        val clampedRvec = RotationMath.clampRelativeToReference(
            pose.rvec, CANONICAL_FACING_RVEC, MarkerConfig.MAX_TILT_CLAMP_DEGREES,
        )
        return pose.copy(rvec = clampedRvec)
    }

    /** RMS reprojection error of [objectMat] under the given pose, against the observed corners. */
    private fun reprojectionError(
        objectMat: MatOfPoint3f,
        imageMat: MatOfPoint2f,
        cameraMatrix: Mat,
        distCoeffs: MatOfDouble,
        rvec: Mat,
        tvec: Mat,
    ): Double {
        val projected = MatOfPoint2f()
        return try {
            Calib3d.projectPoints(objectMat, rvec, tvec, cameraMatrix, distCoeffs, projected)
            val expected = imageMat.toArray()
            val actual = projected.toArray()
            if (expected.size != actual.size || expected.isEmpty()) return Double.NaN
            var sum = 0.0
            for (i in expected.indices) {
                val dx = expected[i].x - actual[i].x
                val dy = expected[i].y - actual[i].y
                sum += dx * dx + dy * dy
            }
            sqrt(sum / expected.size)
        } catch (error: Exception) {
            Log.w(TAG, "reprojection failed", error)
            Double.NaN
        } finally {
            projected.release()
        }
    }

    private fun Point3Mm.toCvPoint3(): Point3 = Point3(x, y, z)

    private fun CameraIntrinsics.toCameraMatrix(): Mat {
        return Mat(3, 3, CvType.CV_64F).apply {
            put(0, 0, fx, 0.0, cx, 0.0, fy, cy, 0.0, 0.0, 1.0)
        }
    }

    private companion object {
        const val TAG = "MobileAimPose"

        /** solvePnP's mathematical floor for a coplanar point set is 4; a lone triangle is degenerate. */
        const val MIN_CORRESPONDENCES = 4

        /** Candidates within this factor of the lowest reprojection error count as tied. */
        const val ERROR_TOLERANCE_FACTOR = 1.5

        /** Absolute slack so near-zero errors (clean synthetic input) still compare as tied. */
        const val ERROR_TOLERANCE_FLOOR_PX = 0.05

        const val DISTANCE_PLAUSIBILITY_MARGIN_LOW = 0.5
        const val DISTANCE_PLAUSIBILITY_MARGIN_HIGH = 2.0

        /**
         * "Camera looking straight at the board, no tilt" is the identity rotation here, because
         * the board's native chessboard-corner frame already shares the camera's own
         * X-right/Y-down/Z-away-from-camera axes (see [MarkerConfig]'s class doc for the on-device
         * derivation) — unlike this project's old hand-rolled single-tag frame, which needed a
         * 180-degree correction.
         */
        val CANONICAL_FACING_RVEC = doubleArrayOf(0.0, 0.0, 0.0)
    }
}
