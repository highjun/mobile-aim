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
 * "영점" mode (the default) at a zoomed zero with clicks dialed in: the aiming crosshair stays
 * visible so the creep pad has something to move, and the pad shows the click-count readout. See
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
                            zoomLevel = 5,
                            isZeroed = true,
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRoboImage("/tmp/mobileaim_ui_screenshot_creep.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot_creep.png")
    }
}
