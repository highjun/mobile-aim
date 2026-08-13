package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sangjun.mobileaim.app.TestPanelConfig
import dev.sangjun.mobileaim.strategy.AimStrategy
import dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig
import dev.sangjun.mobileaim.utils.Point2Px
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** The precise point of aim stays red; the surrounding cross is black so it reads on bright scenes. */
private val ReticleDotColor = Color(0xFFFF1B1B)
private val ReticleCrossColor = Color(0xFF000000)

/** The ballistic-reticle ladder's non-active rungs — dimmer than the active reticle, still visible. */
private val LadderHashColor = Color(0xFFBDBDBD)

/** Fixed instead of theme-derived so it reads as a warning color regardless of the active theme. */
private val WarningIconColor = Color(0xFFFFC107)

/** Distinct from [WarningIconColor] — a drifted mount is a different kind of problem than a lost marker. */
private val DriftWarningColor = Color(0xFFFF5252)

@Composable
fun AimOverlay(
    uiState: AimUiState,
    strategy: AimStrategy,
    onRangeChange: (Float) -> Unit,
    onSettingsClick: () -> Unit,
    onStrategyChange: (AimStrategy) -> Unit,
    onXYOffsetChange: (xMm: Float, yMm: Float) -> Unit,
    onBoardSizeChange: (Float) -> Unit,
    onZ0Change: (Float) -> Unit,
    onZeroClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val markerColor = MaterialTheme.colorScheme.primary
    var showTestPanel by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Always rendered, detected or not: a stale-but-frozen reticle (see AimViewModel) is still
        // worth showing rather than blanking the screen on every brief detection gap.
        TrackingCanvas(
            uiState = uiState,
            markerColor = markerColor,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TopBar(
                rangeMeters = uiState.rangeMeters,
                onRangeChange = onRangeChange,
                onSettingsClick = onSettingsClick,
                showTestPanel = showTestPanel,
                onToggleTestPanel = { showTestPanel = !showTestPanel },
            )
            val errorMessage = uiState.errorMessage
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                WarningBadge(
                    text = errorMessage,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
            // Independent of the marker-lost warning above — a rig that's drifted can still be
            // actively tracking a marker just fine, so this needs its own, visually distinct badge
            // rather than sharing errorMessage's slot.
            if (uiState.driftStatus.isDrifting) {
                Spacer(modifier = Modifier.height(6.dp))
                DriftWarningBadge(
                    residualPx = uiState.driftStatus.medianResidualPx,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
            if (showTestPanel) {
                Spacer(modifier = Modifier.height(6.dp))
                TestPanel(
                    strategy = strategy,
                    onStrategyChange = onStrategyChange,
                    onZeroClick = onZeroClick,
                    xOffsetMm = uiState.xOffsetMm,
                    yOffsetMm = uiState.yOffsetMm,
                    boardSizeMm = uiState.boardSizeMm,
                    z0Meters = uiState.z0Meters,
                    onXYOffsetChange = onXYOffsetChange,
                    onBoardSizeChange = onBoardSizeChange,
                    onZ0Change = onZ0Change,
                )
            }
        }

        // Deliberately separate from the small TopBar icons — this is the one action that has to
        // be hittable without looking, mid-field, so it gets its own large hit target.
        ShutterButton(
            onClick = onCaptureClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(bottom = 16.dp),
        )
    }
}

/** A classic camera shutter look (white ring + filled disc) rather than a stock icon. */
@Composable
private fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .border(3.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun TrackingCanvas(uiState: AimUiState, markerColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (uiState.frameWidth <= 0 || uiState.frameHeight <= 0) return@Canvas

        // Mirrors PerceptionOverlay's FILL_CENTER crop-compensation math (BattleSight). Assumes
        // PreviewView.scaleType == FILL_CENTER.
        val scale = max(size.width / uiState.frameWidth, size.height / uiState.frameHeight)
        val displayedWidth = uiState.frameWidth * scale
        val displayedHeight = uiState.frameHeight * scale
        val offsetX = (size.width - displayedWidth) / 2f
        val offsetY = (size.height - displayedHeight) / 2f
        fun map(point: Point2Px) = Offset(offsetX + point.x * scale, offsetY + point.y * scale)

        drawMarkerRecognition(uiState.markerCorners, markerColor, ::map)
        drawBallisticReticle(uiState.rangeReticlePoints, uiState.rangeMeters, ::map)
    }
}

/**
 * One dot per detected interior board corner. Unlike a single 4-point tag, the ChArUco board can
 * report anywhere from a handful up to all 9 corners depending on viewing angle, so there is no
 * fixed quad to outline — just mark what was actually found.
 */
private fun DrawScope.drawMarkerRecognition(corners: List<Point2Px>, color: Color, map: (Point2Px) -> Offset) {
    corners.forEach { corner ->
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = map(corner))
    }
}

/**
 * A BDC-style ladder: every preset range in [ladder] is projected against the current pose and
 * drawn as a rung, so the whole spread is visible at once instead of only the currently selected
 * one. Unlike a physical scope's fixed reticle, this reprojects every frame off the live pose, so
 * all the rungs — including the active one — move together as the rig moves. The rung nearest
 * [activeRangeMeters] (the range the top slider currently has selected) gets the full dot+cross
 * treatment; the rest are dim reference hashes.
 */
private fun DrawScope.drawBallisticReticle(
    ladder: List<RangeReticlePoint>,
    activeRangeMeters: Float,
    map: (Point2Px) -> Offset,
) {
    if (ladder.isEmpty()) return
    val active = ladder.minByOrNull { abs(it.rangeMeters - activeRangeMeters) }
    val hashHalfLength = 6.dp.toPx()
    val hashStroke = 1.dp.toPx()

    ladder.forEach { rung ->
        val center = map(rung.imagePoint)
        if (rung === active) {
            drawActiveReticle(center)
        } else {
            drawLine(
                color = LadderHashColor,
                start = Offset(center.x - hashHalfLength, center.y),
                end = Offset(center.x + hashHalfLength, center.y),
                strokeWidth = hashStroke,
            )
        }
    }
}

private fun DrawScope.drawActiveReticle(center: Offset) {
    val gap = 9.dp.toPx()
    val armLength = 13.dp.toPx()
    val stroke = 1.4.dp.toPx()

    drawLine(ReticleCrossColor, Offset(center.x, center.y - gap - armLength), Offset(center.x, center.y - gap), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x, center.y + gap), Offset(center.x, center.y + gap + armLength), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x - gap - armLength, center.y), Offset(center.x - gap, center.y), strokeWidth = stroke)
    drawLine(ReticleCrossColor, Offset(center.x + gap, center.y), Offset(center.x + gap + armLength, center.y), strokeWidth = stroke)
    drawCircle(color = ReticleDotColor, radius = 1.8.dp.toPx(), center = center)
}

