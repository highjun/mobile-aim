package dev.sangjun.mobileaim.feature.pose

data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val distCoeffs: DoubleArray = DoubleArray(5),
    /** The pixel resolution these fx/fy/cx/cy values are valid for. */
    val referenceWidth: Int,
    val referenceHeight: Int,
)

/** Marker pose relative to the camera, as returned by solvePnP: Rodrigues rotation + translation vectors. */
data class PoseEstimate(val rvec: DoubleArray, val tvec: DoubleArray)
