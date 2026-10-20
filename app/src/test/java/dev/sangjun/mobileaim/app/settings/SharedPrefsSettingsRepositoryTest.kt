package dev.sangjun.mobileaim.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the interaction between the two ways of moving a zoom's zero —
 * [SharedPrefsSettingsRepository.setZero] (drag) and [SharedPrefsSettingsRepository.updateClicks]
 * (creep) — and that zooms keep separate zeros, since a bug here reads as "the zero I just set
 * doesn't look right" rather than a crash. See [SharedPrefsSettingsRepositoryMigrationTest] for
 * schema migration instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsSettingsRepositoryTest {

    @Test
    fun `re-dragging the zero resets that zoom's click adjustments to 0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)
        repository.updateClicks(zoomLevel = 5, elevationDelta = 6, windageDelta = -2)

        repository.setZero(zoomLevel = 5, xFraction = 0.6f, yFraction = 0.3f)

        assertEquals(ZoomZero(0.6f, 0.3f, 0, 0), repository.settings.value.activeProfile.zeroFor(5))
    }

    @Test
    fun `creep clicks do not touch the dragged position`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)
        repository.setZero(zoomLevel = 5, xFraction = 0.6f, yFraction = 0.3f)

        repository.updateClicks(zoomLevel = 5, elevationDelta = 3, windageDelta = 1)

        assertEquals(ZoomZero(0.6f, 0.3f, 3, 1), repository.settings.value.activeProfile.zeroFor(5))
    }

    @Test
    fun `each zoom keeps its own zero`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)
        repository.setZero(zoomLevel = 5, xFraction = 0.6f, yFraction = 0.3f)

        repository.setZero(zoomLevel = 6, xFraction = 0.4f, yFraction = 0.7f)

        val profile = repository.settings.value.activeProfile
        assertEquals(ZoomZero(0.6f, 0.3f, 0, 0), profile.zeroFor(5))
        assertEquals(ZoomZero(0.4f, 0.7f, 0, 0), profile.zeroFor(6))
        assertNull(profile.zeroFor(7))
    }

    @Test
    fun `clicking at an unzeroed zoom starts from screen center`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)

        repository.updateClicks(zoomLevel = 8, elevationDelta = -1, windageDelta = 0)

        assertEquals(ZoomZero(0.5f, 0.5f, -1, 0), repository.settings.value.activeProfile.zeroFor(8))
    }

    @Test
    fun `per-zoom zeros survive a reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SharedPrefsSettingsRepository(context).apply {
            setZero(zoomLevel = 5, xFraction = 0.6f, yFraction = 0.3f)
            updateClicks(zoomLevel = 5, elevationDelta = 2, windageDelta = -1)
        }

        val reloaded = SharedPrefsSettingsRepository(context).settings.value.activeProfile

        assertEquals(mapOf(5 to ZoomZero(0.6f, 0.3f, 2, -1)), reloaded.zeros)
    }

    @Test
    fun `selected zoom level survives a reload so its zero shows after a restart`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SharedPrefsSettingsRepository(context).apply {
            updateZoomLevel(8)
            setZero(zoomLevel = 8, xFraction = 0.55f, yFraction = 0.45f)
        }

        val reloaded = SharedPrefsSettingsRepository(context).settings.value

        assertEquals(8, reloaded.zoomLevel)
        assertEquals(ZoomZero(0.55f, 0.45f), reloaded.activeProfile.zeroFor(reloaded.zoomLevel))
    }

    @Test
    fun `zoom level is clamped to the offered range`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)

        repository.updateZoomLevel(30)

        assertEquals(ZoomLevels.last, repository.settings.value.zoomLevel)
    }

    @Test
    fun `saveZero writes to the given profile, not the active one`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)
        val other = repository.settings.value.profiles.first { it.id != repository.settings.value.activeProfileId }

        repository.saveZero(other.id, 8, ZoomZero(0.4f, 0.6f, -40, -40))

        val reloaded = SharedPrefsSettingsRepository(context).settings.value
        assertEquals(ZoomZero(0.4f, 0.6f, -40, -40), reloaded.profiles.first { it.id == other.id }.zeroFor(8))
        assertNull(reloaded.activeProfile.zeroFor(8))
    }

    @Test
    fun `enabled zoom levels survive a reload, cleaned and sorted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SharedPrefsSettingsRepository(context).updateEnabledZoomLevels(listOf(10, 1, 5, 5, 30))

        assertEquals(listOf(1, 5, 10), SharedPrefsSettingsRepository(context).settings.value.enabledZoomLevels)
    }

    @Test
    fun `an empty zoom level list is ignored`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SharedPrefsSettingsRepository(context)

        repository.updateEnabledZoomLevels(emptyList())

        assertEquals(DefaultEnabledZoomLevels, repository.settings.value.enabledZoomLevels)
    }

    @Test
    fun `reticle size survives a reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SharedPrefsSettingsRepository(context).updateReticleSize(ReticleSize.Large)

        assertEquals(ReticleSize.Large, SharedPrefsSettingsRepository(context).settings.value.reticleSize)
    }
}