@Composable
private fun TopBar(
    rangeMeters: Float,
    onRangeChange: (Float) -> Unit,
    onSettingsClick: () -> Unit,
    showTestPanel: Boolean,
    onToggleTestPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
            tonalElevation = 4.dp,
            modifier = Modifier.weight(1f),
        ) {
            RangeScale(
                rangeMeters = rangeMeters,
                onRangeChange = onRangeChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        FilledIconButton(
            onClick = onToggleTestPanel,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (showTestPanel) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)
                },
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Build, contentDescription = "테스트 컨트롤")
        }

        FilledIconButton(
            onClick = onSettingsClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "설정")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeScale(
    rangeMeters: Float,
    onRangeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val trackFraction = (rangeMeters - RangeSliderConfig.MIN_METERS) /
            (RangeSliderConfig.MAX_METERS - RangeSliderConfig.MIN_METERS)
        Slider(
            value = rangeMeters,
            onValueChange = onRangeChange,
            valueRange = RangeSliderConfig.MIN_METERS..RangeSliderConfig.MAX_METERS,
            thumb = { ChipThumb() },
            track = { ThinTrack(fraction = trackFraction) },
            modifier = Modifier.height(24.dp),
        )
        TickLabels(
            values = RangeSliderConfig.SNAP_POINTS_METERS,
            min = RangeSliderConfig.MIN_METERS,
            max = RangeSliderConfig.MAX_METERS,
            currentValue = rangeMeters,
        )
    }
}

/**
 * Visually a slim vertical pill, wrapped in a much larger transparent box so it stays easy to grab.
 * (Material's Slider also lets you press anywhere along the track.)
 */
