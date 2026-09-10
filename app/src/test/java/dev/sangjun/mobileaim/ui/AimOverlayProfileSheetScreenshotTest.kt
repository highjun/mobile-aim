package dev.sangjun.mobileaim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sangjun.mobileaim.aim.AimOverlay
import dev.sangjun.mobileaim.aim.AimUiState
import dev.sangjun.mobileaim.aim.ProfileSheetTestTag
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
 * Exercises the profile picker sheet (list, then its editor) — [isProfileSheetOpen] is hoisted
 * (see [AimOverlay]'s KDoc), so unlike zeroing mode this doesn't need a header-button click to
 * reach. See [AimOverlayScreenshotTest] for why this is its own test class rather than a shared
 * one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class AimOverlayProfileSheetScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureProfileSheetAppearance() {
        composeRule.setContent {
            MobileAimTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    AimOverlay(
                        uiState = AimUiState(
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
                        isProfileSheetOpen = true,
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
        composeRule.onNodeWithTag(ProfileSheetTestTag).captureRoboImage("/tmp/mobileaim_ui_screenshot_profile_list.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot_profile_list.png")

        composeRule.onNodeWithContentDescription("화기 편집").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileSheetTestTag).captureRoboImage("/tmp/mobileaim_ui_screenshot_profile_editor.png")
        println("SCREENSHOT_SAVED_TO: /tmp/mobileaim_ui_screenshot_profile_editor.png")
    }
}
