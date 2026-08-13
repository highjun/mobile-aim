package dev.sangjun.mobileaim

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.GravityPoseDisambiguator
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.strategy.checkermarker.OpenCvPoseEstimationService
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.strategy.checkermarker.PoseProjector
import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.Point3Mm
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
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
 * Validates the pose-estimation + projection math against synthetic ground truth.
 *
 * These have to be instrumented tests rather than plain JVM unit tests: the OpenCV AAR only ships
 * Android native libraries, so `org.opencv.*` cannot be exercised on a desktop JVM.
 *
 * Projection tests deliberately use a *ground-truth* pose rather than an estimated one, so that
 * projection correctness is not confounded by pose-estimation conditioning (which is characterised
 * separately in [recoveredPoseAimsWithinToleranceAcrossViews]).
 *
 * Object points are the ChArUco board's real interior chessboard corners (see [MarkerConfig]),
 * whose native frame is X-right/Y-down/Z-away-from-camera — the camera's own axes, so "facing the
 * camera dead-on" is the *identity* rotation here, not the 180-degree flip this project's old
 * single-tag ArUco convention needed. See [MarkerConfig]'s class doc for the on-device derivation.
 */
@RunWith(AndroidJUnit4::class)
class ProjectionAccuracyTest {

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

    // --- pose estimation -----------------------------------------------------------------------

    @Test
    fun poseRoundTripRecoversKnownTranslation() {
        val groundTruth = frontalPose(distanceMm = 300.0)
        val recovered = poseService.estimatePose(
            objectPoints(BOARD_SIZE_MM),
            synthesizeCorners(groundTruth, BOARD_SIZE_MM),
            intrinsics,
        )
        requireNotNull(recovered) { "estimatePose returned null for a clean synthetic frontal view" }

        assertEquals(groundTruth.tvec[0], recovered.tvec[0], 0.5)
        assertEquals(groundTruth.tvec[1], recovered.tvec[1], 0.5)
        assertEquals(groundTruth.tvec[2], recovered.tvec[2], 0.5)
    }

    @Test
    fun recoveredDistanceMatchesGroundTruth() {
        for (distanceMm in listOf(200.0, 300.0, 400.0)) {
            val recovered = poseService.estimatePose(
                objectPoints(BOARD_SIZE_MM),
                synthesizeCorners(frontalPose(distanceMm), BOARD_SIZE_MM),
                intrinsics,
            )
            requireNotNull(recovered) { "estimatePose returned null at ${distanceMm}mm" }
            assertEquals("distance at ${distanceMm}mm", distanceMm, norm(recovered.tvec), 1.0)
        }
    }

    // --- target point geometry -----------------------------------------------------------------

    /**
     * The aim point must land *in front of* the camera. The board's +Z axis points *away* from
     * the camera through its back, so a downrange target has to be at positive Z in board space —
     * the opposite sign from this project's old single-tag convention. At -Z a 25m target would
     * sit ~25m *behind* a camera that is only 30cm from the board, projecting to a mirrored,
     * meaningless pixel.
     */
    @Test
    fun targetPointIsInFrontOfCamera() {
        val poses = mapOf(
            "frontal" to frontalPose(distanceMm = 300.0),
            "yaw 15deg" to yawedPose(distanceMm = 300.0, yawRadians = Math.toRadians(15.0)),
        )
        for ((label, pose) in poses) {
            val target = MarkerConfig.targetLocalPointMm(RANGE_METERS, xOffsetMm = 0.0, Y_OFFSET_MM)
            val depthMm = markerToCamera(pose, target)[2]
            assertTrue(
                "$label: target must be in front of the camera (positive depth) but was ${depthMm}mm",
                depthMm > 0.0,
            )
        }
    }

    /**
     * Square to the camera the reticle sits essentially on the optical centre — the small vertical
     * boresight offset subtends a tiny angle at 25m. Analytically: u = cx, v = cy + fy*yOff/Z.
     */
    @Test
    fun frontalProjectionMatchesAnalyticalValue() {
        val distanceMm = 300.0
        val pose = frontalPose(distanceMm)
        val target = MarkerConfig.targetLocalPointMm(RANGE_METERS, xOffsetMm = 0.0, Y_OFFSET_MM)

        val projected = PoseProjector.projectLocalPoint(pose, intrinsics, target)
        requireNotNull(projected) { "projectLocalPoint returned null" }

        val expectedZ = RANGE_METERS * 1000.0 + distanceMm
        val expectedV = intrinsics.cy + intrinsics.fy * Y_OFFSET_MM / expectedZ

        assertEquals("u", intrinsics.cx, projected.x.toDouble(), 0.01)
        assertEquals("v", expectedV, projected.y.toDouble(), 0.01)
    }