@Composable
private fun ChipThumb() {
    Box(
        modifier = Modifier.size(width = 22.dp, height = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Thin track with [snapPoints] marked by notches standing slightly taller than the track, so the
 * detent positions are visible and findable rather than implied only by the labels. An empty
 * [snapPoints] (e.g. for a free-form control like D) just draws the plain line with no notches.
 */
@Composable
private fun ThinTrack(
    fraction: Float,
    min: Float = RangeSliderConfig.MIN_METERS,
    max: Float = RangeSliderConfig.MAX_METERS,
    snapPoints: List<Float> = RangeSliderConfig.SNAP_POINTS_METERS,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val trackHeight = 3.dp.toPx()
        val centerY = size.height / 2f
        val activeWidth = size.width * clamped

        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = trackHeight,
        )
        if (activeWidth > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(activeWidth, centerY),
                strokeWidth = trackHeight,
            )
        }

        val notchHalfHeight = 5.dp.toPx()
        val notchStroke = 1.5.dp.toPx()
        snapPoints.forEach { value ->
            val t = ((value - min) / (max - min)).coerceIn(0f, 1f)
            val x = (t * size.width).coerceIn(notchStroke / 2f, size.width - notchStroke / 2f)
            drawLine(
                color = if (t <= clamped) activeColor else inactiveColor,
                start = Offset(x, centerY - notchHalfHeight),
                end = Offset(x, centerY + notchHalfHeight),
                strokeWidth = notchStroke,
            )
        }
    }
}

/**
 * A vertical variant of the same thin-track/chip-thumb style as [RangeScale], for the test panel's
 * Z0/D controls. Compose's `Slider` has no built-in vertical mode, so this builds an ordinary
 * horizontal one and rotates it — Compose transforms touch input through the same rotation, so
 * dragging still works; only the layout box needs its width/height swapped to match. Up increases
 * the value ([rotationZ] = 270°, i.e. -90°) — confirmed against the running app, not just assumed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalThinSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    length: Dp,
    snapPoints: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        thumb = { ChipThumb() },
        track = {
            ThinTrack(fraction = fraction, min = valueRange.start, max = valueRange.endInclusive, snapPoints = snapPoints)
        },
        // `.layout` must be outermost so the swapped (narrow, tall) size it reports is what the
        // parent Column actually sees — with it innermost (as in an earlier attempt), the fixed
        // .width/.height constraints override that report back to the pre-rotation (wide, short)
        // size, and the Column ends up allocating an oversized box the rotated slider floats in.
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width / 2 - placeable.height / 2),
                        y = -(placeable.height / 2 - placeable.width / 2),
                    )
                }
            }
            .width(length)
            .height(24.dp)
            .graphicsLayer { rotationZ = 270f },
    )
}

/**
 * Places each label centred on its value's position along the track rather than distributing them
 * evenly, since the snap points are unevenly spaced (25/50/100/150/200). The label matching the
 * current value is highlighted.
 */
@Composable
private fun TickLabels(values: List<Float>, min: Float, max: Float, currentValue: Float) {
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Layout(
        content = {
            values.forEach { value ->
                Text(
                    text = value.roundToInt().toString(),
                    color = if (value.roundToInt() == currentValue.roundToInt()) activeColor else idleColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val fraction = if (max > min) ((values[index] - min) / (max - min)).coerceIn(0f, 1f) else 0f
                val centerX = fraction * constraints.maxWidth
                val x = (centerX - placeable.width / 2f).roundToInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                placeable.placeRelative(x = x, y = 0)
            }
        }
    }
}

/**
 * Live X/Y/D/Z0 controls for exercising the aim algorithm — unpersisted (see
 * [AimViewModel.setXOffsetMm] and friends), purely for testing. X/Y move together as one 2D offset
 * rather than two independent sliders since they're the same kind of quantity (a point in the
 * board's local plane); Z0 and D each get their own vertical [VerticalThinSlider], styled to match
 * the top [RangeScale] rather than a stock `Slider`.
 */
@Composable
private fun TestPanel(
    strategy: AimStrategy,
    onStrategyChange: (AimStrategy) -> Unit,
    onZeroClick: () -> Unit,
    xOffsetMm: Double,
    yOffsetMm: Double,
    boardSizeMm: Double,
    z0Meters: Float,
    onXYOffsetChange: (xMm: Float, yMm: Float) -> Unit,
    onBoardSizeChange: (Float) -> Unit,
    onZ0Change: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrategyToggle(strategy = strategy, onStrategyChange = onStrategyChange)
                // No-op unless the active strategy actually uses a zero (currently: 노마커) — always
                // shown rather than conditionally, since AimUiState doesn't carry "is zeroable".
                FilterChip(selected = false, onClick = onZeroClick, label = { Text("영점", style = MaterialTheme.typography.labelSmall) })
            }
            Spacer(modifier = Modifier.height(8.dp))
            TestPanelSliders(
                xOffsetMm = xOffsetMm,
                yOffsetMm = yOffsetMm,
                boardSizeMm = boardSizeMm,
                z0Meters = z0Meters,
                onXYOffsetChange = onXYOffsetChange,
                onBoardSizeChange = onBoardSizeChange,
                onZ0Change = onZ0Change,
            )
        }
    }
}

