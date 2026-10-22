package dev.sangjun.mobileaim.aim

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeTrackerTest {

    @Test
    fun `steady aim reads no shake`() {
        val tracker = ShakeTracker()
        var reading = tracker.update(0L, 0.01f)
        for (i in 1..120) reading = tracker.update(i * 16_666_667L, 0.01f)

        assertEquals(0f, reading.deviationRad, 1e-7f)
        assertEquals(0f, reading.rmsRad, 1e-7f)
    }

    @Test
    fun `fast tremor shows up as rms close to its own`() {
        // 8 Hz tremor of 1 mrad amplitude: rms of a sine is amplitude / sqrt(2).
        val tracker = ShakeTracker()
        var reading = tracker.update(0L, 0f)
        for (i in 1..240) {
            val t = i / 60.0
            reading = tracker.update((t * 1e9).toLong(), (0.001 * sin(2 * PI * 8 * t)).toFloat())
        }

        assertEquals(0.001f / 1.4142f, reading.rmsRad, 0.0001f)
    }

    @Test
    fun `slow change of aim fades out of the deviation`() {
        // Raise the aim by 5 mrad and hold it: after a few seconds the baseline has caught up.
        val tracker = ShakeTracker()
        tracker.update(0L, 0f)
        var reading = tracker.update(16_666_667L, 0.005f)
        assertTrue(reading.deviationRad > 0.004f)
        for (i in 2..300) reading = tracker.update(i * 16_666_667L, 0.005f)

        assertEquals(0f, reading.deviationRad, 0.0001f)
    }

    @Test
    fun `camera elevation is level for an upright phone and up when tilted back`() {
        // Identity: device axes = world axes, so the back camera (-Z) looks straight down.
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertEquals((-PI / 2).toFloat(), cameraElevationRad(identity), 1e-6f)
        // Phone standing upright facing the user (device Y up, Z toward the user): camera looks level.
        val upright = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        assertEquals(0f, cameraElevationRad(upright), 1e-6f)
    }

    @Test
    fun `an angle reads as millimetres at 25m`() {
        assertEquals(8f, (0.008f / 25f).toMmAt25m(), 1e-4f)
    }
}
