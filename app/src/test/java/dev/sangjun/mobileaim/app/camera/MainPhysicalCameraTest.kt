package dev.sangjun.mobileaim.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainPhysicalCameraTest {

    /** Galaxy S25 Ultra's logical back camera "0": ultra-wide, main, 3x and 5x telephoto. */
    private val s25UltraLenses = mapOf("2" to 2.2f, "5" to 6.3f, "6" to 7.9f, "7" to 18.6f)

    @Test
    fun `picks the lens matching the logical camera's 1x focal length`() {
        assertEquals("5", selectMainPhysicalCameraId(6.3f, s25UltraLenses))
    }

    @Test
    fun `no match when no lens has the logical focal length`() {
        assertNull(selectMainPhysicalCameraId(5.0f, s25UltraLenses))
    }

    @Test
    fun `no match when the logical focal length is unknown`() {
        assertNull(selectMainPhysicalCameraId(null, s25UltraLenses))
    }

    @Test
    fun `no match for a single-lens camera with no physical lenses`() {
        assertNull(selectMainPhysicalCameraId(6.3f, emptyMap()))
    }
}
