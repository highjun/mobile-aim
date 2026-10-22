package dev.sangjun.mobileaim.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRootTest {
    @Test
    fun `legacy videos land in MobileAim videos`() {
        assertEquals("MobileAim/videos", LegacyVideoSubdir)
    }

    @Test
    fun `video file name keeps the video_ prefix and mp4 extension`() {
        assertEquals("video_2026-08-24-10-15-30-482.mp4", videoFileName("2026-08-24-10-15-30-482"))
    }
}
