package dev.sangjun.mobileaim.feature.drift

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sangjun.mobileaim.feature.pose.CameraIntrinsics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.random.Random

/**
 * Validates [OrbDriftMonitorService] against synthetic frames: the same textured frame should
 * never read as drift, but a frame whose content has shifted *without* a matching gyro rotation
 * (exactly what a slipped mount looks like) must be flagged.
 */
@RunWith(AndroidJUnit4::class)
class OrbDriftMonitorServiceTest {
    private val intrinsics = CameraIntrinsics(
        fx = 1000.0,
        fy = 1000.0,
        cx = 320.0,
        cy = 240.0,
        referenceWidth = 640,
        referenceHeight = 480,
    )

    /** `[w, x, y, z]` identity — "camera hasn't rotated" for both reference and current frame. */
    private val identityRotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0)

    @Before
    fun setUp() {
        assertTrue("OpenCV native library must load", OpenCVLoader.initLocal())
    }

    @Test
    fun noMotionIsNotFlaggedAsDrift() {
        val service = OrbDriftMonitorService()
        val frame = texturedFrame()
        try {
            service.setReference(frame, rotationDegrees = 0, rotationDeviceFrame = identityRotation)
            service.analyze(frame, rotationDegrees = 0, intrinsics = intrinsics, rotationDeviceFrame = identityRotation)

            val status = service.state.value
            assertTrue("expected enough ORB matches on an identical frame", status.matchCount >= 8)
            assertFalse("identical frame was flagged as drift", status.isDrifting)
        } finally {
            frame.release()
        }
    }

    @Test
    fun unexplainedShiftIsFlaggedAsDrift() {
        val service = OrbDriftMonitorService()
        val reference = texturedFrame()
        val shifted = shiftedFrame(reference, dxPx = 30)
        try {
            service.setReference(reference, rotationDegrees = 0, rotationDeviceFrame = identityRotation)
            // Same gyro reading as the reference (the rig reports no rotation) but the scene
            // content moved — exactly the "mount slipped" signature, since a genuine aiming
            // rotation would instead have shown up in rotationDeviceFrame and been corrected for.
            service.analyze(shifted, rotationDegrees = 0, intrinsics = intrinsics, rotationDeviceFrame = identityRotation)

            val status = service.state.value
            assertTrue("expected enough ORB matches on a shifted frame", status.matchCount >= 8)
            assertTrue("shifted frame was not flagged as drift", status.isDrifting)
        } finally {
            reference.release()
            shifted.release()
        }
    }

    /** A pattern of random rectangles — plenty of ORB-friendly corners, unlike a blank frame. */
    private fun texturedFrame(): Mat {
        val mat = Mat(480, 640, CvType.CV_8UC1, Scalar(255.0))
        val random = Random(42)
        repeat(60) {
            val x = random.nextInt(20, 600)
            val y = random.nextInt(20, 440)
            val size = random.nextInt(10, 30)
            Imgproc.rectangle(mat, Point(x.toDouble(), y.toDouble()), Point((x + size).toDouble(), (y + size).toDouble()), Scalar(0.0), -1)
        }
        return mat
    }

    /** [source] with its content shifted [dxPx] to the left — simulates the scene sliding sideways. */
    private fun shiftedFrame(source: Mat, dxPx: Int): Mat {
        val shifted = Mat(source.rows(), source.cols(), CvType.CV_8UC1, Scalar(255.0))
        val croppedSource = Mat(source, Rect(dxPx, 0, source.cols() - dxPx, source.rows()))
        val destinationRoi = Mat(shifted, Rect(0, 0, source.cols() - dxPx, source.rows()))
        croppedSource.copyTo(destinationRoi)
        return shifted
    }
}
