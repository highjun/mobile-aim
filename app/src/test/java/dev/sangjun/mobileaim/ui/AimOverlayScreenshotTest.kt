package dev.sangjun.mobileaim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sangjun.mobileaim.aim.AimOverlay
import dev.sangjun.mobileaim.aim.AimUiState
import dev.sangjun.mobileaim.app.settings.DefaultAimProfiles
import dev.sangjun.mobileaim.app.settings.FirearmProfileId
import dev.sangjun.mobileaim.app.ui.theme.MobileAimTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Not a correctness test — a device-free way to eyeball header/reticle/slider styling changes.
 * Renders [AimOverlay] over a plain white box (standing in for the camera feed, which needs a real
 * camera) via Robolectric's native graphics mode, then writes the result to a PNG so it can be
 * opened directly instead of installing the APK on a device just to look at it. Uses Roborazzi's
 * [captureRoboImage] rather than plain compose-ui-test's `captureToImage()`, which times out
 * waiting for a redraw under Robolectric's paused main Looper.
 *
 * Split from [AimOverlayManualGunScreenshotTest]/[AimOverlayZeroingScreenshotTest] into separate
 * classes rather than more `@Test`s here — Robolectric gives each test *class* a fresh
 * `Application`, but within one class Koin's `GlobalContext` (a JVM static) survives across
 * `@Test` methods, so a second `MobileAimApplication.onCreate()` call in the same class throws
 * `KoinAppAlreadyStartedException`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class AimOverlayScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureAimOverlayAppearance() {
        val profile = DefaultAimProfiles.first { it.id == FirearmProfileId }
        composeRule.setContent {
            MobileAimTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    AimOverlay(
                        uiState = AimUiState(
                            reticleXFraction = 0.5f,
                            reticleYFraction = 0.4f,
                            effectiveReticleXFraction = 0.5f,
                            effectiveReticleYFraction = 0.4f,
                            isLoggingActive = false,
                            brightness = 0.8f,
                            profiles = DefaultAimProfiles,
                            activeProfileId = profile.id,
                        ),
                        onReticleDrag = { _, _ -> },
                        onToggleLogging = {},
                        onCaptureClick = {},
                        isFocusMode = false,
                        onToggleFocusMode = {},
                        onBrightnessChange = {},
                        onZoomChange = {},
                        isProfileSheetOpen = false,
                        onOpenProfileSheet = {},
                        onDismissProfileSheet = {},
                        onSelectProfile = {},
                        onAddProfile = { "" },
                        onRenameProfile = { _, _ -> },
                        onDeleteProfile = {},
                        onCreepUp = {},
                        onCreepDown = {},
                        onCreepLeft = {},
                        onCreepRight = {},
                        onBoresightUp = {},
                        onBoresightDown = {},
                        onBoresightLeft = {},
                        onBoresightRight = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRoboImage("/tmp/mobileaim_ui_screenshot.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot.png")
    }
}
