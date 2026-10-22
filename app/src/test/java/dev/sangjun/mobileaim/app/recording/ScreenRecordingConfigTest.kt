package dev.sangjun.mobileaim.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingConfigTest {

    @Test
    fun `keeps the native size when the encoder accepts it`() {
        // Galaxy S25 Ultra in landscape — both sides are already multiples of 16.
        assertEquals(VideoSize(3120, 1440), chooseRecordingSize(3120, 1440) { _, _ -> true })
    }

    @Test
    fun `aligns each side down to a multiple of 16`() {
        assertEquals(VideoSize(2336, 1072), chooseRecordingSize(2340, 1080) { _, _ -> true })
    }

    @Test
    fun `scales down keeping the aspect ratio until the encoder accepts the size`() {
        val size = chooseRecordingSize(3120, 1440) { width, height -> width <= 1920 && height <= 1088 }
        assertTrue(size.width <= 1920 && size.height <= 1088)
        assertEquals(0, size.width % RecordingSizeAlignment)
        assertEquals(0, size.height % RecordingSizeAlignment)
        assertEquals(3120.0 / 1440.0, size.width.toDouble() / size.height, 0.05)
        // The first accepted step (0.8^3), not something needlessly smaller.
        assertEquals(VideoSize(1584, 736), size)
    }

    @Test
    fun `falls back to the smallest size tried when nothing is supported`() {
        val size = chooseRecordingSize(3120, 1440) { _, _ -> false }
        assertTrue(size.width < 1280)
        assertTrue(size.height >= RecordingSizeAlignment)
    }

    @Test
    fun `clamps the bitrate to the encoder ceiling`() {
        val config = screenRecordingConfig(3120, 1440, maxVideoBitRate = 10_000_000) { _, _ -> true }
        assertEquals(10_000_000, config.videoBitRate)
    }

    @Test
    fun `uses 16 Mbps 60fps and 48 kHz audio by default`() {
        val config = screenRecordingConfig(3120, 1440, maxVideoBitRate = null) { _, _ -> true }
        assertEquals(16_000_000, config.videoBitRate)
        assertEquals(60, config.frameRate)
        assertEquals(48_000, config.audioSamplingRate)
    }
}
