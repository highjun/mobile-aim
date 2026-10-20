package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import dev.sangjun.mobileaim.app.settings.ReticleSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sangjun.mobileaim.app.settings.ZoomLevels
import kotlin.math.abs
import kotlinx.coroutines.delay

/** The precise point of aim stays red — matches the ladder/label color used throughout this file. */
private val ReticleDotColor = Color(0xFFFF1B1B)

/** Never lets the forced brightness go all the way to 0 — the screen must stay usable, not black. */
private const val MinBrightness = 0.05f
private const val MaxBrightness = 1f

private const val CreepInitialRepeatDelayMillis = 400L
private const val CreepRepeatIntervalMillis = 100L

/** The two header modes — see [AimOverlay]'s KDoc. */
private enum class AimMode { Adjusting, Shooting }

/**
 * v1: no vision, no pose estimation. Each [dev.sangjun.mobileaim.app.settings.AimProfile] is one
 * gun with a separate zero per zoom level ([AimUiState.zoomLevel]). The header's [ModeSegmentedControl]
 * switches between two modes with a plain tap:
 *
 * - "사격" (shooting, the starting mode): only the reticle — no arrows, no screen gestures, and the
 *   gun/zoom pickers are disabled, so a grip touch can't move or swap the zero mid-string.
 *   Brightness, logging, capture and focus still work. Zeroing groups are shot here too, including
 *   at a zoom with no zero yet (the reticle then sits at screen center).
 * - "조정" (adjusting): dragging moves the current zoom's zero — onto the laser bore-sighter's dot
 *   or straight onto a group of hits — and the [CreepPad] fine-tunes it by clicks (one click = one
 *   square of the 25m zeroing target, see [RadiansPerClick]). While a finger is dragging, the
 *   reticle turns into a plain translucent dot (a single filled blob lines up with a laser dot more
 *   precisely than the ring does). A drag starts from the reticle's current position (relative — a
 *   finger landing elsewhere doesn't teleport it) and resets that zoom's clicks (see
 *   [dev.sangjun.mobileaim.app.settings.ISettingsRepository.setZero]).
 *
 * In both modes [ZeroInfo] under the header's right corner shows the current zoom's clicks and
 * stored zero coordinates.
 */
