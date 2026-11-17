package dev.sangjun.mobileaim.strategy.circlemarker

/**
 * One candidate solution from [CirclePoseRecovery] — a circle alone constrains position (3 DOF)
 * and plane normal (2 DOF) but not rotation about that normal, and the decomposition is two-fold
 * ambiguous, so callers typically see more than one of these per detection.
 */
data class CirclePoseCandidate(
    /** Circle centre in camera space, mm. */
    val positionMm: DoubleArray,
    /** Unit plane normal in camera space, pointing back toward the camera (see [CirclePoseRecovery]). */
    val normal: DoubleArray,
)
