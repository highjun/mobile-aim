package dev.sangjun.mobileaim.strategy.circlemarker

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sangjun.mobileaim.ProjectionAccuracyTest
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import kotlin.math.abs
import kotlin.math.acos
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
import org.opencv.imgproc.Imgproc

/**
 * Validates [CirclePoseRecovery] against synthetic ground truth: render a known 3D circle to
 * pixels via a real pinhole projection + [Imgproc.fitEllipse], run it back through
 * [ConicMath]/[CirclePoseRecovery], and check the recovered position/normal against the truth
 * used to render it. Mirrors [dev.sangjun.mobileaim.ProjectionAccuracyTest]'s approach for the
 * ChArUco path.
 */
@RunWith(AndroidJUnit4::class)
class CirclePoseRecoveryTest {

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
    fun frontalCircleRecoversKnownPositionAndNormal() {
        checkRecovery(truePosition = doubleArrayOf(0.0, 0.0, 300.0), tiltDegrees = 0.0, radiusMm = 25.0)
    }

    @Test
    fun tiltedCircleRecoversKnownPositionAndNormal() {
        for (tiltDegrees in listOf(10.0, 20.0, 30.0)) {
            checkRecovery(truePosition = doubleArrayOf(0.0, 0.0, 300.0), tiltDegrees = tiltDegrees, radiusMm = 25.0)
        }
    }

    @Test
    fun recoveryAcrossWorkingDistanceRange() {
        for (distanceMm in listOf(200.0, 300.0, 400.0)) {
            checkRecovery(truePosition = doubleArrayOf(0.0, 0.0, distanceMm), tiltDegrees = 15.0, radiusMm = 25.0)
        }
    }

    /** An off-axis (not centred in frame), untilted circle — position is not parallel to normal here. */
    @Test
    fun offAxisUntiltedCircleRecoversKnownPosition() {
        checkRecovery(truePosition = doubleArrayOf(100.0, -40.0, 300.0), tiltDegrees = 0.0, radiusMm = 25.0)
    }

    /** Off-axis *and* tilted — the general case, not aligned with any special axis. */
    @Test
    fun offAxisTiltedCircleRecoversKnownPosition() {
        checkRecovery(truePosition = doubleArrayOf(80.0, 30.0, 320.0), tiltDegrees = 20.0, radiusMm = 25.0)
    }

    /**
     * [tiltDegrees] tilts the circle's plane about the camera's X axis, starting from dead-on
     * facing the camera (normal `(0,0,-1)`, matching [CirclePoseRecovery]'s "faces the camera"
     * convention).
     */
    private fun checkRecovery(truePosition: DoubleArray, tiltDegrees: Double, radiusMm: Double) {
        val tilt = Math.toRadians(tiltDegrees)
        val trueNormal = doubleArrayOf(0.0, sin(tilt), -cos(tilt))

        val ellipse = projectCircleToEllipse(truePosition, trueNormal, radiusMm)
        val conic = ConicMath.ellipseToConic(ellipse)
        val candidates = CirclePoseRecovery.recover(conic, intrinsics, radiusMm)

        assertTrue("expected at least one surviving candidate", candidates.isNotEmpty())
        val best = candidates.minByOrNull { distance(it.positionMm, truePosition) }!!

        val positionErrorMm = distance(best.positionMm, truePosition)
        val normalErrorDeg = Math.toDegrees(acos(dot(best.normal, trueNormal).coerceIn(-1.0, 1.0)))

        Log.i(
            TAG,
            "truePos=${truePosition.toList()} tilt=${tiltDegrees}deg -> " +
                "posErr=${"%.2f".format(positionErrorMm)}mm normalErr=${"%.2f".format(normalErrorDeg)}deg " +
                "candidates=${candidates.size}",
        )

        assertTrue("position error too large: ${positionErrorMm}mm", positionErrorMm < POSITION_TOLERANCE_MM)
        assertTrue("normal error too large: ${normalErrorDeg}deg", normalErrorDeg < NORMAL_TOLERANCE_DEG)
    }

    /** Projects a 3D circle's boundary to pixels via a real pinhole projection, then fits an ellipse to those pixels. */
    private fun projectCircleToEllipse(centerMm: DoubleArray, normal: DoubleArray, radiusMm: Double): RotatedEllipse {
        val arbitrary = if (abs(normal[2]) < 0.9) doubleArrayOf(0.0, 0.0, 1.0) else doubleArrayOf(1.0, 0.0, 0.0)
        val u = normalize(cross(arbitrary, normal))
        val v = cross(normal, u)

        val points3d = (0 until 360 step 3).map { deg ->
            val t = Math.toRadians(deg.toDouble())
            Point3(
                centerMm[0] + radiusMm * (cos(t) * u[0] + sin(t) * v[0]),
                centerMm[1] + radiusMm * (cos(t) * u[1] + sin(t) * v[1]),
                centerMm[2] + radiusMm * (cos(t) * u[2] + sin(t) * v[2]),
            )
        }
        val objectMat = MatOfPoint3f().apply { fromList(points3d) }
        val rvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, 0.0, 0.0, 0.0) }
        val tvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, 0.0, 0.0, 0.0) }
        val cameraMatrix = Mat(3, 3, CvType.CV_64F).apply {
            put(0, 0, intrinsics.fx, 0.0, intrinsics.cx, 0.0, intrinsics.fy, intrinsics.cy, 0.0, 0.0, 1.0)
        }
        val distCoeffs = MatOfDouble(*intrinsics.distCoeffs)
        val projected = MatOfPoint2f()
        try {
            Calib3d.projectPoints(objectMat, rvec, tvec, cameraMatrix, distCoeffs, projected)
            val rotatedRect = Imgproc.fitEllipse(projected)
            val majorMinor = listOf(rotatedRect.size.width, rotatedRect.size.height).sortedDescending()
            // fitEllipse's `angle` is the rotation of `size.width`'s axis from horizontal — if
            // `height` turns out to be the longer axis instead, the true major-axis angle is
            // perpendicular to the reported one.
            val angleRad = if (rotatedRect.size.width >= rotatedRect.size.height) {
                Math.toRadians(rotatedRect.angle)
            } else {
                Math.toRadians(rotatedRect.angle + 90.0)
            }
            return RotatedEllipse(
                cx = rotatedRect.center.x,
                cy = rotatedRect.center.y,
                a = majorMinor[0] / 2.0,
                b = majorMinor[1] / 2.0,
                angleRad = angleRad,
            )
        } finally {
            objectMat.release()
            rvec.release()
            tvec.release()
            cameraMatrix.release()
            projected.release()
        }
    }

    private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun normalize(v: DoubleArray): DoubleArray {
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return DoubleArray(3) { v[it] / n }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private companion object {
        const val TAG = "MobileAimWhyConPose"
        const val POSITION_TOLERANCE_MM = 5.0
        const val NORMAL_TOLERANCE_DEG = 2.0
    }
}
