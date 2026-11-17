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
    fun `hand tremor shows up as rms close to its own`() {
        // 3 Hz tremor of 1 mrad amplitude: rms of a sine is amplitude / sqrt(2).
        val tracker = ShakeTracker()
        var reading = tracker.update(0L, 0f)
        for (i in 1..240) {
            val t = i / 60.0
            reading = tracker.update((t * 1e9).toLong(), (0.001 * sin(2 * PI * 3 * t)).toFloat())
        }

        assertEquals(0.001f / 1.4142f, reading.rmsRad, 0.0001f)
    }

    @Test
    fun `fast vibration is mostly filtered out`() {
        // 25 Hz buzz (desk, keyboard) of 1 mrad amplitude, sampled at 200 Hz: the 10 Hz low-pass keeps under half of it.
        val tracker = ShakeTracker()
        var reading = tracker.update(0L, 0f)
        for (i in 1..800) {
            val t = i / 200.0
            reading = tracker.update((t * 1e9).toLong(), (0.001 * sin(2 * PI * 25 * t)).toFloat())
        }

        assertTrue(reading.rmsRad < 0.5f * 0.001f / 1.4142f)
    }

    @Test
    fun `slow change of aim fades out of the deviation`() {
        // Raise the aim by 5 mrad and hold it: after a few seconds the baseline has caught up.
        val tracker = ShakeTracker()
        tracker.update(0L, 0f)
        var reading = tracker.update(16_666_667L, 0.005f)
        // A tenth of a second later the low-pass has passed the step but the 1 s baseline hasn't.
        for (i in 2..6) reading = tracker.update(i * 16_666_667L, 0.005f)
        assertTrue(reading.deviationRad > 0.004f)
        for (i in 7..300) reading = tracker.update(i * 16_666_667L, 0.005f)

        assertEquals(0f, reading.deviationRad, 0.0001f)
    }

    @Test
    fun `camera elevation is level for an upright phone and up when tilted back`() {
        // Identity: device axes = world axes, so the back camera (-Z) looks straight down.
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertEquals((-PI / 2).toFloat(), cameraElevationRad(identity), 1e-6f)
        // Upright, then tilted back by 0.1 rad about the device X axis: the camera looks 0.1 rad up.
        val c = kotlin.math.cos(0.1).toFloat()
        val s = kotlin.math.sin(0.1).toFloat()
        val tilted = floatArrayOf(1f, 0f, 0f, 0f, -s, -c, 0f, c, -s)
        assertEquals(0.1f, cameraElevationRad(tilted), 1e-5f)
        // Phone standing upright facing the user (device Y up, Z toward the user): camera looks level.
        val upright = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        assertEquals(0f, cameraElevationRad(upright), 1e-6f)
    }

    @Test
    fun `an angle reads as millimetres at 25m`() {
        assertEquals(8f, (0.008f / 25f).toMmAt25m(), 1e-4f)
    }

    @Test
    fun `swinging to a new aim resets at once instead of easing back`() {
        // Hold, then swing the aim up by 30 mrad (well past the 10 mrad re-aim threshold) and hold again.
        val tracker = ShakeTracker()
        for (i in 0..60) tracker.update(i * 16_666_667L, 0f)
        var reading = tracker.update(61 * 16_666_667L, 0.03f)
        assertEquals(0f, reading.deviationRad, 1e-7f)
        for (i in 62..70) reading = tracker.update(i * 16_666_667L, 0.03f)

        assertEquals(0f, reading.deviationRad, 1e-7f)
        assertEquals(0f, reading.rmsRad, 1e-7f)
    }
}
