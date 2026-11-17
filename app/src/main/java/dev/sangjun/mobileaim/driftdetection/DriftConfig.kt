package dev.sangjun.mobileaim.driftdetection

/** Tuning for [dev.sangjun.mobileaim.driftdetection.OrbDriftMonitorService] — needs real-device retuning. */
object DriftConfig {
    const val ORB_FEATURE_COUNT = 500
    const val MIN_GOOD_MATCHES = 8
    const val LOWE_RATIO = 0.75

    /** Median gyro-corrected reprojection residual (px) beyond which the rig is flagged as drifted. */
    const val MAX_RESIDUAL_PX = 6.0
}
