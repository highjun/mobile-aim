package dev.sangjun.mobileaim.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {

    @Test
    fun `seeds both default profiles with the shared v0 reticle position`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 0, reticleXFraction = 0.42f, reticleYFraction = 0.61f)

        assertEquals(2, result.profiles.size)
        result.profiles.forEach { profile ->
            assertEquals(0.42f, profile.reticleXFraction, 0f)
            assertEquals(0.61f, profile.reticleYFraction, 0f)
            assertEquals(0, profile.elevationClicks)
            assertEquals(0, profile.windageClicks)
        }
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
    fun `default profiles carry the ballistic parameters new in v1`() {
        val result = migrateAimModeToProfiles(aimModeOrdinal = 0, reticleXFraction = 0.5f, reticleYFraction = 0.5f)

        val firearm = result.profiles.first { it.id == FirearmProfileId }
        assertTrue("firearm zero distance should be positive", firearm.zeroDistanceM > 0f)
        assertTrue("firearm HOB should be positive", firearm.hobCm > 0f)
        assertTrue("firearm target radius should be positive", firearm.targetRadiusCm > 0f)

        val manualGun = result.profiles.first { it.id == ManualGunProfileId }
        assertTrue("manual gun zero distance should be positive", manualGun.zeroDistanceM > 0f)
        assertTrue("manual gun velocity should be far below the firearm's", manualGun.muzzleVelocityMps < firearm.muzzleVelocityMps)
    }
}
