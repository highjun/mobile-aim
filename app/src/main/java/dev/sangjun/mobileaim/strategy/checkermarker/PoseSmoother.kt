package dev.sangjun.mobileaim.strategy.checkermarker

/**
 * Damps frame-to-frame pose noise before the far aim point is projected.
 *
 * Smoothing only the final projected pixel is not enough: the target sits up to ~200m out while the
 * marker is a few centimetres across, so a fraction of a degree of rotation error is amplified into
 * a large on-screen swing. Damping the pose itself, before that amplification, is what matters.
 *
 * Rotation is smoothed **as a quaternion, hemisphere-aligned against the previous sample**, not
 * component-wise on the Rodrigues vector. A near-frontal marker sits at a rotation angle of ~pi,
 * exactly where the Rodrigues representation is antipodally ambiguous (pi about n == pi about -n),
 * so consecutive frames of a motionless marker can report nearly opposite vectors. Averaging those
 * component-wise collapses toward zero — the identity rotation, i.e. the marker facing away — which
 * measured as ~1600x *worse* jitter than no smoothing at all. See RotationMath.
 */
class PoseSmoother(private val alpha: Float = DEFAULT_ALPHA) {
    private var smoothedQuaternion: DoubleArray? = null
    private var smoothedTvec: DoubleArray? = null

    fun smooth(raw: PoseEstimate): PoseEstimate {
        val rawQuaternion = RotationMath.normalize(RotationMath.rvecToQuaternion(raw.rvec))
        val previousQuaternion = smoothedQuaternion
        val nextQuaternion = if (previousQuaternion == null) {
            rawQuaternion
        } else {
            RotationMath.nlerp(previousQuaternion, rawQuaternion, alpha.toDouble())
        }
        smoothedQuaternion = nextQuaternion

        val previousTvec = smoothedTvec
        val nextTvec = if (previousTvec == null) {
            raw.tvec.copyOf()
        } else {
            DoubleArray(3) { i -> previousTvec[i] + alpha * (raw.tvec[i] - previousTvec[i]) }
        }
        smoothedTvec = nextTvec

        return PoseEstimate(
            rvec = RotationMath.quaternionToRvec(nextQuaternion),
            tvec = nextTvec.copyOf(),
        )
    }

    /** Call on a detect/re-detect transition so the pose snaps rather than drifting in. */
    fun reset() {
        smoothedQuaternion = null
        smoothedTvec = null
    }

    /** The last value returned by [smooth], or null before the first sample / after [reset]. */
    val current: PoseEstimate?
        get() {
            val quaternion = smoothedQuaternion ?: return null
            val tvec = smoothedTvec ?: return null
            return PoseEstimate(RotationMath.quaternionToRvec(quaternion), tvec.copyOf())
        }

    companion object {
        /**
         * Chosen from the ReticleStabilityTest sweep on a static 25mm marker with +/-0.3px corner
         * noise: max frame-to-frame reticle movement was 88.8px unsmoothed, 9.6px at 0.15, 5.9px at
         * 0.08 and 3.9px at 0.05. 0.08 takes most of the benefit while keeping the lag time constant
         * near 0.4s at 30fps, where 0.05 would push it past 0.6s and feel sluggish when aiming.
         */
        const val DEFAULT_ALPHA = 0.08f
    }
}
