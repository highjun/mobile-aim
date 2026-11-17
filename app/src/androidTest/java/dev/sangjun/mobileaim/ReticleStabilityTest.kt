package dev.sangjun.mobileaim

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.strategy.checkermarker.OpenCvPoseEstimationService
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.strategy.checkermarker.PoseProjector
import dev.sangjun.mobileaim.strategy.checkermarker.PoseSmoother
import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.Point3Mm
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

/**
 * Measures how much the reticle moves when the marker is *not* moving.
 *
 * The user's complaint is that the aim point shakes badly even with a near-motionless hand, so the
 * scenario here is a completely static marker with only sub-pixel corner-detection noise injected.
 * Any reticle movement is therefore pure noise amplification: the aim point sits 25m out from a
 * marker a few centimetres across, so it is driven almost entirely by the marker's out-of-plane
 * rotation — the least well-observed quantity in a planar-square pose solve.
 *
 * Numbers land in logcat under [TAG]; the sweep over marker size and smoothing strength is what
 * tells us whether this is fixable in software or needs a physically bigger marker.
 */
@RunWith(AndroidJUnit4::class)
class ReticleStabilityTest {

    private val poseService = OpenCvPoseEstimationService()

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
    fun staticMarkerJitterSweep() {
        val results = mutableMapOf<String, Jitter>()

        for (boardSizeMm in listOf(30.0, 45.0, 60.0)) {
            for ((smoothingLabel, alpha) in SMOOTHING_SETTINGS) {
                val jitter = measureJitter(boardSizeMm, alpha)
                results["${boardSizeMm.toInt()}mm/$smoothingLabel"] = jitter
                Log.i(
                    TAG,
                    "board=${boardSizeMm.toInt()}mm smoothing=$smoothingLabel " +
                        "maxStep=${"%.1f".format(jitter.maxStepPx)}px " +
                        "rmsStep=${"%.1f".format(jitter.rmsStepPx)}px " +
                        "spread=${"%.1f".format(jitter.spreadPx)}px " +
                        "=> worldSpread=${"%.2f".format(jitter.spreadPx / intrinsics.fx * RANGE_MM / 1000.0)}m at ${RANGE_METERS}m",
                )
            }
        }

        assertTrue("expected jitter measurements", results.isNotEmpty())
        assertTrue(
            "all measurements should be finite",
            results.values.all { it.maxStepPx.isFinite() && it.spreadPx.isFinite() },
        )
    }

    /**
     * Regression guard for the Rodrigues antipodal-collapse bug. Component-wise EMA of rotation
     * vectors measured a 93,000px frame-to-frame jump on this exact scenario — roughly 1600x worse
     * than no smoothing at all — because a near-frontal marker sits at a rotation angle of pi where
     * `pi about n` and `pi about -n` are the same rotation. Smoothing must damp motion, never
     * amplify it, so we also assert it beats the unsmoothed baseline.
     */
    @Test
    fun defaultSmoothingDampsRatherThanAmplifies() {
        val boardSizeMm = 45.0
        val unsmoothed = measureJitter(boardSizeMm, alpha = null)
        val smoothed = measureJitter(boardSizeMm, alpha = PoseSmoother.DEFAULT_ALPHA)

        Log.i(
            TAG,
            "regression: unsmoothed maxStep=${"%.1f".format(unsmoothed.maxStepPx)}px " +
                "vs smoothed maxStep=${"%.1f".format(smoothed.maxStepPx)}px",
        )

        assertTrue(
            "smoothing must reduce frame-to-frame movement, not amplify it " +
                "(unsmoothed=${unsmoothed.maxStepPx}px, smoothed=${smoothed.maxStepPx}px)",
            smoothed.maxStepPx < unsmoothed.maxStepPx,
        )
        assertTrue(
            "frame-to-frame reticle jump should stay small, was ${smoothed.maxStepPx}px",
            smoothed.maxStepPx < MAX_ACCEPTABLE_STEP_PX,
        )
    }

    private data class Jitter(val maxStepPx: Double, val rmsStepPx: Double, val spreadPx: Double)

