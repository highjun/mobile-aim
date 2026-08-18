package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
 * Everything except the reticle itself lives in the top [Header] (this app is landscape-only).
 */
@Composable
fun AimOverlay(
    uiState: AimUiState,
    onReticleDrag: (xFraction: Float, yFraction: Float) -> Unit,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
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
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(12.dp),
        )
    }
}

/** The only UI besides the reticle itself — landscape top bar, three icon buttons, nothing else. */
@Composable
private fun Header(
    isZeroingMode: Boolean,
    onToggleZeroingMode: () -> Unit,
    isLoggingActive: Boolean,
    onToggleLogging: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val gap = 14.dp.toPx()
    val armLength = 20.dp.toPx()
    val stroke = 2.dp.toPx()

    drawLine(ReticleCrossColor, Offset(center.x, center.y - gap - armLength), Offset(center.x, center.y - gap), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x, center.y + gap), Offset(center.x, center.y + gap + armLength), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x - gap - armLength, center.y), Offset(center.x - gap, center.y), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x + gap, center.y), Offset(center.x + gap + armLength, center.y), strokeWidth = stroke)
    drawCircle(color = ReticleDotColor, radius = 3.dp.toPx(), center = center)
}
