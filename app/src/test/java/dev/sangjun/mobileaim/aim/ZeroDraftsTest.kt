package dev.sangjun.mobileaim.aim

import dev.sangjun.mobileaim.app.settings.ZoomZero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroDraftsTest {

    @Test
    fun `no draft falls back to the saved zero`() {
        val saved = ZoomZero(0.4f, 0.6f, 2, 1)

        assertEquals(saved, ZeroDrafts().zeroFor("k2c1", 8, saved))
        assertFalse(ZeroDrafts().isUnsaved("k2c1", 8))
    }

    @Test
    fun `a drag becomes an unsaved zero with its clicks reset`() {
        val drafts = ZeroDrafts().drag("k2c1", 8, 0.3f, 0.7f)

        assertEquals(ZoomZero(0.3f, 0.7f, 0, 0), drafts.zeroFor("k2c1", 8, ZoomZero(0.4f, 0.6f, 2, 1)))
        assertTrue(drafts.isUnsaved("k2c1", 8))
    }

    @Test
    fun `clicks start from the saved zero when there is no draft`() {
        val drafts = ZeroDrafts().click("k2c1", 8, ZoomZero(0.4f, 0.6f, 2, 1), elevationDelta = -1, windageDelta = 3)

        assertEquals(ZoomZero(0.4f, 0.6f, 1, 4), drafts.zeroFor("k2c1", 8, null))
    }

    @Test
    fun `clicks start from screen center when nothing is saved`() {
        val drafts = ZeroDrafts().click("k2c1", 5, null, elevationDelta = 2, windageDelta = 0)

        assertEquals(ZoomZero(0.5f, 0.5f, 2, 0), drafts.zeroFor("k2c1", 5, null))
    }

    @Test
    fun `drafts are kept per profile and zoom`() {
        val drafts = ZeroDrafts().drag("k2c1", 8, 0.3f, 0.7f)

        assertFalse(drafts.isUnsaved("k2c1", 7))
        assertFalse(drafts.isUnsaved("airsoft", 8))
    }
}