    /**
     * Yawing the board must swing the far aim point sideways by the analytically predicted
     * amount. This is the test that pins down the target-point sign: with the sign inverted both
     * the numerator and denominator of u flip, landing the reticle at a visibly different pixel.
     */
    @Test
    fun yawedProjectionMatchesAnalyticalValue() {
        val distanceMm = 300.0
        val yaw = Math.toRadians(15.0)
        val pose = yawedPose(distanceMm, yaw)
        val target = MarkerConfig.targetLocalPointMm(RANGE_METERS, xOffsetMm = 0.0, Y_OFFSET_MM)

        val projected = PoseProjector.projectLocalPoint(pose, intrinsics, target)
        requireNotNull(projected) { "projectLocalPoint returned null" }

        val rangeMm = RANGE_METERS * 1000.0
        // Board's local Z (away from camera) rotates toward local X under this yaw definition —
        // see yawedPose's rotation matrix.
        val camX = sin(yaw) * rangeMm
        val camY = Y_OFFSET_MM
        val camZ = cos(yaw) * rangeMm + distanceMm
        val expectedU = intrinsics.cx + intrinsics.fx * camX / camZ
        val expectedV = intrinsics.cy + intrinsics.fy * camY / camZ

        assertEquals("u", expectedU, projected.x.toDouble(), 0.05)
        assertEquals("v", expectedV, projected.y.toDouble(), 0.05)
    }

    // --- characterisation ----------------------------------------------------------------------

    /**
     * Guards against the planar-pose ambiguity regressing. A near-frontal board has two poses that
     * reproject almost identically; picking the wrong one barely moves the corners but throws the
     * far aim point off by metres, and flips between frames. Feeding the *recovered* pose back
     * through projection catches that, because aiming [RANGE_METERS] out from a board a few
     * centimetres across amplifies any rotation error by roughly range/distance. This deliberately
     * exercises *yaw* (not tilt), and without a gravity hint: yaw leaves the board's local Y axis
     * fixed, so [dev.sangjun.mobileaim.strategy.checkermarker.GravityPoseDisambiguator] has no discriminating
     * power here — this is characterising the solver/candidate-selection defence on its own. The
     * gravity-resolved tilt ambiguity gets its own dedicated sweep in AmbiguityZoneTest. Measured
     * numbers land in logcat under the tag below.
     */
    @Test
    fun recoveredPoseAimsWithinToleranceAcrossViews() {
        val distanceMm = 300.0
        for (boardSizeMm in listOf(30.0, 45.0, 60.0)) {
            for (yawDegrees in listOf(0.0, 5.0, 15.0)) {
                val truth = if (yawDegrees == 0.0) {
                    frontalPose(distanceMm)
                } else {
                    yawedPose(distanceMm, Math.toRadians(yawDegrees))
                }
                val recovered = poseService.estimatePose(
                    objectPoints(boardSizeMm),
                    synthesizeCorners(truth, boardSizeMm),
                    intrinsics,
                ) ?: continue

                val target = MarkerConfig.targetLocalPointMm(RANGE_METERS, xOffsetMm = 0.0, Y_OFFSET_MM)
                val truthPx = PoseProjector.projectLocalPoint(truth, intrinsics, target)
                val estimatedPx = PoseProjector.projectLocalPoint(recovered, intrinsics, target)
                if (truthPx == null || estimatedPx == null) continue

                val pixelError = sqrt(
                    (truthPx.x - estimatedPx.x).toDouble() * (truthPx.x - estimatedPx.x) +
                        (truthPx.y - estimatedPx.y).toDouble() * (truthPx.y - estimatedPx.y),
                )
                // Convert the pixel miss into a real-world miss at the target distance.
                val worldErrorMm = pixelError / intrinsics.fx * (RANGE_METERS * 1000.0)
                val rotationErrorDeg = rotationDeltaDegrees(truth.rvec, recovered.rvec)

                Log.i(
                    TAG,
                    "board=${boardSizeMm}mm yaw=${yawDegrees}deg " +
                        "rotErr=${"%.3f".format(rotationErrorDeg)}deg " +
                        "aimErr=${"%.1f".format(pixelError)}px " +
                        "=> ${"%.2f".format(worldErrorMm / 1000.0)}m at ${RANGE_METERS}m",
                )

                assertTrue(
                    "board=${boardSizeMm}mm yaw=${yawDegrees}deg: aim point off by " +
                        "${"%.2f".format(worldErrorMm / 1000.0)}m at ${RANGE_METERS}m " +
                        "(rotation error ${"%.3f".format(rotationErrorDeg)}deg) — " +
                        "likely the planar-pose mirror solution was chosen",
                    worldErrorMm < MAX_AIM_ERROR_MM,
                )
            }
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun objectPoints(boardSizeMm: Double): List<Point3Mm> {
        val corners = MarkerConfig.charucoBoard(boardSizeMm).chessboardCorners
        return (0 until corners.rows()).map { row ->
            val xyz = corners.get(row, 0)
            Point3Mm(xyz[0].toDouble(), xyz[1].toDouble(), xyz[2].toDouble())
        }
    }

    private fun norm(v: DoubleArray): Double = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    /**
     * Board squarely facing the camera at [distanceMm]. The board's native frame already shares
     * the camera's X-right/Y-down/Z-away-from-camera axes (see [MarkerConfig]'s class doc), so
     * facing the camera dead-on needs no rotation at all.
     */
    private fun frontalPose(distanceMm: Double): PoseEstimate =
        poseFromRotation(
            arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0),
            ),
            distanceMm,
        )

