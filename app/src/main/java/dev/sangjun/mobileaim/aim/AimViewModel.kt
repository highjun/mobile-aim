package dev.sangjun.mobileaim.aim

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sangjun.mobileaim.app.TestPanelConfig
import dev.sangjun.mobileaim.app.sensor.IGravityProvider
import dev.sangjun.mobileaim.app.sensor.IRotationVectorProvider
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.app.settings.MarkerSettings
import dev.sangjun.mobileaim.app.ui.MainScreen
import dev.sangjun.mobileaim.driftdetection.DriftStatus
import dev.sangjun.mobileaim.driftdetection.IDriftMonitorService
import dev.sangjun.mobileaim.logging.CaptureRow
import dev.sangjun.mobileaim.logging.ICaptureLogWriter
import dev.sangjun.mobileaim.logging.ISensorMotionLogger
import dev.sangjun.mobileaim.strategy.AimStrategy
import dev.sangjun.mobileaim.strategy.IAimTrackingService
import dev.sangjun.mobileaim.strategy.TrackingResult
import dev.sangjun.mobileaim.strategy.ZeroableAimTracking
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import dev.sangjun.mobileaim.strategy.checkermarker.ICameraIntrinsicsProvider
import dev.sangjun.mobileaim.strategy.checkermarker.ImageProxyMatConverter
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.strategy.checkermarker.PoseEstimate
import dev.sangjun.mobileaim.strategy.checkermarker.PoseProjector
import dev.sangjun.mobileaim.strategy.checkermarker.PoseSmoother
import dev.sangjun.mobileaim.utils.GravityFrameConverter
import dev.sangjun.mobileaim.utils.GyroPosePredictor
import dev.sangjun.mobileaim.utils.Point2Px
import dev.sangjun.mobileaim.utils.RotationVectorFrameConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class AimViewModel(
    private val trackingService: IAimTrackingService,
    private val intrinsicsProvider: ICameraIntrinsicsProvider,
    private val settingsRepository: ISettingsRepository,
    private val gravityProvider: IGravityProvider,
    private val rotationVectorProvider: IRotationVectorProvider,
    private val motionLogger: ISensorMotionLogger,
    private val captureLogWriter: ICaptureLogWriter,
    private val driftMonitor: IDriftMonitorService,
) : ViewModel() {
    private val matConverter = ImageProxyMatConverter()

    /** Null until the user first moves the slider, at which point Z0 stops seeding it. */
    private val rangeOverrideFlow = MutableStateFlow<Float?>(null)

    /** Live, unpersisted test-panel overrides (X/Y/D/Z0) — same "override wins over Settings" pattern as range. */
    private val xOffsetOverrideFlow = MutableStateFlow<Float?>(null)
    private val yOffsetOverrideFlow = MutableStateFlow<Float?>(null)
    private val boardSizeOverrideFlow = MutableStateFlow<Float?>(null)
    private val z0OverrideFlow = MutableStateFlow<Float?>(null)

    private val cameraContextFlow = MutableStateFlow<CameraContext?>(null)
    private val reticleSmoother = PointSmoother()
    private val poseSmoother = PoseSmoother()

    /** Wall-clock time of the last real (non-frozen) detection; null once the pose has been forgotten. */
    private var lastDetectionTimestampMillis: Long? = null

    /** Device orientation at the last real detection — the anchor [GyroPosePredictor] predicts from. */
    private var lastVisionRotationDeviceFrame: DoubleArray? = null

    /** Points from the last real detection, kept on screen through a brief miss (see [stale]). */
    private var lastVisualizationPoints: List<Point2Px> = emptyList()

    /**
     * Set by [onZeroButtonPressed] (main thread) and consumed on the next [onFrame] (camera
     * thread) — [ZeroableAimTracking.setZero] needs a `rotationDegrees` value, which is only ever
     * computed fresh inside [onFrame], so zeroing rides along with the next frame rather than
     * needing its own cached copy of that value.
     */
    private var pendingZeroRequest = false

    init {
        gravityProvider.start()
        rotationVectorProvider.start()
        motionLogger.start()
    }

    /** For the Settings dialog to read/prefill current values. */
    val settingsState: StateFlow<MarkerSettings> = settingsRepository.settings

    private data class CombinedInputs(
        val trackingResult: TrackingResult,
        val rangeOverride: Float?,
        val cameraContext: CameraContext?,
        val settings: MarkerSettings,
    )

    /** X/Y/D/Z0 test-panel overrides, merged into one flow so the main chain only needs one more `combine` step. */
    private data class TestOverrides(
        val xOffsetMm: Float?,
        val yOffsetMm: Float?,
        val boardSizeMm: Float?,
        val z0Meters: Float?,
    )

    private val testOverridesFlow = combine(
        xOffsetOverrideFlow,
        yOffsetOverrideFlow,
        boardSizeOverrideFlow,
        z0OverrideFlow,
    ) { xOffset, yOffset, boardSize, z0 -> TestOverrides(xOffset, yOffset, boardSize, z0) }

    val uiState: StateFlow<AimUiState> = combine(
        trackingService.state,
        rangeOverrideFlow,
        cameraContextFlow,
        settingsRepository.settings,
    ) { trackingResult, rangeOverride, cameraContext, settings ->
        CombinedInputs(trackingResult, rangeOverride, cameraContext, settings)
    }
        .combine(rotationVectorProvider.rotation) { inputs, rotationDeviceFrame -> inputs to rotationDeviceFrame }
        .combine(testOverridesFlow) { (inputs, rotationDeviceFrame), testOverrides -> Triple(inputs, rotationDeviceFrame, testOverrides) }
        .combine(driftMonitor.state) { (inputs, rotationDeviceFrame, testOverrides), driftStatus ->
            computeUiState(
                inputs.trackingResult,
                inputs.rangeOverride,
                inputs.cameraContext,
                inputs.settings,
                rotationDeviceFrame,
                testOverrides,
                driftStatus,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AimUiState())

    /** Feeds a camera frame to the active tracking strategy; call from the CameraX analyzer. */
    fun onFrame(image: ImageProxy) {
        val rotationDegrees = image.imageInfo.rotationDegrees
        val gray = matConverter.toGrayMat(image)
        image.close()
        try {
            val rotationDeviceFrame = rotationVectorProvider.rotation.value

            if (pendingZeroRequest) {
                pendingZeroRequest = false
                rotationDeviceFrame?.let {
                    (trackingService as? ZeroableAimTracking)?.setZero(it, rotationDegrees)
                }
                // "영점" doubles as the drift monitor's reference capture — the same "this is the
                // known-good baseline" moment applies to both, and it avoids a second dedicated button.
                driftMonitor.setReference(gray, rotationDegrees, rotationDeviceFrame)
            }

            val boardSizeMm = boardSizeOverrideFlow.value?.toDouble() ?: settingsRepository.settings.value.boardSizeMm
            trackingService.updateMarkerSizeMm(boardSizeMm)

            val cameraId = cameraContextFlow.value?.cameraId
            val intrinsics = cameraId?.let {
                intrinsicsProvider.getIntrinsics(it, gray.cols(), gray.rows(), rotationDegrees)
            }
            val gravityCamera = gravityProvider.gravity.value?.let {
                GravityFrameConverter.toCameraFrame(it, rotationDegrees)
            }

            trackingService.analyze(
                gray,
                rotationDegrees,
                System.currentTimeMillis(),
                intrinsics,
                gravityCamera,
                rotationDeviceFrame,
            )
            driftMonitor.analyze(gray, rotationDegrees, intrinsics, rotationDeviceFrame)
        } finally {
            gray.release()
        }
    }

    fun onCameraReady(cameraId: String) {
        cameraContextFlow.value = CameraContext(cameraId)
    }

    /** [raw] is an absolute aim distance in meters. */
    fun setRangeMeters(raw: Float) {
        rangeOverrideFlow.value = snapRange(raw)
    }

    /** Live test-panel controls — unpersisted, for exercising the aim algorithm (see [TestPanelConfig]). */
    fun setXOffsetMm(raw: Float) {
        xOffsetOverrideFlow.value = raw.coerceIn(TestPanelConfig.MIN_OFFSET_MM, TestPanelConfig.MAX_OFFSET_MM)
    }

    fun setYOffsetMm(raw: Float) {
        yOffsetOverrideFlow.value = raw.coerceIn(TestPanelConfig.MIN_OFFSET_MM, TestPanelConfig.MAX_OFFSET_MM)
    }

    fun setBoardSizeMm(raw: Float) {
        boardSizeOverrideFlow.value = raw.coerceIn(TestPanelConfig.MIN_BOARD_SIZE_MM, TestPanelConfig.MAX_BOARD_SIZE_MM)
    }

    /**
     * Live-adjusts the Z0 seed itself (distinct from [setRangeMeters], which drives the currently
     * displayed range) — also clears [rangeOverrideFlow] so the effect is immediately visible, the
     * same trick [updateZ0Meters] already uses for the persisted path.
     */
    fun setZ0MetersLive(raw: Float) {
        z0OverrideFlow.value = raw.coerceIn(RangeSliderConfig.MIN_METERS, RangeSliderConfig.MAX_METERS)
        rangeOverrideFlow.value = null
    }

    fun updateBoardSizeMm(boardSizeMm: Double) {
        settingsRepository.updateBoardSizeMm(boardSizeMm)
        boardSizeOverrideFlow.value = null
    }

    fun updateXOffsetMm(xOffsetMm: Double) {
        settingsRepository.updateXOffsetMm(xOffsetMm)
        xOffsetOverrideFlow.value = null
    }

    fun updateYOffsetMm(yOffsetMm: Double) {
        settingsRepository.updateYOffsetMm(yOffsetMm)
        yOffsetOverrideFlow.value = null
    }

    fun updateZ0Meters(z0Meters: Float) {
        settingsRepository.updateZ0Meters(z0Meters)
        // Z0 is the slider's starting point, so changing it should move the slider there.
        rangeOverrideFlow.value = null
        z0OverrideFlow.value = null
    }

    /** A tap on the strategy toggle commits immediately — unlike X/Y/D/Z0, there's no live drag to preview. */
    fun setStrategy(strategy: AimStrategy) {
        settingsRepository.updateStrategy(strategy)
    }

    /**
     * Always (re)captures the drift monitor's reference frame; additionally zeroes the active
     * strategy if it's [ZeroableAimTracking] (currently: no-marker mode) — a no-op there otherwise.
     */
    fun onZeroButtonPressed() {
        pendingZeroRequest = true
    }

    override fun onCleared() {
        gravityProvider.stop()
        rotationVectorProvider.stop()
        motionLogger.stop()
    }

    /**
     * Records a "조준 완료" capture event — [captureId] is shared with the photo file's name (see
     * [dev.sangjun.mobileaim.app.ui.MainScreen]'s shutter button) so the two can be matched up later.
     */
    fun logCaptureSnapshot(captureId: String) {
        val state = uiState.value
        captureLogWriter.append(
            CaptureRow(
                captureId = captureId,
                timestampMillis = System.currentTimeMillis(),
                strategy = settingsRepository.settings.value.strategy.name,
                isMarkerDetected = state.isMarkerDetected,
                rangeMeters = state.rangeMeters,
                xOffsetMm = state.xOffsetMm,
                yOffsetMm = state.yOffsetMm,
                accel = motionLogger.latestAccel.value,
                gyro = motionLogger.latestGyro.value,
            ),
        )
    }

    private fun computeUiState(
        trackingResult: TrackingResult,
        rangeOverride: Float?,
        cameraContext: CameraContext?,
        settings: MarkerSettings,
        rotationDeviceFrame: DoubleArray?,
        testOverrides: TestOverrides,
        driftStatus: DriftStatus,
    ): AimUiState {
        val effectiveZ0Meters = testOverrides.z0Meters ?: settings.z0Meters
        val rangeMeters = snapRange(rangeOverride ?: effectiveZ0Meters)
        val xOffsetMm = testOverrides.xOffsetMm?.toDouble() ?: settings.xOffsetMm
        val yOffsetMm = testOverrides.yOffsetMm?.toDouble() ?: settings.yOffsetMm
        val boardSizeMm = testOverrides.boardSizeMm?.toDouble() ?: settings.boardSizeMm
        val target = MarkerConfig.targetLocalPointMm(rangeMeters, xOffsetMm, yOffsetMm)
        val intrinsics = cameraContext?.let {
            intrinsicsProvider.getIntrinsics(
                it.cameraId,
                trackingResult.frameWidth,
                trackingResult.frameHeight,
                trackingResult.rotationDegrees,
            )
        }

        // Not a real detection this frame — but rather than blank the reticle, keep re-projecting
        // a pose so aiming isn't interrupted by a brief miss: predicted forward from how the phone
        // itself has rotated since the last real fix (see GyroPosePredictor), falling back to a
        // plain freeze when that isn't possible. Only once the miss has gone on for a while do we
        // actually forget the pose, so a later re-detection isn't biased toward a stale orientation.
        // The warning badge and last-known visualization points follow that same "forget" moment —
        // during a brief gap the UI stays fully quiet (no badge, points still shown), so it doesn't
        // flicker on every dropped frame; only once the pose is actually given up on does the UI
        // admit it.
        fun stale(message: String): AimUiState {
            val missDurationMillis = lastDetectionTimestampMillis?.let { trackingResult.timestampMillis - it }
            if (missDurationMillis != null && PoseMemoryPolicy.shouldForgetPose(missDurationMillis)) {
                reticleSmoother.reset()
                poseSmoother.reset()
                trackingService.reset()
                lastDetectionTimestampMillis = null
                lastVisionRotationDeviceFrame = null
                lastVisualizationPoints = emptyList()
            }
            val recentlyTracking = lastDetectionTimestampMillis != null
            val predictedPose = predictStalePose(rotationDeviceFrame, trackingResult.rotationDegrees)
            val frozenPoint = if (predictedPose != null && intrinsics != null) {
                PoseProjector.projectLocalPoint(predictedPose, intrinsics, target)
            } else {
                null
            }
            val ladder = if (predictedPose != null && intrinsics != null) {
                projectLadder(predictedPose, intrinsics, xOffsetMm, yOffsetMm)
            } else {
                emptyList()
            }
            return AimUiState(
                isMarkerDetected = false,
                reticleImagePoint = frozenPoint,
                markerCorners = if (recentlyTracking) lastVisualizationPoints else emptyList(),
                rangeReticlePoints = ladder,
                frameWidth = trackingResult.frameWidth,
                frameHeight = trackingResult.frameHeight,
                rangeMeters = rangeMeters,
                xOffsetMm = xOffsetMm,
                yOffsetMm = yOffsetMm,
                boardSizeMm = boardSizeMm,
                z0Meters = effectiveZ0Meters,
                errorMessage = if (recentlyTracking) null else message,
                driftStatus = driftStatus,
            )
        }

        val pose = trackingResult.pose
        if (!trackingResult.isTracking || pose == null) return stale(trackingResult.statusMessage ?: "Marker not detected")
        if (intrinsics == null) return stale("Camera calibration unavailable")

        lastDetectionTimestampMillis = trackingResult.timestampMillis
        lastVisionRotationDeviceFrame = rotationDeviceFrame
        lastVisualizationPoints = trackingResult.visualizationPoints
        // Smooth the pose itself, before projecting — the target point can be ~200m away while
        // the marker is a few centimetres across, so smoothing only the final projected pixel
        // damps an already-amplified value. Damping the rotation/translation first is far more
        // effective at killing handheld jitter.
        val smoothedPose = poseSmoother.smooth(pose)

        val projectedRaw = PoseProjector.projectLocalPoint(smoothedPose, intrinsics, target)
            ?: return stale("Unable to project aim point")

        return AimUiState(
            isMarkerDetected = true,
            reticleImagePoint = reticleSmoother.smooth(projectedRaw),
            markerCorners = trackingResult.visualizationPoints,
            rangeReticlePoints = projectLadder(smoothedPose, intrinsics, xOffsetMm, yOffsetMm),
            frameWidth = trackingResult.frameWidth,
            frameHeight = trackingResult.frameHeight,
            rangeMeters = rangeMeters,
            xOffsetMm = xOffsetMm,
            yOffsetMm = yOffsetMm,
            boardSizeMm = boardSizeMm,
            z0Meters = effectiveZ0Meters,
            driftStatus = driftStatus,
        )
    }

    /**
     * Projects every preset range in [RangeSliderConfig.SNAP_POINTS_METERS] against the same
     * [pose], for the ballistic-reticle ladder — the currently selected range gets its own
     * [reticleSmoother]-damped point already; these are raw projections off an already-smoothed
     * pose, which is stable enough for secondary reference marks without a smoother of their own.
     */
    private fun projectLadder(
        pose: PoseEstimate,
        intrinsics: CameraIntrinsics,
        xOffsetMm: Double,
        yOffsetMm: Double,
    ): List<RangeReticlePoint> = RangeSliderConfig.SNAP_POINTS_METERS.mapNotNull { snapMeters ->
        val target = MarkerConfig.targetLocalPointMm(snapMeters, xOffsetMm, yOffsetMm)
        PoseProjector.projectLocalPoint(pose, intrinsics, target)?.let { RangeReticlePoint(snapMeters, it) }
    }

    /**
     * The pose to show while tracking isn't currently live: predicted forward from
     * [poseSmoother]'s last real fix using how the phone has rotated since (see
     * [GyroPosePredictor]), or that same last-fixed pose unchanged (a plain freeze) when gyro
     * prediction is disabled or the needed readings aren't available yet.
     */
    private fun predictStalePose(currentRotationDeviceFrame: DoubleArray?, rotationDegrees: Int): PoseEstimate? {
        val basePose = poseSmoother.current ?: return null
        if (!PoseMemoryPolicy.USE_GYRO_PREDICTION) return basePose
        val oldRotationDeviceFrame = lastVisionRotationDeviceFrame ?: return basePose
        if (currentRotationDeviceFrame == null) return basePose

        val qOld = RotationVectorFrameConverter.toCameraFrame(oldRotationDeviceFrame, rotationDegrees)
        val qNew = RotationVectorFrameConverter.toCameraFrame(currentRotationDeviceFrame, rotationDegrees)
        return GyroPosePredictor.predict(basePose, qOld, qNew)
    }
}
