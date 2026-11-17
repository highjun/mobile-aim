package dev.sangjun.mobileaim.strategy.checkermarker

import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.Point3Mm

interface IPoseEstimationService {
    /**
     * [objectPoints] and [imagePoints] must be the same size and index-matched (e.g. the ChArUco
     * board's detected chessboard-corner IDs, resolved to their 3D positions).
     *
     * [gravityCamera] is the phone's "up" direction, already rotated into the camera's optical
     * frame — when supplied, it resolves the planar pose's two-fold ambiguity by physical fact
     * (see [GravityPoseDisambiguator]) instead of guessing from reprojection error alone. Null
     * skips that step, falling back to temporal continuity with the previous pose.
     *
     * Returns null if the pose is degenerate/unsolvable.
     */
    fun estimatePose(
        objectPoints: List<Point3Mm>,
        imagePoints: List<Point2Px>,
        intrinsics: CameraIntrinsics,
        gravityCamera: DoubleArray? = null,
    ): PoseEstimate?

    /**
     * Clears any tracking state kept between calls. Call when the marker is lost so the next
     * acquisition is not biased toward a stale orientation.
     */
    fun reset()
}
