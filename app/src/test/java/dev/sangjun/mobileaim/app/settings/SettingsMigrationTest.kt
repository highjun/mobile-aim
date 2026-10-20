package dev.sangjun.mobileaim.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {

    @Test
    fun `seeds both default profiles with the shared v0 reticle position at 1x`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 0, reticleXFraction = 0.42f, reticleYFraction = 0.61f)

        assertEquals(2, result.profiles.size)
        result.profiles.forEach { profile ->
            assertEquals(ZoomZero(0.42f, 0.61f, 0, 0), profile.zeroFor(1))
            assertEquals(setOf(1), profile.zeros.keys)
        }
    }

    @Test
    fun `an untouched v0 center zero becomes no zero at all`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 0, reticleXFraction = 0.5f, reticleYFraction = 0.5f)

        result.profiles.forEach { assertTrue(it.zeros.isEmpty()) }
    }

    @Test
    fun `ordinal 0 activates the firearm profile, matching AimMode FIREARM`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 0, reticleXFraction = 0.5f, reticleYFraction = 0.5f)

        assertEquals(FirearmProfileId, result.activeProfileId)
    }

    @Test
    fun `ordinal 1 activates the manual gun profile, matching AimMode MANUAL_GUN`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 1, reticleXFraction = 0.5f, reticleYFraction = 0.5f)

        assertEquals(ManualGunProfileId, result.activeProfileId)
    }

    @Test
    fun `out-of-range ordinal falls back to the firearm profile`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 99, reticleXFraction = 0.5f, reticleYFraction = 0.5f)

        assertEquals(FirearmProfileId, result.activeProfileId)
    }

    @Test
    fun `a legacy single zero lands in the zoom slot it was captured at`() {
        val zeros = legacyZeroToZoomZeros(0.47f, 0.52f, zeroZoomRatio = 4f, elevationClicks = 3, windageClicks = -1)

        assertEquals(mapOf(4 to ZoomZero(0.47f, 0.52f, 3, -1)), zeros)
    }

    @Test
    fun `a legacy zero captured between pinch steps rounds to the nearest zoom level, clamped to 1x-25x`() {
        assertEquals(setOf(3), legacyZeroToZoomZeros(0.4f, 0.4f, zeroZoomRatio = 2.6f, elevationClicks = 0, windageClicks = 0).keys)
        assertEquals(setOf(10), legacyZeroToZoomZeros(0.4f, 0.4f, zeroZoomRatio = 9.5f, elevationClicks = 0, windageClicks = 0).keys)
        assertEquals(setOf(25), legacyZeroToZoomZeros(0.4f, 0.4f, zeroZoomRatio = 40f, elevationClicks = 0, windageClicks = 0).keys)
    }

    @Test
    fun `a legacy center zero with clicks still counts as zeroed`() {
        val zeros = legacyZeroToZoomZeros(0.5f, 0.5f, zeroZoomRatio = 1f, elevationClicks = 2, windageClicks = 0)

        assertEquals(mapOf(1 to ZoomZero(0.5f, 0.5f, 2, 0)), zeros)
    }
}
