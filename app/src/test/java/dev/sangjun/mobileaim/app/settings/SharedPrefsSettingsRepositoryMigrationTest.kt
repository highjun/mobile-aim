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
        assertEquals(0.44f, settings.activeProfile.reticleXFraction, 0f)
        assertEquals(0.58f, settings.activeProfile.reticleYFraction, 0f)
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
    fun `a fresh install with no stored keys gets the default profiles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val settings = SharedPrefsSettingsRepository(context).settings.value

        assertEquals(DefaultAimProfiles.map { it.id }, settings.profiles.map { it.id })
        assertEquals(FirearmProfileId, settings.activeProfileId)
    }
}
