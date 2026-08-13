package dev.sangjun.mobileaim.feature.motion

import kotlinx.coroutines.flow.StateFlow

interface ISensorMotionLogger {
    /** `[x, y, z]` of the most recent reading; null until the first one arrives. */
    val latestAccel: StateFlow<DoubleArray?>
    val latestGyro: StateFlow<DoubleArray?>

    /** Begins listening and opens a new session CSV file; safe to call repeatedly. */
    fun start()

    /** Stops listening and closes the session file. */
    fun stop()
}
