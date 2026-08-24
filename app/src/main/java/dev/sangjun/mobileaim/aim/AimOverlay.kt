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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp

/** The precise point of aim stays red; the surrounding cross is black so it reads on bright scenes. */
private val ReticleDotColor = Color(0xFFFF1B1B)
private val ReticleCrossColor = Color(0xFF000000)

/** Never lets the forced brightness go all the way to 0 — the screen must stay usable, not black. */
private const val MinBrightness = 0.05f
private const val MaxBrightness = 1f

/**
 * v1: the reticle is wherever the user last dragged it ([uiState.reticleXFraction]/`YFraction`,
 * a 0..1 fraction of this Composable's own size) — held completely fixed, no camera/pose
 * computation. Dragging only moves it while "영점 조준 모드" ([isZeroingMode]) is on, so an
 * incidental touch mid-shake-test can't nudge it — find the laser dot on the real target, turn
 * zeroing mode on, drag the reticle onto it, then turn zeroing mode back off.
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
            drawActiveReticle(Offset(uiState.reticleXFraction * size.width, uiState.reticleYFraction * size.height))
        }

        Header(
            isZeroingMode = isZeroingMode,
            onToggleZeroingMode = { isZeroingMode = !isZeroingMode },
            isLoggingActive = uiState.isLoggingActive,
            onToggleLogging = onToggleLogging,
            onCaptureClick = onCaptureClick,
            isFocusMode = isFocusMode,
            onToggleFocusMode = onToggleFocusMode,
            brightness = uiState.brightness,
            onBrightnessChange = onBrightnessChange,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * A full-width translucent bar pinned to the top edge — the only UI besides the reticle. Aiming
 * controls sit on the left as labeled chips (icon-only buttons are hard to read at a glance —
 * a text label under each icon removes the guesswork); the manual brightness slider sits on the
 * right (there is plenty of room in landscape). The bar is a plain overlay drawn above the camera
 * preview.
 */
@Composable
private fun Header(
    isZeroingMode: Boolean,
    onToggleZeroingMode: () -> Unit,
    isLoggingActive: Boolean,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
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

            BrightnessCapsuleSlider(brightness = brightness, onBrightnessChange = onBrightnessChange)
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

private fun DrawScope.drawActiveReticle(center: Offset) {
    val gap = 22.dp.toPx()
    val armLength = 34.dp.toPx()
    val stroke = 3.dp.toPx()

    drawLine(ReticleCrossColor, Offset(center.x, center.y - gap - armLength), Offset(center.x, center.y - gap), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x, center.y + gap), Offset(center.x, center.y + gap + armLength), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x - gap - armLength, center.y), Offset(center.x - gap, center.y), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x + gap, center.y), Offset(center.x + gap + armLength, center.y), strokeWidth = stroke)
    drawCircle(color = ReticleDotColor, radius = 6.dp.toPx(), center = center)
}
