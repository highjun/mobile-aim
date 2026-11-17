package dev.sangjun.mobileaim.driftdetection

import dev.sangjun.mobileaim.strategy.IAimTrackingService
import dev.sangjun.mobileaim.strategy.checkermarker.CameraIntrinsics
import kotlinx.coroutines.flow.StateFlow
import org.opencv.core.Mat

/**
 * A secondary safety net, independent of whichever [dev.sangjun.mobileaim.strategy.IAimTrackingService]
 * strategy is active: detects whether the phone has shifted in its mount relative to the barrel
 * since the last zero — recoil is repeated mechanical shock, and a "rigid" 3D-printed mount can
 * still creep or a screw can loosen over many rounds.
 */
@Deprecated("거치대 이탈 감지는 CV 추적에 의존했음 — v1에선 흔들기 테스트로 수동 검증")
interface IDriftMonitorService {
    val state: StateFlow<DriftStatus>

    /** Captures [gray] (and the rig's orientation at that instant) as the "known good" baseline. */
    fun setReference(gray: Mat, rotationDegrees: Int, rotationDeviceFrame: DoubleArray?)

    /**
     * Compares [gray] against the reference, first correcting for how the rig has been *aimed*
     * (rotated) since the reference was set — only the leftover, gyro-unexplained motion is
     * attributed to drift. No-ops (state unchanged) until [setReference] has been called.
     */
    fun analyze(gray: Mat, rotationDegrees: Int, intrinsics: CameraIntrinsics?, rotationDeviceFrame: DoubleArray?)

    fun clearReference()
}