@Composable
fun AimOverlay(
    uiState: AimUiState,
    onReticleDrag: (xFraction: Float, yFraction: Float) -> Unit,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onZoomChange: (Int) -> Unit,
    isProfileSheetOpen: Boolean,
    onOpenProfileSheet: () -> Unit,
    onDismissProfileSheet: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> String,
    onRenameProfile: (profileId: String, name: String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onCreepUp: () -> Unit,
    onCreepDown: () -> Unit,
    onCreepLeft: () -> Unit,
    onCreepRight: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveZeroDrafts: () -> Unit = {},
    onDiscardZeroDrafts: () -> Unit = {},
    onEnabledZoomLevelsChange: (List<Int>) -> Unit = {},
    onCycleReticleSize: () -> Unit = {},
    maxZoomLevel: Int = ZoomLevels.last,
) {
    var mode by remember { mutableStateOf(AimMode.Shooting) }
    var isSavePromptOpen by remember { mutableStateOf(false) }
    var isZoomPickerOpen by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val currentReticle = rememberUpdatedState(uiState.effectiveReticleXFraction to uiState.effectiveReticleYFraction)
    val isAdjusting = mode == AimMode.Adjusting

    Box(
        modifier = modifier.then(
            if (isAdjusting) {
                Modifier.pointerInput(Unit) {
                    // Relative, not "jump to finger": a finger sliding off a header chip or arrow
                    // used to teleport the zero to wherever it was. Accumulate locally since the
                    // saved zero round-trips through settings asynchronously.
                    var dragX = 0f
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            dragX = currentReticle.value.first
                            dragY = currentReticle.value.second
                            isDragging = true
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        dragX = (dragX + dragAmount.x / size.width).coerceIn(0f, 1f)
                        dragY = (dragY + dragAmount.y / size.height).coerceIn(0f, 1f)
                        onReticleDrag(dragX, dragY)
                    }
                }
            } else {
                Modifier
            },
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(uiState.effectiveReticleXFraction * size.width, uiState.effectiveReticleYFraction * size.height)
            if (isDragging) drawZeroingDot(center) else drawRingCrosshair(center, uiState.reticleSize)
        }

        if (isAdjusting) {
            CreepPad(
                isEnabled = uiState.isCreepAvailable,
                onCreepUp = onCreepUp,
                onCreepDown = onCreepDown,
                onCreepLeft = onCreepLeft,
                onCreepRight = onCreepRight,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.End,
        ) {
        Header(
            mode = mode,
            onModeChange = { next ->
                // Leaving the adjusting mode with unsaved zero edits asks first — see ZeroDrafts.
                if (mode == AimMode.Adjusting && next == AimMode.Shooting && uiState.hasUnsavedZeros) isSavePromptOpen = true else mode = next
            },
            activeProfileName = uiState.activeProfileName,
            onOpenProfileSheet = onOpenProfileSheet,
            isLoggingActive = uiState.isLoggingActive,
            onToggleLogging = onToggleLogging,
            onCaptureClick = onCaptureClick,
            isFocusMode = isFocusMode,
            onToggleFocusMode = onToggleFocusMode,
            brightness = uiState.brightness,
            onBrightnessChange = onBrightnessChange,
            zoomLevel = uiState.zoomLevel,
            onZoomChange = onZoomChange,
            enabledZoomLevels = uiState.enabledZoomLevels.filter { it <= maxZoomLevel },
            onOpenZoomPicker = { isZoomPickerOpen = true },
            reticleSize = uiState.reticleSize,
            onCycleReticleSize = onCycleReticleSize,
        )
        ZeroInfo(
            isZeroed = uiState.isZeroed,
            xFraction = uiState.reticleXFraction,
            yFraction = uiState.reticleYFraction,
            elevationClicks = uiState.elevationClicks,
            windageClicks = uiState.windageClicks,
            isUnsaved = uiState.isZeroUnsaved,
            modifier = Modifier
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.End).asPaddingValues())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        }
    }

    if (isSavePromptOpen) {
        SaveZeroDialog(
            onSave = { onSaveZeroDrafts(); isSavePromptOpen = false; mode = AimMode.Shooting },
            onDiscard = { onDiscardZeroDrafts(); isSavePromptOpen = false; mode = AimMode.Shooting },
            onCancel = { isSavePromptOpen = false },
        )
    }

    if (isZoomPickerOpen) {
        ZoomPickerDialog(
            enabledZoomLevels = uiState.enabledZoomLevels,
            maxZoomLevel = maxZoomLevel,
            onConfirm = { onEnabledZoomLevelsChange(it); isZoomPickerOpen = false },
            onDismiss = { isZoomPickerOpen = false },
        )
    }

    if (isProfileSheetOpen) {
        ProfileSheet(
            profiles = uiState.profiles,
            activeProfileId = uiState.activeProfileId,
            onSelectProfile = onSelectProfile,
            onAddProfile = onAddProfile,
            onRenameProfile = onRenameProfile,
            onDeleteProfile = onDeleteProfile,
            onDismiss = onDismissProfileSheet,
        )
    }
}

/**
 * A full-width translucent bar pinned to the top edge. Left: the [ModeSegmentedControl], then the
 * logging/capture/focus chips (icon-only buttons are hard to read at a glance — a text label under
 * each icon removes the guesswork). Right: the gun (profile) and zoom pickers — disabled while
 * shooting, since changing either swaps the zero in use — and the manual brightness slider.
 */
