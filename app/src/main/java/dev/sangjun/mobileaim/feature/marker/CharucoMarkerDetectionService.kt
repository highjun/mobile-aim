package dev.sangjun.mobileaim.feature.marker

import android.util.Log
import dev.sangjun.mobileaim.config.MarkerConfig
import dev.sangjun.mobileaim.feature.geometry.Point2Px
import org.opencv.core.Mat
import org.opencv.objdetect.CharucoDetector
import org.opencv.objdetect.CharucoParameters
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect

/**
 * Detects the ChArUco board and reports its interior chessboard corners — many well-conditioned
 * points instead of a single 4-point tag, which is what makes the pose estimate built on top of
 * this stable enough to disambiguate with [dev.sangjun.mobileaim.feature.pose.GravityPoseDisambiguator].
 *
 * The board built here is only for the detector's own geometry matching (dictionary + marker
 * layout) — physical size is irrelevant to 2D pixel detection, so a fixed placeholder is used
 * rather than the user's actual measured board size (see [MarkerConfig.charucoBoard]'s doc).
 * Turning [MarkerDetectionResult.cornerIds] into real 3D positions, scaled by the user's actual
 * board size, is [dev.sangjun.mobileaim.feature.tracking.CharucoAimTrackingService]'s job.
 */
class CharucoMarkerDetectionService : IMarkerDetectionService {
    private val board = MarkerConfig.charucoBoard(MarkerConfig.DEFAULT_BOARD_SIZE_MM)
    private val detector = CharucoDetector(
        board,
        CharucoParameters(),
        DetectorParameters().apply { set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX) },
    )

    override fun analyze(gray: Mat, rotationDegrees: Int, timestampMillis: Long): MarkerDetectionResult {
        val charucoCorners = Mat()
        val charucoIds = Mat()
        return try {
            detector.detectBoard(gray, charucoCorners, charucoIds)

            val detectedCount = charucoIds.rows()
            if (detectedCount >= MIN_CORNERS) {
                val points = (0 until detectedCount).map { row ->
                    val xy = charucoCorners.get(row, 0)
                    Point2Px(xy[0].toFloat(), xy[1].toFloat())
                }
                val ids = (0 until detectedCount).map { row -> charucoIds.get(row, 0)[0].toInt() }
                MarkerDetectionResult(
                    isDetected = true,
                    imagePoints = points,
                    cornerIds = ids,
                    frameWidth = gray.cols(),
                    frameHeight = gray.rows(),
                    rotationDegrees = rotationDegrees,
                    timestampMillis = timestampMillis,
                )
            } else {
                MarkerDetectionResult(
                    isDetected = false,
                    frameWidth = gray.cols(),
                    frameHeight = gray.rows(),
                    rotationDegrees = rotationDegrees,
                    timestampMillis = timestampMillis,
                    statusMessage = if (detectedCount == 0) {
                        "Board not found"
                    } else {
                        "Only $detectedCount corners found (need $MIN_CORNERS)"
                    },
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to analyze camera frame", error)
            MarkerDetectionResult(
                isDetected = false,
                frameWidth = gray.cols(),
                frameHeight = gray.rows(),
                rotationDegrees = rotationDegrees,
                timestampMillis = timestampMillis,
                statusMessage = "Analysis error",
            )
        } finally {
            charucoCorners.release()
            charucoIds.release()
        }
    }

    private companion object {
        const val TAG = "MobileAimMarker"

        /**
         * Below this many interior corners, solvePnP's conditioning gets too poor to trust. A 4x4
         * board caps out at 9 interior corners (an NxN grid yields (N-1)x(N-1)) — 5 keeps a margin
         * above the mathematical minimum (4) without demanding most of the board be visible.
         */
        const val MIN_CORNERS = 5
    }
}
