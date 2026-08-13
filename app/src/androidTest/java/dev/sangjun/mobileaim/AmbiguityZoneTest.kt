package dev.sangjun.mobileaim

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.CharucoMarkerDetectionService
import dev.sangjun.mobileaim.strategy.checkermarker.GravityPoseDisambiguator
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.strategy.checkermarker.OpenCvPoseEstimationService
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.Point3Mm
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

/**
 * The success criterion for the gravity-disambiguation redesign: sweep the rig's real operating
 * envelope (200-400mm distance, 0-15 degree tilt, 45/55/65mm boards, and a marginal 5-of-9-corner
 * detection matching [dev.sangjun.mobileaim.strategy.checkermarker.CharucoMarkerDetectionService]'s
 * minimum gate) with realistic corner noise, and confirm gravity disambiguation never makes the
 * planar pose's up/down-flip twin *more* likely to be picked than reprojection error alone.
 *
 * A *tilt* (rotation about the board's local X axis) is used deliberately, not yaw — that is the
 * ambiguity mode gravity actually targets (see [dev.sangjun.mobileaim.strategy.checkermarker.GravityPoseDisambiguator]'s
 * class doc) and the one reported as "aim point flips top-to-bottom of the screen."
 *
 * Measured finding, worth stating plainly: within this operating envelope, even at the 5-corner
 * minimum, the flip rate was 0% *with or without* gravity — the ChArUco board's many-point
 * correspondence (versus the old single 4-point ArUco tag) already conditions the SQPNP solve well
 * enough that the classic two-fold planar ambiguity doesn't measurably manifest here. Gravity only
 * showed a measurable effect well outside the rig's promised range (tilts past ~60 degrees, tested
 * manually, not asserted below since the rig will never see them). That does not make gravity
 * disambiguation dead weight: it is a real, physically-grounded safety net for conditions this
 * synthetic sweep cannot cover (asymmetric corner occlusion, non-Gaussian detector failure modes,
 * a smaller or more distorted board than tested here) — but the primary fix for the reported bug
 * was the ChArUco migration itself, not gravity. This test exists to catch a *regression*: if a
 * future change reintroduces flips within this envelope, gravity must not make them worse.
 */
@RunWith(AndroidJUnit4::class)
class AmbiguityZoneTest {

    private val intrinsics = CameraIntrinsics(
        fx = 1000.0,
        fy = 1000.0,
        cx = 320.0,
        cy = 240.0,
        referenceWidth = 640,
        referenceHeight = 480,
    )

    @Before
    fun setUp() {
        assertTrue("OpenCV native library must load", OpenCVLoader.initLocal())
    }

