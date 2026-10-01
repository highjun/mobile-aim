package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/** The precise point of aim stays red — matches the ladder/label color used throughout this file. */
private val ReticleDotColor = Color(0xFFFF1B1B)

/** Never lets the forced brightness go all the way to 0 — the screen must stay usable, not black. */
private const val MinBrightness = 0.05f
private const val MaxBrightness = 1f

private const val CreepInitialRepeatDelayMillis = 400L
private const val CreepRepeatIntervalMillis = 100L

/**
 * v1: no vision, no pose estimation. Each [dev.sangjun.mobileaim.app.settings.AimProfile] carries
 * its own bore-sighted zero the user drags (or nudges — see [onBoresightUp]) onto their laser
 * bore-sighter, plus a click-based fine adjustment dialed in after a confirmation shot (see
 * [CreepPad]). Those are two separate header buttons/modes, not one toggle, because they show a
 * different reticle:
 *
 * - "정렬" ([isBoresightingMode], boresighting): dragging moves the raw zero — held completely
 *   fixed otherwise, so an incidental touch mid-shake-test can't nudge it. The reticle is always a
 *   plain translucent dot at the raw zero (a single filled blob is easier to line up precisely with
 *   an actual laser dot than the thin outer ring of the aiming reticle would be). The [CreepPad]
 *   arrow pad is also shown here (wired to
 *   [onBoresightUp]/[onBoresightDown]/[onBoresightLeft]/[onBoresightRight] instead of the creep
 *   callbacks) as a finer-grained alternative to dragging — it nudges the same raw zero by one
 *   click's worth of screen distance per press, without touching
 *   [AimUiState.elevationClicks]/[AimUiState.windageClicks] at all, so nothing to read out. Moving
 *   the raw zero, whether by drag or by this pad, resets any click adjustment from a prior "영점"
 *   session back to 0 (see [dev.sangjun.mobileaim.app.settings.ISettingsRepository.updateReticlePosition])
 *   — it was dialed in relative to the zero that just moved, so keeping it would silently offset
 *   the crosshair away from wherever the zero just landed.
 * - "영점" ([isCreepMode], zeroing): shows the normal aiming crosshair (below) so the creep pad's
 *   arrows have something to visibly move — a merged mode would either hide the crosshair the pad
 *   is supposed to move, or let a drag land mid-creep and silently overwrite the zero.
 *
 * The two are mutually exclusive (turning one on turns the other off) since a translucent dot has
 * nothing for creep clicks to visibly move, and the crosshair isn't meant to be dragged directly.
 *
 * Otherwise (neither mode on) the reticle is a ring with a crosshair stroke growing outward from
 * each of the ring's four cardinal points, plus a translucent center dot (see [drawRingCrosshair]).
 * The dot's alpha is what keeps the actual point of aim visible underneath it — the same idea as a
 * real holosight's glass being translucent, just faked digitally instead. The reticle itself is
 * drawn at the *effective* position — zero plus click adjustment (see
 * [AimUiState.effectiveReticleXFraction]/[AimUiState.effectiveReticleYFraction]) — and that's also
 * what's shown (and adjustable via the creep pad) while [isCreepMode] is on.
 *
 * Everything else lives in the top [Header] bar (this app is landscape-only, but rotates freely
 * between the two landscape orientations so the bar always sits at the physical top — see the
 * manifest's `sensorLandscape`). The header's active-profile label (see [ActiveProfileLabel]) opens
 * [ProfileSheet] — [isProfileSheetOpen] is hoisted (not internal state like
 * [isBoresightingMode]/[isCreepMode]) so it can be driven directly in tests.
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
    onZoomChange: (Float) -> Unit,
    isProfileSheetOpen: Boolean,
    onOpenProfileSheet: () -> Unit,
    onDismissProfileSheet: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> String,
    onRenameProfile: (profileId: String, name: String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onUpdateProfileBallistics: (profileId: String, muzzleVelocityMps: Float, zeroDistanceM: Float, hobCm: Float, targetRadiusCm: Float) -> Unit,
    onCreepUp: () -> Unit,
    onCreepDown: () -> Unit,
    onCreepLeft: () -> Unit,
    onCreepRight: () -> Unit,
    onBoresightUp: () -> Unit,
    onBoresightDown: () -> Unit,
    onBoresightLeft: () -> Unit,
    onBoresightRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mutually exclusive: dragging edits the raw zero the crosshair isn't even drawn against
    // (see the dot/crosshair split below), and the creep pad's whole point is to watch the
    // crosshair move — so only one of these is ever on at a time.
    var isBoresightingMode by remember { mutableStateOf(false) }
    var isCreepMode by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.then(
            if (isBoresightingMode) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onReticleDrag(change.position.x / size.width, change.position.y / size.height)
                    }
                }
            } else {
                Modifier
            },
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isBoresightingMode) {
                val zeroCenter = Offset(uiState.reticleXFraction * size.width, uiState.reticleYFraction * size.height)
                drawZeroingDot(zeroCenter)
            } else {
                val effectiveCenter = Offset(
                    uiState.effectiveReticleXFraction * size.width,
                    uiState.effectiveReticleYFraction * size.height,
                )
                drawRingCrosshair(effectiveCenter)
            }
        }

        if (isBoresightingMode || isCreepMode) {
            CreepPad(
                elevationClicks = if (isCreepMode) uiState.elevationClicks else 0,
                windageClicks = if (isCreepMode) uiState.windageClicks else 0,
                isEnabled = uiState.isCreepAvailable,
                onCreepUp = if (isCreepMode) onCreepUp else onBoresightUp,
                onCreepDown = if (isCreepMode) onCreepDown else onBoresightDown,
                onCreepLeft = if (isCreepMode) onCreepLeft else onBoresightLeft,
                onCreepRight = if (isCreepMode) onCreepRight else onBoresightRight,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            )
        }

        Header(
            activeProfileName = uiState.activeProfileName,
            onOpenProfileSheet = onOpenProfileSheet,
            isBoresightingMode = isBoresightingMode,
            onToggleBoresightingMode = {
                isBoresightingMode = !isBoresightingMode
                if (isBoresightingMode) isCreepMode = false
            },
            isCreepMode = isCreepMode,
            onToggleCreepMode = {
                isCreepMode = !isCreepMode
                if (isCreepMode) isBoresightingMode = false
            },
            isLoggingActive = uiState.isLoggingActive,
            onToggleLogging = onToggleLogging,
            onCaptureClick = onCaptureClick,
            isFocusMode = isFocusMode,
            onToggleFocusMode = onToggleFocusMode,
            brightness = uiState.brightness,
            onBrightnessChange = onBrightnessChange,
            zoomRatio = uiState.zoomRatio,
            onZoomChange = onZoomChange,
            modifier = Modifier.align(Alignment.TopCenter),
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
            onUpdateProfileBallistics = onUpdateProfileBallistics,
            onDismiss = onDismissProfileSheet,
        )
    }
}

/**
 * A full-width translucent bar pinned to the top edge — the only UI besides the reticle and (while
 * in either boresighting or creep mode) the creep pad. Aiming controls sit on the left as labeled
 * chips (icon-only buttons are hard to read at a glance — a text label under each icon removes the
 * guesswork); the manual brightness slider sits on the right (there is plenty of room in landscape).
 *
 * "정렬"([isBoresightingMode]) and "영점"([isCreepMode]) are separate buttons rather than one
 * toggle — they're different steps of the zeroing procedure (see [dev.sangjun.mobileaim.aim.AimOverlay]'s
 * KDoc) with different reticle displays, and mixing them meant the crosshair the creep pad is
 * supposed to move wasn't even drawn while the pad was visible.
 */
