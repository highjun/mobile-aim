package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.strategy.checkermarker.IPoseEstimationService
import dev.sangjun.mobileaim.strategy.checkermarker.PoseSmoother
import dev.sangjun.mobileaim.utils.GyroPosePredictor

/**
 * Decides when a smoothed pose that hasn't been refreshed by a real detection for a while should be
 * discarded, so the next detection starts a clean re-acquisition instead of being biased toward a
 * stale orientation (see [dev.sangjun.mobileaim.strategy.checkermarker.PoseSmoother.reset],
 * [dev.sangjun.mobileaim.strategy.checkermarker.IPoseEstimationService.reset]).
 *
 * Distinct from "keep showing the frozen reticle," which needs no policy at all — the reticle stays
 * up for as long as a pose exists, full stop; this instead governs the moment the UI as a whole
 * admits the marker is actually lost. [dev.sangjun.mobileaim.aim.AimViewModel] keys the
 * warning badge and the last-known marker corner overlay off the same [shouldForgetPose] check as
 * the pose itself, so a brief miss stays fully quiet (no badge flicker, corners still shown) and
 * only a sustained one surfaces anything to the user.
 * [FORGET_AFTER_MILLIS] trades two risks against each other: too short reintroduces feeding a fresh
 * pose into still-warm smoothers right after a reset (a visible snap-then-crawl); too long risks
 * holding onto a wrong orientation if the rig's physical pose genuinely changed while the board was
 * lost. Time-based (not frame-count-based) so it stays meaningful regardless of camera frame rate.
 */
@Deprecated("deprecated CV 파이프라인의 pose 재획득 정책 — v1은 고정 조준점이라 불필요")
internal object PoseMemoryPolicy {
    fun shouldForgetPose(missDurationMillis: Long): Boolean = missDurationMillis >= FORGET_AFTER_MILLIS

    /** Needs real-device re-tuning once camera focus hunting (the dominant miss cause) is fixed. */
    const val FORGET_AFTER_MILLIS = 2_500L

    /**
     * Safety switch for [dev.sangjun.mobileaim.utils.GyroPosePredictor]: its device-to-
     * camera-frame convention is unverified without a real device (see that class's doc). Flip to
     * `false` to fall back to plain freezing (the pre-gyro behavior) if prediction misbehaves.
     */
    const val USE_GYRO_PREDICTION = true
}
