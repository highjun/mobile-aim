package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sangjun.mobileaim.app.settings.AimMode

/** The precise point of aim stays red; the surrounding cross is black so it reads on bright scenes. */
private val ReticleDotColor = Color(0xFFFF1B1B)
private val ReticleCrossColor = Color(0xFF000000)

/** Never lets the forced brightness go all the way to 0 — the screen must stay usable, not black. */
private const val MinBrightness = 0.05f
private const val MaxBrightness = 1f

private const val ManualGunMinDistanceMeters = 5f
private const val ManualGunMaxDistanceMeters = 10f

/**
 * v1: no vision, no pose estimation. There's one shared zero the user drags onto their laser
 * bore-sighter ([uiState.reticleXFraction]/`YFraction` before any drop offset — see
 * [AimViewModel]) — held completely fixed, no camera/pose computation. Dragging only moves it
 * while "영점 조준 모드" ([isZeroingMode]) is on, so an incidental touch mid-shake-test can't
 * nudge it, and it works the same regardless of which [AimMode] is currently showing (both modes
 * draw from this one zero). While zeroing, the reticle switches to a plain translucent dot instead
 * of the full crosshair — easier to line up precisely with an actual laser dot than the crosshair's
 * shape is.
 *
 * [AimMode.FIREARM] displays that zero as-is (real bullets drop too little at short/mid range to
 * matter). [AimMode.MANUAL_GUN] displays it with a computed ballistic-drop offset already added
 * (done upstream in [AimViewModel] via [computeDropOffsetFraction]) — its header controls
 * (distance slider + muzzle velocity) only affect that computation, never the zero itself.
 *
 * The reticle is drawn large so it stays visible from a distance. Everything else lives in the top
 * [Header] bar (this app is landscape-only, but rotates freely between the two landscape
 * orientations so the bar always sits at the physical top — see the manifest's `sensorLandscape`).
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
    onToggleAimMode: () -> Unit,
    onManualGunDistanceChange: (Float) -> Unit,
    onManualGunVelocityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isZeroingMode by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.then(
            if (isZeroingMode) {
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
            drawActiveReticle(
                center = Offset(uiState.reticleXFraction * size.width, uiState.reticleYFraction * size.height),
                isZeroingMode = isZeroingMode,
            )
        }

        Header(
            aimMode = uiState.aimMode,
            onToggleAimMode = onToggleAimMode,
            isZeroingMode = isZeroingMode,
            onToggleZeroingMode = { isZeroingMode = !isZeroingMode },
            isLoggingActive = uiState.isLoggingActive,
            onToggleLogging = onToggleLogging,
            onCaptureClick = onCaptureClick,
            isFocusMode = isFocusMode,
            onToggleFocusMode = onToggleFocusMode,
            brightness = uiState.brightness,
            onBrightnessChange = onBrightnessChange,
            manualGunDistanceMeters = uiState.manualGunDistanceMeters,
            onManualGunDistanceChange = onManualGunDistanceChange,
            manualGunVelocityMps = uiState.manualGunVelocityMps,
            onManualGunVelocityChange = onManualGunVelocityChange,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * A full-width translucent bar pinned to the top edge — the only UI besides the reticle. Aiming
 * controls sit on the left as labeled chips (icon-only buttons are hard to read at a glance —
 * a text label under each icon removes the guesswork); the manual brightness slider sits on the
 * right (there is plenty of room in landscape). In [AimMode.MANUAL_GUN], a distance slider +
 * muzzle-velocity button appear between the two, driving [dev.sangjun.mobileaim.aim.AimViewModel]'s
 * drop calculation. The bar is a plain overlay drawn above the camera preview.
 */