@Composable
private fun Header(
    activeProfileName: String,
    onOpenProfileSheet: () -> Unit,
    isBoresightingMode: Boolean,
    onToggleBoresightingMode: () -> Unit,
    isCreepMode: Boolean,
    onToggleCreepMode: () -> Unit,
    isLoggingActive: Boolean,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                LabeledHeaderButton(
                    icon = Icons.Filled.GpsFixed,
                    label = "정렬",
                    active = isBoresightingMode,
                    onClick = onToggleBoresightingMode,
                )
                LabeledHeaderButton(
                    icon = Icons.Filled.ControlCamera,
                    label = "영점",
                    active = isCreepMode,
                    onClick = onToggleCreepMode,
                )
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
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActiveProfileLabel(name = activeProfileName, onClick = onOpenProfileSheet)
                ZoomButton(zoomRatio = zoomRatio, onZoomChange = onZoomChange)
                BrightnessCapsuleSlider(brightness = brightness, onBrightnessChange = onBrightnessChange)
            }
        }
    }
}

/**
 * Names the active profile inside the same chip styling the other header buttons use — plain
 * unboxed text here used to read as inert labeling rather than the actual entry point into profile
 * management; the box (plus the trailing [Icons.Filled.ArrowDropDown]) now matches those buttons'
 * affordance for "this is tappable". Opens [ProfileSheet] on tap.
 */
