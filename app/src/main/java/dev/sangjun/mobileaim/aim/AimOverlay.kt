package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** The precise point of aim stays red; the surrounding cross is black so it reads on bright scenes. */
private val ReticleDotColor = Color(0xFFFF1B1B)
private val ReticleCrossColor = Color(0xFF000000)

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
 * controls sit on the left; the manual brightness slider on the right (there is plenty of room in
 * landscape). The bar is a plain overlay drawn above the camera preview.
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
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
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
                HeaderIconButton(
                    icon = Icons.Filled.GpsFixed,
                    contentDescription = "영점 조준 모드",
                    active = isZeroingMode,
                    onClick = onToggleZeroingMode,
                )
                HeaderIconButton(
                    icon = if (isLoggingActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isLoggingActive) "로깅 정지" else "로깅 시작",
                    active = isLoggingActive,
                    onClick = onToggleLogging,
                )
                HeaderIconButton(
                    icon = Icons.Filled.CameraAlt,
                    contentDescription = "캡처",
                    active = false,
                    onClick = onCaptureClick,
                )
                HeaderIconButton(
                    icon = Icons.Filled.DoNotDisturbOn,
                    contentDescription = if (isFocusMode) "집중 모드 끄기" else "집중 모드 켜기",
                    active = isFocusMode,
                    onClick = onToggleFocusMode,
                )
            }

            BrightnessControl(brightness = brightness, onBrightnessChange = onBrightnessChange)
        }
    }
}

/** Sun icon + slider (0.05..1) that drives the window's forced brightness — never 0, so the screen never goes fully dark. */
@Composable
private fun BrightnessControl(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.BrightnessHigh,
            contentDescription = "화면 밝기",
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = 0.05f..1f,
            modifier = Modifier.width(200.dp),
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = contentDescription)
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