@Composable
private fun Header(
    aimMode: AimMode,
    onToggleAimMode: () -> Unit,
    isZeroingMode: Boolean,
    onToggleZeroingMode: () -> Unit,
    isLoggingActive: Boolean,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    manualGunDistanceMeters: Float,
    onManualGunDistanceChange: (Float) -> Unit,
    manualGunVelocityMps: Float,
    onManualGunVelocityChange: (Float) -> Unit,
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
                    icon = Icons.Filled.SwapHoriz,
                    label = if (aimMode == AimMode.FIREARM) "화기" else "수동",
                    active = aimMode == AimMode.MANUAL_GUN,
                    onClick = onToggleAimMode,
                )
                LabeledHeaderButton(
                    icon = Icons.Filled.GpsFixed,
                    label = "영점",
                    active = isZeroingMode,
                    onClick = onToggleZeroingMode,
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

            if (aimMode == AimMode.MANUAL_GUN) {
                ManualGunControls(
                    distanceMeters = manualGunDistanceMeters,
                    onDistanceChange = onManualGunDistanceChange,
                    velocityMps = manualGunVelocityMps,
                    onVelocityChange = onManualGunVelocityChange,
                )
            }

            BrightnessCapsuleSlider(brightness = brightness, onBrightnessChange = onBrightnessChange)
        }
    }
}

/** Distance slider (drives the drop calc's engagement distance) + a button opening the muzzle-velocity dialog — only shown in [AimMode.MANUAL_GUN]. */
@Composable
private fun ManualGunControls(
    distanceMeters: Float,
    onDistanceChange: (Float) -> Unit,
    velocityMps: Float,
    onVelocityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showVelocityDialog by remember { mutableStateOf(false) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DistanceCapsuleSlider(distanceMeters = distanceMeters, onDistanceChange = onDistanceChange)
        VelocityButton(velocityMps = velocityMps, onClick = { showVelocityDialog = true })
    }

    if (showVelocityDialog) {
        VelocityInputDialog(
            initialVelocityMps = velocityMps,
            onDismiss = { showVelocityDialog = false },
            onConfirm = {
                onVelocityChange(it)
                showVelocityDialog = false
            },
        )
    }
}

/**
 * A capsule slider for the manual gun's engagement distance (5..10m) — unlike [BrightnessCapsuleSlider]
 * this shows the exact value as text, since the drop calculation is sensitive to it and the user
 * needs to read it precisely rather than just eyeball a fill level.
 */
@Composable
private fun DistanceCapsuleSlider(
    distanceMeters: Float,
    onDistanceChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fillFraction = ((distanceMeters - ManualGunMinDistanceMeters) / (ManualGunMaxDistanceMeters - ManualGunMinDistanceMeters)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .width(140.dp)
            .height(32.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                fun onXChanged(x: Float) {
                    val fraction = (x / size.width).coerceIn(0f, 1f)
                    onDistanceChange(ManualGunMinDistanceMeters + fraction * (ManualGunMaxDistanceMeters - ManualGunMinDistanceMeters))
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
        Text(
            text = "%.1fm".format(distanceMeters),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun VelocityButton(velocityMps: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(32.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "${velocityMps.toInt()}m/s",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
            )
        }
    }
}

/** Muzzle velocity is a set-once-per-gun value, not something adjusted per shot, so a dialog (precise numeric entry) fits better than a slider. */
@Composable
private fun VelocityInputDialog(
    initialVelocityMps: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(initialVelocityMps.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("탄속 설정 (m/s)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toFloatOrNull()?.let(onConfirm) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
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
            .width(200.dp)
            .height(32.dp)
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

/** In zeroing mode, a plain translucent dot (easier to overlay precisely on a real laser dot) replaces the crosshair. */
private fun DrawScope.drawActiveReticle(center: Offset, isZeroingMode: Boolean) {
    if (isZeroingMode) {
        drawCircle(color = ReticleDotColor.copy(alpha = 0.5f), radius = 20.dp.toPx(), center = center)
        return
    }

    val gap = 22.dp.toPx()
    val armLength = 34.dp.toPx()
    val stroke = 3.dp.toPx()

    drawLine(ReticleCrossColor, Offset(center.x, center.y - gap - armLength), Offset(center.x, center.y - gap), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x, center.y + gap), Offset(center.x, center.y + gap + armLength), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x - gap - armLength, center.y), Offset(center.x - gap, center.y), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x + gap, center.y), Offset(center.x + gap + armLength, center.y), strokeWidth = stroke)
    drawCircle(color = ReticleDotColor, radius = 6.dp.toPx(), center = center)
}