@Composable
private fun Header(
    mode: AimMode,
    onModeChange: (AimMode) -> Unit,
    activeProfileName: String,
    onOpenProfileSheet: () -> Unit,
    isLoggingActive: Boolean,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    zoomLevel: Int,
    onZoomChange: (Int) -> Unit,
    enabledZoomLevels: List<Int>,
    onOpenZoomPicker: () -> Unit,
    reticleSize: ReticleSize,
    onCycleReticleSize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isShooting = mode == AimMode.Shooting
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RectangleShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSegmentedControl(mode = mode, onModeChange = onModeChange)
                LabeledHeaderButton(
                    icon = if (isLoggingActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (isLoggingActive) "정지" else "로깅",
                    active = isLoggingActive,
                    onClick = onToggleLogging,
                )
                LabeledHeaderButton(
                    icon = Icons.Filled.CameraAlt,
                    label = "캡처",
                    active = false,
                    onClick = onCaptureClick,
                )
                LabeledHeaderButton(
                    icon = Icons.Filled.DoNotDisturbOn,
                    label = "집중",
                    active = isFocusMode,
                    onClick = onToggleFocusMode,
                )
                // Works in both modes — the right size depends on the shooting position (eye-to-screen distance).
                LabeledHeaderButton(
                    icon = Icons.Filled.FilterCenterFocus,
                    label = when (reticleSize) {
                        ReticleSize.Small -> "작게"
                        ReticleSize.Medium -> "보통"
                        ReticleSize.Large -> "크게"
                    },
                    active = false,
                    onClick = onCycleReticleSize,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActiveProfileLabel(name = activeProfileName, isEnabled = !isShooting, onClick = onOpenProfileSheet)
                ZoomButton(
                    zoomLevel = zoomLevel,
                    zoomLevels = enabledZoomLevels,
                    isEnabled = !isShooting,
                    onZoomChange = onZoomChange,
                    onOpenZoomPicker = onOpenZoomPicker,
                )
                BrightnessCapsuleSlider(brightness = brightness, onBrightnessChange = onBrightnessChange)
            }
        }
    }
}

/** Two segments, "조정" | "사격", the selected one filled — a plain tap on either switches modes. */
@Composable
private fun ModeSegmentedControl(
    mode: AimMode,
    onModeChange: (AimMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)),
    ) {
        Segment(
            label = "조정",
            icon = Icons.Filled.ControlCamera,
            isSelected = mode == AimMode.Adjusting,
            onClick = { onModeChange(AimMode.Adjusting) },
        )
        Segment(
            label = "사격",
            icon = Icons.Filled.GpsFixed,
            isSelected = mode == AimMode.Shooting,
            onClick = { onModeChange(AimMode.Shooting) },
        )
    }
}

@Composable
private fun Segment(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 52.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
                maxLines = 1,
            )
        }
    }
}

/**
 * Two small faint lines under the header's right corner: the current zoom's clicks (zero axes
 * left out) and its stored zero coordinates as-is — "—" when that zoom has no zero yet. Kept
 * subtle so it informs without competing with the target.
 */
@Composable
private fun ZeroInfo(
    isZeroed: Boolean,
    xFraction: Float,
    yFraction: Float,
    elevationClicks: Int,
    windageClicks: Int,
    isUnsaved: Boolean,
    modifier: Modifier = Modifier,
) {
    val clicks = listOfNotNull(
        formatClickReadout(elevationClicks, upLabel = "↑", downLabel = "↓"),
        formatClickReadout(windageClicks, upLabel = "→", downLabel = "←"),
    ).joinToString(" ").ifEmpty { "클릭 0" }
    val coordinates = if (isZeroed) "(%.3f, %.3f)".format(xFraction, yFraction) else "—"
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.End,
    ) {
        for (line in listOf(clicks, coordinates)) {
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        if (isUnsaved) {
            Text(text = "저장 안 됨", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC107))
        }
    }
}

/**
 * Names the active gun (profile) inside the same chip styling the other header buttons use — the
 * box plus the trailing [Icons.Filled.ArrowDropDown] read as "this is tappable". Opens
 * [ProfileSheet] on tap; dimmed and inert while shooting.
 */
@Composable
private fun ActiveProfileLabel(
    name: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isEnabled) 0.70f else 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 1f else 0.5f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = "프로파일 목록 열기")
        }
    }
}

/**
 * A single header button (same shape as [LabeledHeaderButton]) showing the current zoom level as
 * its label — tapping it opens a [DropdownMenu] of [ZoomLevels]. A slider needs a free hand to drag
 * precisely, which the shooter doesn't have while holding the gun; a one-tap pick doesn't. Inert
 * while shooting.
 */
