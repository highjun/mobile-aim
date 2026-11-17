package dev.sangjun.mobileaim.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the migration through the real `SharedPreferences`-backed repository (not just the
 * pure [migrateAimModeToProfiles] function) — this is what actually runs against a v0-format store
 * an `allowBackup` restore might drop onto a fresh install.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsSettingsRepositoryMigrationTest {

    @Test
    fun `migrates a v0 store on construction and persists the new schema`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mobileaim_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("aim_mode", 1) // MANUAL_GUN
            .putFloat("reticle_x_fraction", 0.44f)
            .putFloat("reticle_y_fraction", 0.58f)
            .apply()

        val settings = SharedPrefsSettingsRepository(context).settings.value

        assertEquals(2, settings.profiles.size)
        assertEquals(ManualGunProfileId, settings.activeProfileId)
        assertEquals(ZoomZero(0.44f, 0.58f, 0, 0), settings.activeProfile.zeroFor(1))
        assertEquals(1, prefs.getInt("settings_schema_version", 0))
        // Old keys are left in place, not deleted, in case the migration needs to re-run.
        assertTrue(prefs.contains("aim_mode"))
    }

    @Test
    fun `re-reading an already-migrated store does not touch the active profile`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mobileaim_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("aim_mode", 1).putFloat("reticle_x_fraction", 0.3f).putFloat("reticle_y_fraction", 0.3f).apply()

        SharedPrefsSettingsRepository(context) // first read: migrates
        SharedPrefsSettingsRepository(context).setActiveProfile(FirearmProfileId)
        val reloaded = SharedPrefsSettingsRepository(context).settings.value // second read: must not re-migrate over the switch above

        assertEquals(FirearmProfileId, reloaded.activeProfileId)
    }

    @Test
    fun `a fresh install with no stored keys gets the default profiles with 에어소프트 active`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val settings = SharedPrefsSettingsRepository(context).settings.value

        assertEquals(listOf(ManualGunProfileId, FirearmProfileId), settings.profiles.map { it.id })
        assertEquals(listOf("에어소프트", "K2C1"), settings.profiles.map { it.name })
        assertEquals(ManualGunProfileId, settings.activeProfileId)
    }

    /**
     * A v1 profile JSON blob saved before zoom existed has no `zeroZoomRatio` key at all — its zero
     * must land in the 1x slot (zoom didn't exist yet), not crash on a missing key.
     */
    @Test
    fun `reads a pre-zoom v1 profile without a zeroZoomRatio key into the 1x slot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mobileaim_settings", Context.MODE_PRIVATE)
        val profileJson = """
            {"id":"$FirearmProfileId","name":"화기","reticleXFraction":0.45,"reticleYFraction":0.5,
             "elevationClicks":0,"windageClicks":0,"muzzleVelocityMps":920.0,"zeroDistanceM":50.0,
             "hobCm":4.0,"targetRadiusCm":38.0}
        """.trimIndent()
        prefs.edit()
            .putInt("settings_schema_version", 1)
            .putString("profiles", "[$profileJson]")
            .putString("active_profile_id", FirearmProfileId)
            .apply()

        val settings = SharedPrefsSettingsRepository(context).settings.value

        assertEquals(mapOf(1 to ZoomZero(0.45f, 0.5f, 0, 0)), settings.activeProfile.zeros)
    }

    @Test
    fun `reads a single-zero profile saved with zeroZoomRatio into that zoom's slot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mobileaim_settings", Context.MODE_PRIVATE)
        val profileJson = """
            {"id":"$FirearmProfileId","name":"화기","reticleXFraction":0.52,"reticleYFraction":0.41,
             "zeroZoomRatio":4.0,"elevationClicks":3,"windageClicks":-2}
        """.trimIndent()
        prefs.edit()
            .putInt("settings_schema_version", 1)
            .putString("profiles", "[$profileJson]")
            .putString("active_profile_id", FirearmProfileId)
            .apply()

        val settings = SharedPrefsSettingsRepository(context).settings.value

        assertEquals(mapOf(4 to ZoomZero(0.52f, 0.41f, 3, -2)), settings.activeProfile.zeros)
    }
}
