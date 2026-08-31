package dev.sangjun.mobileaim.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the interaction between the two ways of moving the active profile's reticle —
 * [SharedPrefsSettingsRepository.updateReticlePosition] (drag-to-zero) and
 * [SharedPrefsSettingsRepository.updateClicks] (creep) — since a bug here reads as "the zero I
 * just set doesn't look right" rather than a crash. See [SharedPrefsSettingsRepositoryMigrationTest]
 * for schema migration instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsSettingsRepositoryTest {

    @Test
    fun `re-dragging the zero resets click adjustments to 0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)
        repository.updateClicks(elevationDelta = 6, windageDelta = -2)

        repository.updateReticlePosition(0.6f, 0.3f)

        val profile = repository.settings.value.activeProfile
        assertEquals(0.6f, profile.reticleXFraction, 0f)
        assertEquals(0.3f, profile.reticleYFraction, 0f)
        assertEquals(0, profile.elevationClicks)
        assertEquals(0, profile.windageClicks)
    }

    @Test
    fun `creep clicks do not touch the reticle position`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)
        repository.updateReticlePosition(0.6f, 0.3f)

        repository.updateClicks(elevationDelta = 3, windageDelta = 1)

        val profile = repository.settings.value.activeProfile
        assertEquals(0.6f, profile.reticleXFraction, 0f)
        assertEquals(0.3f, profile.reticleYFraction, 0f)
        assertEquals(3, profile.elevationClicks)
        assertEquals(1, profile.windageClicks)
    }
}
