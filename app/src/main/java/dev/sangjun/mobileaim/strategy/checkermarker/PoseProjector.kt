package dev.sangjun.mobileaim.strategy.checkermarker

import android.util.Log
import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.Point3Mm
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

/**
 * Projects a 3D point through an already-known [PoseEstimate] onto the image plane. This is pure
 * projection math — identical regardless of which tracking strategy produced the pose (ChArUco
 * solvePnP, WhyCon conic decomposition, ...) — so every strategy shares it instead of each
 * reimplementing solvePnP's projectPoints call.
 */
object PoseProjector {
    fun projectLocalPoint(pose: PoseEstimate, intrinsics: CameraIntrinsics, localPointMm: Point3Mm): Point2Px? {
        val objectMat = MatOfPoint3f(Point3(localPointMm.x, localPointMm.y, localPointMm.z))
        val cameraMatrix = intrinsics.toCameraMatrix()
        val distCoeffs = MatOfDouble(*intrinsics.distCoeffs)
        val rvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.rvec) }
        val tvec = Mat(3, 1, CvType.CV_64F).apply { put(0, 0, *pose.tvec) }
        val projected = MatOfPoint2f()

        return try {
            Calib3d.projectPoints(objectMat, rvec, tvec, cameraMatrix, distCoeffs, projected)
            projected.toArray().firstOrNull()?.let { Point2Px(it.x.toFloat(), it.y.toFloat()) }
        } catch (error: Exception) {
            Log.w(TAG, "projectPoints failed", error)
            null
        } finally {
            objectMat.release()
            cameraMatrix.release()
            rvec.release()
            tvec.release()
            projected.release()
        }
    }

    private fun CameraIntrinsics.toCameraMatrix(): Mat = Mat(3, 3, CvType.CV_64F).apply {
        put(0, 0, fx, 0.0, cx, 0.0, fy, cy, 0.0, 0.0, 1.0)
    }

    private const val TAG = "MobileAimPoseProjector"
}