    /**
     * Runs [FRAME_COUNT] frames of a static marker through the real chain
     * (estimatePose -> PoseSmoother -> projectLocalPoint), perturbing corners by seeded noise.
     * [alpha] of null means no pose smoothing at all.
     */
    private fun measureJitter(boardSizeMm: Double, alpha: Float?): Jitter {
        val corners = MarkerConfig.charucoBoard(boardSizeMm).chessboardCorners
        val objectPoints = (0 until corners.rows()).map { row ->
            val xyz = corners.get(row, 0)
            Point3Mm(xyz[0].toDouble(), xyz[1].toDouble(), xyz[2].toDouble())
        }
        // Slightly off-square so the pose is not sitting exactly on the degenerate frontal case.
        val truth = tiltedPose(distanceMm = 300.0, yawRadians = Math.toRadians(8.0))
        val cleanCorners = synthesizeCorners(truth, objectPoints)

        val random = Random(SEED)
        val smoother = alpha?.let { PoseSmoother(it) }
        val target = MarkerConfig.targetLocalPointMm(RANGE_METERS, xOffsetMm = 0.0, Y_OFFSET_MM)

        val points = mutableListOf<Point2Px>()
        repeat(FRAME_COUNT) {
            val noisy = cleanCorners.map { corner ->
                Point2Px(
                    x = corner.x + ((random.nextDouble() - 0.5) * 2.0 * NOISE_PX).toFloat(),
                    y = corner.y + ((random.nextDouble() - 0.5) * 2.0 * NOISE_PX).toFloat(),
                )
            }
            val rawPose = poseService.estimatePose(objectPoints, noisy, intrinsics) ?: return@repeat
            val pose = smoother?.smooth(rawPose) ?: rawPose
            PoseProjector.projectLocalPoint(pose, intrinsics, target)?.let { points += it }
        }

        if (points.size < 2) return Jitter(Double.NaN, Double.NaN, Double.NaN)

        var maxStep = 0.0
        var sumSquaredStep = 0.0
        for (i in 1 until points.size) {
            val step = distance(points[i - 1], points[i])
            if (step > maxStep) maxStep = step
            sumSquaredStep += step * step
        }

        // Overall spread: max distance of any sample from the mean position, doubled to approximate
        // the peak-to-peak wander a user would actually perceive.
        val meanX = points.map { it.x.toDouble() }.average()
        val meanY = points.map { it.y.toDouble() }.average()
        val spread = 2.0 * points.maxOf {
            sqrt((it.x - meanX) * (it.x - meanX) + (it.y - meanY) * (it.y - meanY))
        }

        return Jitter(
            maxStepPx = maxStep,
            rmsStepPx = sqrt(sumSquaredStep / (points.size - 1)),
            spreadPx = spread,
        )
    }

    private fun distance(a: Point2Px, b: Point2Px): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Ry(yaw) — the board's native frame already shares the camera's axes when facing it dead-on
     * (see [MarkerConfig]'s class doc), so this is just a yaw about the board's own local Y axis.
     */
    private fun tiltedPose(distanceMm: Double, yawRadians: Double): PoseEstimate {
        val c = cos(yawRadians)
        val s = sin(yawRadians)
        val rotation = arrayOf(
            doubleArrayOf(c, 0.0, s),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(-s, 0.0, c),
        )
        val rmat = Mat(3, 3, CvType.CV_64F)
        val rvec = Mat()
        try {
            for (row in 0..2) {
                for (col in 0..2) rmat.put(row, col, rotation[row][col])
            }
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
        val objectMat = MatOfPoint3f().apply {
            fromList(objectPoints.map { Point3(it.x, it.y, it.z) })
        }
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

    private companion object {
        const val TAG = "MobileAimStability"
        const val SEED = 20260730L
        const val FRAME_COUNT = 60
        /** Realistic sub-pixel corner localisation noise for a refined ArUco detection. */
        const val NOISE_PX = 0.3
        const val Y_OFFSET_MM = 20.0
        const val RANGE_METERS = 25f
        const val RANGE_MM = 25_000.0

        /** Generous ceiling — the broken component-wise EMA measured ~93,000px here. */
        const val MAX_ACCEPTABLE_STEP_PX = 20.0

        val SMOOTHING_SETTINGS: List<Pair<String, Float?>> = listOf(
            "none" to null,
            "a=0.15" to 0.15f,
            "a=0.08" to 0.08f,
            "a=0.05" to 0.05f,
        )
    }
}
