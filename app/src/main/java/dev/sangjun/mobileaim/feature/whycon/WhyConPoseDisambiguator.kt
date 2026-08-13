package dev.sangjun.mobileaim.feature.whycon

import dev.sangjun.mobileaim.config.MarkerConfig
import kotlin.math.sqrt

/**
 * Picks one of [CirclePoseRecovery]'s (up to 2) surviving candidates. [CirclePoseRecovery] already
 * filters to physically-plausible candidates (front of camera, facing the camera); this narrows
 * further by the rig's expected working distance, then falls back to whichever candidate is
 * closest to the previous frame's position for temporal continuity — the same "physical
 * plausibility, then continuity" shape as
 * [dev.sangjun.mobileaim.feature.pose.OpenCvPoseEstimationService]'s candidate selection, minus a
 * gravity tiebreaker (see [WhyConAimTrackingService]'s class doc for why that's deferred).
 */
internal object WhyConPoseDisambiguator {
    fun select(candidates: List<CirclePoseCandidate>, previousPositionMm: DoubleArray?): CirclePoseCandidate? {
        if (candidates.isEmpty()) return null
        val plausible = candidates.filter { distanceMm(it.positionMm) in MarkerConfig.EXPECTED_DISTANCE_RANGE_MM }
        val pool = plausible.ifEmpty { candidates }
        if (pool.size == 1) return pool.first()
        val previous = previousPositionMm ?: return pool.first()
        return pool.minByOrNull { euclidean(it.positionMm, previous) }
    }

    private fun distanceMm(v: DoubleArray): Double = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun euclidean(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
