package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.utils.Point2Px

/**
 * Exponential-moving-average smoothing for the reticle position, to damp frame-to-frame jitter
 * from handheld shake / corner-detection noise. Call [reset] on a detect/re-detect transition so
 * the reticle snaps to the new position instead of slowly drifting in from the last stale spot.
 */
class PointSmoother(private val alpha: Float = DEFAULT_ALPHA) {
    private var smoothed: Point2Px? = null

    fun smooth(raw: Point2Px): Point2Px {
        val previous = smoothed
        val next = if (previous == null) {
            raw
        } else {
            Point2Px(
                x = previous.x + alpha * (raw.x - previous.x),
                y = previous.y + alpha * (raw.y - previous.y),
            )
        }
        smoothed = next
        return next
    }

    fun reset() {
        smoothed = null
    }

    companion object {
        const val DEFAULT_ALPHA = 0.2f
    }
}
