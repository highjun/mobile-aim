package dev.sangjun.mobileaim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sangjun.mobileaim.aim.AimOverlay
import dev.sangjun.mobileaim.aim.AimUiState
import dev.sangjun.mobileaim.aim.ShakeReading
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
 * "조정" mode (tapped from the default "사격") at a zoomed zero with clicks dialed in: the creep
 * pad appears, and the zero info under the header shows the clicks and stored coordinates. See
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
                            isZeroUnsaved = true,
                            hasUnsavedZeros = true,
                            clickSizeMm = 10f,
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
                        shake = ShakeReading(deviationRad = 0.0005f, rmsRad = 0.00025f),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("조정").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("/tmp/mobileaim_ui_screenshot_creep.png")

        // The chip under the pad opens the click-size picker.
        composeRule.onNodeWithText("1클릭 10mm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1클릭 크기").assertExists()
        composeRule.onAllNodes(isRoot()).onLast().captureRoboImage("/tmp/mobileaim_ui_screenshot_click_size.png")
        composeRule.onNodeWithText("취소").performClick()
        composeRule.waitForIdle()

        // Leaving the adjusting mode with an unsaved zero asks first.
        composeRule.onNodeWithText("사격").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("영점을 저장할까요?").assertExists()
        // The dialog is its own window, so there are two roots now — capture the dialog's.
        composeRule.onAllNodes(isRoot()).onLast().captureRoboImage("/tmp/mobileaim_ui_screenshot_save_prompt.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot_creep.png")
    }
}