@Composable
private fun ZoomButton(
    zoomLevel: Int,
    zoomLevels: List<Int>,
    isEnabled: Boolean,
    onZoomChange: (Int) -> Unit,
    onOpenZoomPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        LabeledHeaderButton(
            icon = Icons.Filled.ZoomIn,
            label = "${zoomLevel}x",
            active = isExpanded,
            isEnabled = isEnabled,
            onClick = { isExpanded = true },
        )
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            for (level in zoomLevels) {
                DropdownMenuItem(
                    text = { Text("${level}x") },
                    onClick = {
                        onZoomChange(level)
                        isExpanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("배율 고르기") },
                onClick = {
                    isExpanded = false
                    onOpenZoomPicker()
                },
            )
        }
    }
}

/**
 * A rounded chip with the icon stacked above a short label — icon-only buttons don't say what
 * they do at a glance, and this is meant to be read from a distance while aiming. [active] drives
 * the same accent-fill treatment the reticle/logging controls already use elsewhere. The label
 * carries the accessible name (via the [Surface]'s clickable semantics), so the icon itself is
 * marked decorative to avoid TalkBack announcing it twice.
 */
@Composable
private fun LabeledHeaderButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = if (isEnabled) 0.70f else 0.35f)
        },
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 1f else 0.5f),
        modifier = modifier.size(width = 56.dp, height = 52.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                // Default line height adds top/bottom leading beyond the glyphs themselves, which
                // throws off the icon+label block's visual centering inside the chip — pin it to
                // the font size so the block's actual ink matches its layout bounds.
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
                maxLines = 1,
            )
        }
    }
}

/**
 * A One UI–style capsule brightness slider: a thick pill-shaped track that fills from the left to
 * show the level, with the sun icon embedded at the start — no separate thumb knob, and no numeric
 * readout (kept visually clean; the fill length itself is the reading). Tapping or dragging
 * anywhere across the track jumps the value straight to that point, which is faster to use
 * one-handed outdoors than dragging a small thumb.
 */
@Composable
private fun BrightnessCapsuleSlider(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fillFraction = ((brightness - MinBrightness) / (MaxBrightness - MinBrightness)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .size(width = 200.dp, height = 32.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                fun onXChanged(x: Float) {
                    val fraction = (x / size.width).coerceIn(0f, 1f)
                    onBrightnessChange(MinBrightness + fraction * (MaxBrightness - MinBrightness))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onXChanged(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        onXChanged(change.position.x)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fillFraction)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        )
        Icon(
            imageVector = Icons.Filled.BrightnessHigh,
            contentDescription = "화면 밝기",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(16.dp),
        )
    }
}

/**
 * A vertical D-pad of four arrow buttons, shown in "조정" mode: each press dials in one click
 * ([RadiansPerClick], one zeroing-target square at 25m) on the current zoom's zero — the net count
 * shows in [ZeroInfo]. Each arrow moves the reticle itself in
 * that direction (not a turret-dial convention) — pressing "위" makes it move up, matching what the
 * shooter sees happen on screen.
 */
@Composable
private fun CreepPad(
    isEnabled: Boolean,
    onCreepUp: () -> Unit,
    onCreepDown: () -> Unit,
    onCreepLeft: () -> Unit,
    onCreepRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CreepArrowButton(Icons.Filled.KeyboardArrowUp, "위로 조정", isEnabled, onCreepUp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CreepArrowButton(Icons.Filled.KeyboardArrowLeft, "왼쪽으로 조정", isEnabled, onCreepLeft)
            Spacer(Modifier.size(48.dp))
            CreepArrowButton(Icons.Filled.KeyboardArrowRight, "오른쪽으로 조정", isEnabled, onCreepRight)
        }
        CreepArrowButton(Icons.Filled.KeyboardArrowDown, "아래로 조정", isEnabled, onCreepDown)
    }
}

/**
 * A single creep-pad arrow: fires [onClick] immediately on press, then repeats it (after an
 * initial pause) for as long as the button stays held — a quick tap still fires exactly once,
 * since [onClick] is driven entirely by [isPressed] rather than the [Surface]'s own click event.
 */