    /** [frontalPose] additionally rotated about its own local Y axis by [yawRadians]. */
    private fun yawedPose(distanceMm: Double, yawRadians: Double): PoseEstimate {
        val c = cos(yawRadians)
        val s = sin(yawRadians)
        return poseFromRotation(
            arrayOf(
                doubleArrayOf(c, 0.0, s),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(-s, 0.0, c),
            ),
            distanceMm,
        )
    }

    private fun poseFromRotation(rotation: Array<DoubleArray>, distanceMm: Double): PoseEstimate {
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

    /** Renders the board's corners to pixels for a known pose — our synthetic "camera frame". */
    private fun synthesizeCorners(pose: PoseEstimate, boardSizeMm: Double): List<Point2Px> {
        val objectMat = MatOfPoint3f().apply {
            fromList(objectPoints(boardSizeMm).map { Point3(it.x, it.y, it.z) })
        }
        val rvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.rvec) }
        val tvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.tvec) }
        val cameraMatrix = cameraMatrix()
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

    private fun cameraMatrix(): Mat = Mat(3, 3, CvType.CV_64F).apply {
        put(0, 0, intrinsics.fx, 0.0, intrinsics.cx, 0.0, intrinsics.fy, intrinsics.cy, 0.0, 0.0, 1.0)
    }

    /** Transforms a board-space point into camera space using [pose]. */
    private fun markerToCamera(pose: PoseEstimate, point: Point3Mm): DoubleArray {
        val rvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.rvec) }
        val rmat = Mat()
        try {
            Calib3d.Rodrigues(rvec, rmat)
            return DoubleArray(3) { row ->
                rmat.get(row, 0)[0] * point.x +
                    rmat.get(row, 1)[0] * point.y +
                    rmat.get(row, 2)[0] * point.z +
                    pose.tvec[row]
            }
        } finally {
            rvec.release()
            rmat.release()
        }
    }

    /** Angle of the relative rotation between two Rodrigues vectors, in degrees. */
    private fun rotationDeltaDegrees(a: DoubleArray, b: DoubleArray): Double {
        val ra = rotationMatrix(a)
        val rb = rotationMatrix(b)
        try {
            val rel = Mat()
            try {
                // trace(Ra^T * Rb) = 1 + 2cos(theta)
                Core.gemm(ra, rb, 1.0, Mat(), 0.0, rel, Core.GEMM_1_T)
                val trace = rel.get(0, 0)[0] + rel.get(1, 1)[0] + rel.get(2, 2)[0]
                val cosTheta = ((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)
                return Math.toDegrees(kotlin.math.acos(cosTheta))
            } finally {
                rel.release()
            }
        } finally {
            ra.release()
            rb.release()
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
        const val TAG = "MobileAimAccuracy"
        const val BOARD_SIZE_MM = 50.0
        const val Y_OFFSET_MM = 20.0
        const val RANGE_METERS = 25f

        /** 0.5m at a 25m range — comfortably tight enough to catch a mirror-solution pick. */
        const val MAX_AIM_ERROR_MM = 500.0
    }
}