@Composable
private fun ActiveProfileLabel(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
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

/** Discrete zoom presets — a slider needs a free hand to drag precisely, which the shooter doesn't have while holding the gun; a one-tap preset doesn't. */
private val ZoomPresets = listOf(1f, 2f, 4f)

/** "2x" for whole ratios, "2.5x" for in-between ones a pinch can land on. */
private fun formatZoom(zoomRatio: Float): String =
    if (zoomRatio % 1f < 0.05f) "${zoomRatio.toInt()}x" else "%.1fx".format(zoomRatio)

/**
 * A single header button (same shape as [LabeledHeaderButton]) showing the current zoom ratio as
 * its label — tapping it opens a [DropdownMenu] to pick a different [ZoomPresets] value, instead of
 * three always-visible chips that took up header width for a value that rarely changes mid-session.
 */
@Composable
private fun ZoomButton(
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        LabeledHeaderButton(
            icon = Icons.Filled.ZoomIn,
            label = formatZoom(zoomRatio),
            active = isExpanded,
            onClick = { isExpanded = true },
        )
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            for (preset in ZoomPresets) {
                DropdownMenuItem(
                    text = { Text(formatZoom(preset)) },
                    onClick = {
                        onZoomChange(preset)
                        isExpanded = false
                    },
                )
            }
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
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
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
 * A vertical D-pad of four arrow buttons, shown in both "정렬"(boresighting) and "영점"(zeroing)
 * mode with different backing actions:
 * - In "영점" mode, dials in click adjustments after a confirmation shot, moving the *effective*
 *   reticle (the aiming crosshair stays visible so there's something to watch move) — a readout
 *   above the pad shows the net click count per axis, omitted for an axis that's still at zero.
 * - In "정렬" mode, nudges the *raw* zero by one click's worth of screen distance per press
 *   instead — a finer-grained alternative to dragging — without touching the click counters at
 *   all, so there's nothing to read out (see [dev.sangjun.mobileaim.aim.AimOverlay]'s KDoc).
 *
 * Either way, each arrow moves the reticle itself in that direction (not a turret-dial convention)
 * — pressing "위" makes it move up, matching what the shooter sees happen on screen.
 */
@Composable
private fun CreepPad(
    elevationClicks: Int,
    windageClicks: Int,
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
        val readout = listOfNotNull(
            formatClickReadout(elevationClicks, upLabel = "↑", downLabel = "↓"),
            formatClickReadout(windageClicks, upLabel = "→", downLabel = "←"),
        ).joinToString(" ")
        if (readout.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = readout,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
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

/** `null` when [clicks] is zero — the caller omits that axis from the readout entirely rather than showing "0클릭". */
private fun formatClickReadout(clicks: Int, upLabel: String, downLabel: String): String? {
    if (clicks == 0) return null
    val direction = if (clicks > 0) upLabel else downLabel
    return "$direction${abs(clicks)}클릭"
}

/**
 * The aiming reticle: a ring with a crosshair stroke growing outward from each of its four
 * cardinal points, plus a translucent center dot (see [AimOverlay]'s KDoc) — the dot's alpha is
 * what keeps the actual point of aim visible underneath it, standing in for a real holosight's
 * translucent glass. Each stroke starts exactly at the ring's edge (no center gap the way the old
 * crosshair-only reticle had) and extends outward, so the ring reads as the strokes' common origin
 * rather than four independent lines.
 */
private fun DrawScope.drawRingCrosshair(center: Offset) {
    val ringRadius = 18.dp.toPx()
    val ringStrokeWidth = 2.dp.toPx()
    val tickLength = 14.dp.toPx()
    val dotRadius = 6.dp.toPx()
    val dotAlpha = 0.5f

    drawCircle(color = ReticleDotColor, radius = ringRadius, center = center, style = Stroke(width = ringStrokeWidth))
    drawLine(
        color = ReticleDotColor,
        start = Offset(center.x, center.y - ringRadius),
        end = Offset(center.x, center.y - ringRadius - tickLength),
        strokeWidth = ringStrokeWidth,
    )
    drawLine(
        color = ReticleDotColor,
        start = Offset(center.x, center.y + ringRadius),
        end = Offset(center.x, center.y + ringRadius + tickLength),
        strokeWidth = ringStrokeWidth,
    )
    drawLine(
        color = ReticleDotColor,
        start = Offset(center.x - ringRadius, center.y),
        end = Offset(center.x - ringRadius - tickLength, center.y),
        strokeWidth = ringStrokeWidth,
    )
    drawLine(
        color = ReticleDotColor,
        start = Offset(center.x + ringRadius, center.y),
        end = Offset(center.x + ringRadius + tickLength, center.y),
        strokeWidth = ringStrokeWidth,
    )
    drawCircle(color = ReticleDotColor.copy(alpha = dotAlpha), radius = dotRadius, center = center)
}

/**
 * In boresighting mode ("정렬"), the reticle is always this plain translucent dot instead of the
 * ring-and-dot aiming reticle — a single filled blob is easier to overlay precisely on a real laser
 * dot than a thin ring is. See [AimOverlay]'s KDoc.
 */
private fun DrawScope.drawZeroingDot(center: Offset) {
    drawCircle(color = ReticleDotColor.copy(alpha = 0.5f), radius = 20.dp.toPx(), center = center)
}