/** Current-fire-test comparison: a discrete pick, committed immediately (no live-drag preview needed). */
@Composable
private fun StrategyToggle(
    strategy: AimStrategy,
    onStrategyChange: (AimStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AimStrategy.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == strategy,
                onClick = { onStrategyChange(candidate) },
                label = { Text(candidate.label(), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

private fun AimStrategy.label(): String = when (this) {
    AimStrategy.CHARUCO -> "ChArUco"
    AimStrategy.WHYCON -> "WhyCon"
    AimStrategy.NO_MARKER -> "노마커"
}

@Composable
private fun TestPanelSliders(
    xOffsetMm: Double,
    yOffsetMm: Double,
    boardSizeMm: Double,
    z0Meters: Float,
    onXYOffsetChange: (xMm: Float, yMm: Float) -> Unit,
    onBoardSizeChange: (Float) -> Unit,
    onZ0Change: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Z0",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                VerticalThinSlider(
                    value = z0Meters,
                    valueRange = RangeSliderConfig.MIN_METERS..RangeSliderConfig.MAX_METERS,
                    onValueChange = onZ0Change,
                    length = PAD_SIZE,
                    snapPoints = RangeSliderConfig.SNAP_POINTS_METERS,
                )
            }

            Column {
                Text(
                    text = "D",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                VerticalThinSlider(
                    value = boardSizeMm.toFloat(),
                    valueRange = TestPanelConfig.MIN_BOARD_SIZE_MM..TestPanelConfig.MAX_BOARD_SIZE_MM,
                    onValueChange = onBoardSizeChange,
                    length = PAD_SIZE,
                )
            }

            Column {
                Text(
                    text = "X/Y (${xOffsetMm.roundToInt()}, ${yOffsetMm.roundToInt()} mm)",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                XYOffsetPad(
                    xOffsetMm = xOffsetMm.toFloat(),
                    yOffsetMm = yOffsetMm.toFloat(),
                    onOffsetChange = onXYOffsetChange,
                )
            }
        }
    }

/**
 * A square drag pad mapping X/Y position 1:1 to the board's local mm offsets — right is +X, down

 * is +Y, matching [dev.sangjun.mobileaim.strategy.checkermarker.MarkerConfig]'s axis convention exactly, so no sign
 * flip is needed between screen drag direction and the offset it produces.
 */
@Composable
private fun XYOffsetPad(
    xOffsetMm: Float,
    yOffsetMm: Float,
    onOffsetChange: (xMm: Float, yMm: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val min = TestPanelConfig.MIN_OFFSET_MM
    val max = TestPanelConfig.MAX_OFFSET_MM
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val dotColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(PAD_SIZE)
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .pointerInput(Unit) {
                fun report(offset: Offset) {
                    val fracX = (offset.x / size.width).coerceIn(0f, 1f)
                    val fracY = (offset.y / size.height).coerceIn(0f, 1f)
                    onOffsetChange(min + fracX * (max - min), min + fracY * (max - min))
                }
                detectDragGestures(onDragStart = { report(it) }) { change, _ -> report(change.position) }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Centre guide lines so the (0, 0) origin is visible even when the dot has moved away.
            drawLine(
                color = trackColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = trackColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
            )

            val fracX = ((xOffsetMm - min) / (max - min)).coerceIn(0f, 1f)
            val fracY = ((yOffsetMm - min) / (max - min)).coerceIn(0f, 1f)
            drawCircle(
                color = dotColor,
                radius = 5.dp.toPx(),
                center = Offset(fracX * size.width, fracY * size.height),
            )
        }
    }
}

private val PAD_SIZE = 120.dp

/** Small, non-blocking — the reticle itself (frozen at its last known position) stays on screen. */
@Composable
private fun WarningBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = WarningIconColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Same shape as [WarningBadge] but a distinct color (see [DriftWarningColor]) — a different problem, not to be confused with a lost marker. */
@Composable
private fun DriftWarningBadge(residualPx: Float, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = DriftWarningColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "거치대 이탈 의심 (${residualPx.roundToInt()}px)",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
