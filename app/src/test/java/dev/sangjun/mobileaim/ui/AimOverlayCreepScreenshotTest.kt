package dev.sangjun.mobileaim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
 * `isCreepMode` is internal state hoisted inside [AimOverlay] itself (not a parameter), so this
 * clicks the "미세조정" header button to reach it, rather than just constructing an [AimUiState].
 * Unlike [AimOverlayBoresightingScreenshotTest]'s translucent dot, the aiming crosshair stays
 * visible here — the whole point of this mode is watching it move via the creep pad. See
 * [AimOverlayScreenshotTest] for why this is its own test class rather than a shared one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class AimOverlayCreepScreenshotTest {
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
                            elevationClicks = 6,
                            windageClicks = -2,
                            isLoggingActive = false,
                            brightness = 0.8f,
                            profiles = DefaultAimProfiles,
                            activeProfileId = profile.id,
                            isCreepAvailable = true,
                        ),
                        onReticleDrag = { _, _ -> },
                        onToggleLogging = {},
                        onCaptureClick = {},
                        isFocusMode = false,
                        onToggleFocusMode = {},
                        onBrightnessChange = {},
                        isProfileSheetOpen = false,
                        onOpenProfileSheet = {},
                        onDismissProfileSheet = {},
                        onSelectProfile = {},
                        onAddProfile = { "" },
                        onRenameProfile = { _, _ -> },
                        onDeleteProfile = {},
                        onUpdateProfileBallistics = { _, _, _, _, _ -> },
                        onCreepUp = {},
                        onCreepDown = {},
                        onCreepLeft = {},
                        onCreepRight = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onNodeWithText("미세조정").performClick()
        composeRule.waitForIdle()

        composeRule.onRoot().captureRoboImage("/tmp/mobileaim_ui_screenshot_creep.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot_creep.png")
    }
}
