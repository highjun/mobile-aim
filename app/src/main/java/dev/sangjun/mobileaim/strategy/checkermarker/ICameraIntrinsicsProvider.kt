package dev.sangjun.mobileaim.strategy.checkermarker

/**
 * Resolves camera intrinsics for a given camera and analysis frame.
 *
 * This is the swappable seam for calibration: [Camera2IntrinsicsProvider] auto-estimates from
 * device-reported characteristics. A future manual-calibration flow (e.g. chessboard capture) can
 * be added as another implementation and swapped in via `di/AppModule.kt` without touching callers.
 */
interface ICameraIntrinsicsProvider {
    /**
     * [frameWidth]/[frameHeight] are the dimensions of the frame we detect on, *after* the
     * [rotationDegrees] clockwise rotation the analysis pipeline applied.
     */
    fun getIntrinsics(
        cameraId: String,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
    ): CameraIntrinsics?
}
