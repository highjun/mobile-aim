package dev.sangjun.mobileaim.feature.aim

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeSnapTest {
    @Test
    fun `snaps within threshold of a snap point`() {
        assertEquals(50f, snapRange(52f))
    }

    @Test
    fun `does not snap outside threshold`() {
        assertEquals(55f, snapRange(55f))
    }

    @Test
    fun `clamps above max`() {
        assertEquals(200f, snapRange(210f))
    }

    @Test
    fun `clamps below min`() {
        assertEquals(5f, snapRange(-10f))
    }

    @Test
    fun `snaps at exact boundary of threshold`() {
        assertEquals(150f, snapRange(148f))
    }

    @Test
    fun `min and max are themselves snap points`() {
        assertEquals(5f, snapRange(5f))
        assertEquals(200f, snapRange(200f))
    }

    @Test
    fun `snaps upward to 200 from just below`() {
        assertEquals(200f, snapRange(198f))
    }

    @Test
    fun `leaves midrange values untouched`() {
        assertEquals(75f, snapRange(75f))
        assertEquals(125f, snapRange(125f))
    }
}
