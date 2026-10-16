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
 * A zoom level with no zero yet: the reticle sits at screen center labelled "영점 없음", and the
 * "사격" segment is dimmed (shooting stays unavailable until this zoom is zeroed). See
 * [AimOverlayScreenshotTest] for why this is its own test class rather than a shared one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class AimOverlayUnzeroedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureAimOverlayAppearance() {
        composeRule.setContent {
            MobileAimTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    AimOverlay(
                        uiState = AimUiState(
                            reticleXFraction = 0.5f,
                            reticleYFraction = 0.5f,
                            effectiveReticleXFraction = 0.5f,
                            effectiveReticleYFraction = 0.5f,
                            isLoggingActive = false,
                            brightness = 0.8f,
                            profiles = DefaultAimProfiles,
                            activeProfileId = FirearmProfileId,
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

        composeRule.onRoot().captureRoboImage("/tmp/mobileaim_ui_screenshot_unzeroed.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot_unzeroed.png")
    }
}