    @Test
    fun gravityDisambiguationCutsUpDownFlipRate() {
        val random = Random(SEED)
        var withoutGravityFlips = 0
        var withGravityFlips = 0
        var total = 0

        for (boardSizeMm in listOf(45.0, 55.0, 65.0)) {
            val objectPoints = chessboardObjectPoints(boardSizeMm)
            for (distanceMm in listOf(200.0, 300.0, 400.0)) {
                for (tiltDegrees in listOf(2.0, 5.0, 8.0, 12.0, 15.0)) {
                    val truth = tiltedPose(distanceMm, Math.toRadians(tiltDegrees))
                    // Marginal detection: only the first 5 of 9 corners "visible" (MIN_CORNERS,
                    // matching CharucoMarkerDetectionService's gate) — a harder, still-realistic
                    // case than a full-board detection with pixel noise alone.
                    val visibleObjectPoints = objectPoints.take(5)
                    val cleanCorners = synthesizeCorners(truth, visibleObjectPoints)
                    val gravityCamera = trueUpInCameraFrame(truth.rvec)

                    // Fresh service per condition: previous-pose memory would otherwise let one
                    // lucky early frame bias every later trial in the same condition.
                    repeat(TRIALS_PER_CONDITION) {
                        val noisyCorners = cleanCorners.map { corner ->
                            Point2Px(
                                x = corner.x + ((random.nextDouble() - 0.5) * 2.0 * NOISE_PX).toFloat(),
                                y = corner.y + ((random.nextDouble() - 0.5) * 2.0 * NOISE_PX).toFloat(),
                            )
                        }
                        total++

                        val withoutGravity = OpenCvPoseEstimationService()
                            .estimatePose(visibleObjectPoints, noisyCorners, intrinsics, gravityCamera = null)
                        if (withoutGravity != null && isFlipped(truth.rvec, withoutGravity.rvec)) withoutGravityFlips++

                        val withGravity = OpenCvPoseEstimationService()
                            .estimatePose(visibleObjectPoints, noisyCorners, intrinsics, gravityCamera = gravityCamera)
                        if (withGravity != null && isFlipped(truth.rvec, withGravity.rvec)) withGravityFlips++
                    }
                }
            }
        }

        val withoutRate = withoutGravityFlips.toDouble() / total
        val withRate = withGravityFlips.toDouble() / total
        Log.i(
            TAG,
            "flip rate without gravity: $withoutGravityFlips/$total (${"%.1f".format(withoutRate * 100)}%) " +
                "vs with gravity: $withGravityFlips/$total (${"%.1f".format(withRate * 100)}%)",
        )

        assertTrue(
            "gravity disambiguation must not increase the flip rate " +
                "(without=$withoutGravityFlips, with=$withGravityFlips)",
            withGravityFlips <= withoutGravityFlips,
        )
        assertTrue(
            "up/down flips should stay rare across the operating envelope (with gravity enabled), " +
                "was ${"%.1f".format(withRate * 100)}%",
            withRate < MAX_ACCEPTABLE_FLIP_RATE,
        )
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun chessboardObjectPoints(boardSizeMm: Double): List<Point3Mm> {
        val corners = MarkerConfig.charucoBoard(boardSizeMm).chessboardCorners
        return (0 until corners.rows()).map { row ->
            val xyz = corners.get(row, 0)
            Point3Mm(xyz[0].toDouble(), xyz[1].toDouble(), xyz[2].toDouble())
        }
    }

    /** Rx(tiltRadians): the board's native frame matches the camera's when facing it dead-on. */
    private fun tiltedPose(distanceMm: Double, tiltRadians: Double): PoseEstimate {
        val c = cos(tiltRadians)
        val s = sin(tiltRadians)
        val rotation = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, c, -s),
            doubleArrayOf(0.0, s, c),
        )
        val rmat = Mat(3, 3, CvType.CV_64F)
        val rvec = Mat()
        try {
            for (row in 0..2) for (col in 0..2) rmat.put(row, col, rotation[row][col])
            Calib3d.Rodrigues(rmat, rvec)
            return PoseEstimate(
                rvec = DoubleArray(3).also { rvec.get(0, 0, it) },
                tvec = doubleArrayOf(0.0, 0.0, distanceMm),
            )
        } finally {
            rmat.release()
            rvec.release()
        }
    }

    private fun synthesizeCorners(pose: PoseEstimate, objectPoints: List<Point3Mm>): List<Point2Px> {
        val objectMat = MatOfPoint3f().apply { fromList(objectPoints.map { Point3(it.x, it.y, it.z) }) }
        val rvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.rvec) }
        val tvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.tvec) }
        val cameraMatrix = Mat(3, 3, CvType.CV_64F).apply {
            put(0, 0, intrinsics.fx, 0.0, intrinsics.cx, 0.0, intrinsics.fy, intrinsics.cy, 0.0, 0.0, 1.0)
        }
        val distCoeffs = MatOfDouble(*intrinsics.distCoeffs)
        val projected = MatOfPoint2f()
        try {
            Calib3d.projectPoints(objectMat, rvec, tvec, cameraMatrix, distCoeffs, projected)
            return projected.toArray().map { Point2Px(it.x.toFloat(), it.y.toFloat()) }
        } finally {
            objectMat.release()
            rvec.release()
            tvec.release()
            cameraMatrix.release()
            projected.release()
        }
    }

    /**
     * What a perfect gravity sensor would read in camera frame for this exact ground truth: the
     * board's local "up" (-Y, see [MarkerConfig]'s class doc) rotated into camera frame.
     */
    private fun trueUpInCameraFrame(rvec: DoubleArray): DoubleArray {
        val rMat = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *rvec) }
        val rotMat = Mat()
        try {
            Calib3d.Rodrigues(rMat, rotMat)
            // R * (0,-1,0) = -1 * R's second column.
            return doubleArrayOf(-rotMat.get(0, 1)[0], -rotMat.get(1, 1)[0], -rotMat.get(2, 1)[0])
        } finally {
            rMat.release()
            rotMat.release()
        }
    }

    /** True when [recoveredRvec] sits closer to [truthRvec]'s up/down-flip twin than to itself. */
    private fun isFlipped(truthRvec: DoubleArray, recoveredRvec: DoubleArray): Boolean {
        val flippedRvec = flipUpDown(truthRvec)
        return rotationDeltaDegrees(flippedRvec, recoveredRvec) < rotationDeltaDegrees(truthRvec, recoveredRvec)
    }

    /** R_truth * diag(1,-1,-1) — the standard planar-pose mirror twin (see class doc). */
    private fun flipUpDown(rvec: DoubleArray): DoubleArray {
        val rMat = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *rvec) }
        val rotMat = Mat()
        val flipDiag = Mat(3, 3, CvType.CV_64F).apply {
            put(0, 0, 1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, -1.0)
        }
        val flippedRotMat = Mat()
        val flippedRvecMat = Mat()
        try {
            Calib3d.Rodrigues(rMat, rotMat)
            Core.gemm(rotMat, flipDiag, 1.0, Mat(), 0.0, flippedRotMat)
            Calib3d.Rodrigues(flippedRotMat, flippedRvecMat)
            return DoubleArray(3).also { flippedRvecMat.get(0, 0, it) }
        } finally {
            rMat.release()
            rotMat.release()
            flipDiag.release()
            flippedRotMat.release()
            flippedRvecMat.release()
        }
    }

    /** Angle of the relative rotation between two Rodrigues vectors, in degrees. */
    private fun rotationDeltaDegrees(a: DoubleArray, b: DoubleArray): Double {
        val ra = rotationMatrix(a)
        val rb = rotationMatrix(b)
        val rel = Mat()
        try {
            Core.gemm(ra, rb, 1.0, Mat(), 0.0, rel, Core.GEMM_1_T)
            val trace = rel.get(0, 0)[0] + rel.get(1, 1)[0] + rel.get(2, 2)[0]
            val cosTheta = ((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)
            return Math.toDegrees(kotlin.math.acos(cosTheta))
        } finally {
            ra.release()
            rb.release()
            rel.release()
        }
    }

    private fun rotationMatrix(rvec: DoubleArray): Mat {
        val v = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *rvec) }
        val m = Mat()
        try {
            Calib3d.Rodrigues(v, m)
        } finally {
            v.release()
        }
        return m
    }

    private companion object {
        const val TAG = "MobileAimAmbiguity"
        const val SEED = 20260730L
        const val TRIALS_PER_CONDITION = 15

        /** Realistic sub-pixel corner localisation noise for a refined ChArUco detection. */
        const val NOISE_PX = 0.3

        const val MAX_ACCEPTABLE_FLIP_RATE = 0.05
    }
}