@Composable
private fun CreepArrowButton(
    icon: ImageVector,
    contentDescription: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed, isEnabled) {
        if (!isPressed || !isEnabled) return@LaunchedEffect
        onClick()
        delay(CreepInitialRepeatDelayMillis)
        while (isPressed) {
            onClick()
            delay(CreepRepeatIntervalMillis)
        }
    }

    Surface(
        onClick = {},
        enabled = isEnabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isEnabled) 0.70f else 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

/** `null` when [clicks] is zero — the caller omits that axis from [ZeroInfo] entirely. */
private fun formatClickReadout(clicks: Int, upLabel: String, downLabel: String): String? {
    if (clicks == 0) return null
    val direction = if (clicks > 0) upLabel else downLabel
    return "$direction${abs(clicks)}"
}

/**
 * The aiming reticle. The aim point is where the thin center lines cross — they are 1dp wide and
 * stay the same at every [size], so the reticle never adds aiming error or hides the target (1dp is
 * under one 7mm square at 25m from about 8x up). [size] only scales the ring and the thicker outer
 * ticks, which make the reticle easy to find at different eye-to-screen distances. The thick parts
 * get a thin dark outline so they show against a bright sky as well as dark ground.
 */
private fun DrawScope.drawRingCrosshair(center: Offset, size: ReticleSize) {
    val (ringRadiusDp, tickLengthDp, strokeDp) = when (size) {
        ReticleSize.Small -> Triple(12f, 8f, 1.5f)
        ReticleSize.Medium -> Triple(18f, 14f, 2f)
        ReticleSize.Large -> Triple(26f, 20f, 3f)
    }
    val ringRadius = ringRadiusDp.dp.toPx()
    val tickLength = tickLengthDp.dp.toPx()
    val stroke = strokeDp.dp.toPx()
    val outline = stroke + 1.5.dp.toPx()
    val outlineColor = Color.Black.copy(alpha = 0.6f)
    val directions = listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))

    // outline first, then the red strokes on top
    for ((color, width) in listOf(outlineColor to outline, ReticleDotColor to stroke)) {
        drawCircle(color = color, radius = ringRadius, center = center, style = Stroke(width = width))
        for (d in directions) {
            drawLine(color = color, start = center + d * ringRadius, end = center + d * (ringRadius + tickLength), strokeWidth = width)
        }
    }
    // thin center lines: the aim point is their crossing
    val thin = 1.dp.toPx()
    for (d in directions) {
        drawLine(color = ReticleDotColor, start = center, end = center + d * ringRadius, strokeWidth = thin)
    }
}

/**
 * While a drag is moving the zero, the reticle is this plain translucent dot instead of the
 * ring-and-dot aiming reticle — a single filled blob is easier to overlay precisely on a real laser
 * dot than a thin ring is. See [AimOverlay]'s KDoc.
 */
private fun DrawScope.drawZeroingDot(center: Offset) {
    drawCircle(color = ReticleDotColor.copy(alpha = 0.5f), radius = 20.dp.toPx(), center = center)
}

/**
 * Asked when leaving the adjusting mode with unsaved zero edits (see [ZeroDrafts]): save them, drop
 * them (the saved zeros come back), or stay in the adjusting mode.
 */
@Composable
private fun SaveZeroDialog(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("영점을 저장할까요?") },
        text = { Text("조정한 영점은 저장해야 남습니다. 저장하지 않으면 이전 영점으로 돌아갑니다.") },
        confirmButton = { TextButton(onClick = onSave) { Text("저장") } },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("취소") }
                TextButton(onClick = onDiscard) { Text("저장 안 함") }
            }
        },
    )
}

/**
 * Picks which of [ZoomLevels] the header's zoom picker lists. Levels above [maxZoomLevel] (what the
 * camera can reach) can't be turned on. At least one level stays on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoomPickerDialog(
    enabledZoomLevels: List<Int>,
    maxZoomLevel: Int,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(enabledZoomLevels.filter { it <= maxZoomLevel }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쓸 배율 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이 폰의 최대 배율은 ${maxZoomLevel}배입니다.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (level in ZoomLevels) {
                        val isOn = level in selected
                        FilterChip(
                            selected = isOn,
                            enabled = level <= maxZoomLevel,
                            onClick = { selected = if (isOn) selected - level else selected + level },
                            label = { Text("${level}x") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected.sorted()) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
