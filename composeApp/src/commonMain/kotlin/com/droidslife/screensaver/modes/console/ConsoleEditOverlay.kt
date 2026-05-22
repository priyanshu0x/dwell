package com.droidslife.screensaver.modes.console

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.ui.GrabbingPointerIcon
import com.droidslife.screensaver.ui.ResizeSEPointerIcon
import com.droidslife.screensaver.widget.api.GridRect
import com.droidslife.screensaver.widget.api.WidgetSize
import kotlin.math.roundToInt

private const val COLS = 12
private const val ROWS = 6
private val GAP = 12.dp
private val PADDING = 32.dp
private val HANDLE_SIZE = 16.dp

/**
 * Overlay drawn on top of the Console grid while edit mode is on. Lets the user
 * drag tiles to move and drag corner handles to resize. Commits each change via
 * [onMove] / [onResize].
 *
 * Pixel math mirrors [ConsoleGrid] exactly so handle/ghost positions line up.
 */
@Composable
fun ConsoleEditOverlay(
    placements: Map<String, GridRect>,
    sizeConstraints: Map<String, WidgetSize>,
    liveDrags: SnapshotStateMap<String, TileLiveDrag>,
    onMove: (id: String, rect: GridRect) -> Unit,
    onResize: (id: String, rect: GridRect) -> Unit,
    hoveredTile: String? = null,
    onHover: (String?) -> Unit = {},
    onFocus: (String) -> Unit = {},
    showBanner: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val paddingPx = with(density) { PADDING.toPx() }
        val gapPx = with(density) { GAP.toPx() }
        val innerW = (constraints.maxWidth - paddingPx * 2f).coerceAtLeast(0f)
        val innerH = (constraints.maxHeight - paddingPx * 2f).coerceAtLeast(0f)
        val cellW = ((innerW - gapPx * (COLS - 1)) / COLS).coerceAtLeast(0f)
        val cellH = ((innerH - gapPx * (ROWS - 1)) / ROWS).coerceAtLeast(0f)
        val stepX = cellW + gapPx
        val stepY = cellH + gapPx

        // One ghost rect per tile being dragged/resized (null when idle).
        val ghosts = remember { mutableStateMapOf<String, GridRect>() }

        // key(id) so reordering placements (focus z-lift) doesn't tear down
        // an in-flight drag gesture by remounting EditTile from scratch.
        placements.forEach { (id, rect) ->
            key(id) {
                val constraint = sizeConstraints[id] ?: WidgetSize()
                EditTile(
                    id = id,
                    rect = rect,
                    constraint = constraint,
                    paddingPx = paddingPx,
                    cellW = cellW,
                    cellH = cellH,
                    gapPx = gapPx,
                    stepX = stepX,
                    stepY = stepY,
                    ghosts = ghosts,
                    liveDrags = liveDrags,
                    placements = placements,
                    isHovered = hoveredTile == id,
                    onHover = onHover,
                    onFocus = onFocus,
                    onMove = onMove,
                    onResize = onResize,
                )
            }
        }

        val ghostDash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f) }
        ghosts.forEach { (_, gr) ->
            val w = gr.cols * cellW + (gr.cols - 1) * gapPx
            val h = gr.rows * cellH + (gr.rows - 1) * gapPx
            val x = paddingPx + gr.col * stepX
            val y = paddingPx + gr.row * stepY
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(
                        width = with(density) { w.toDp() },
                        height = with(density) { h.toDp() },
                    )
                    .drawBehind {
                        val sw = 1.dp.toPx()
                        val r = 12.dp.toPx()
                        drawRoundRect(
                            color = DwellColors.LumenCyan.copy(alpha = 0.35f),
                            topLeft = Offset(sw / 2, sw / 2),
                            size = Size(size.width - sw, size.height - sw),
                            cornerRadius = CornerRadius(r, r),
                            style = Stroke(width = sw, pathEffect = ghostDash),
                        )
                    },
            )
        }

        if (showBanner) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(DwellColors.LumenCyan.copy(alpha = 0.14f))
                    .border(
                        width = 1.dp,
                        color = DwellColors.LumenCyan.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "EDIT LAYOUT  ·  L to exit  ·  drag to move  ·  ⌥ drag to resize",
                    fontSize = 10.sp,
                    letterSpacing = 0.25.sp,
                    color = DwellColors.LumenCyan,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditTile(
    id: String,
    rect: GridRect,
    constraint: WidgetSize,
    paddingPx: Float,
    cellW: Float,
    cellH: Float,
    gapPx: Float,
    stepX: Float,
    stepY: Float,
    ghosts: SnapshotStateMap<String, GridRect>,
    liveDrags: SnapshotStateMap<String, TileLiveDrag>,
    placements: Map<String, GridRect>,
    isHovered: Boolean,
    onHover: (String?) -> Unit,
    onFocus: (String) -> Unit,
    onMove: (id: String, rect: GridRect) -> Unit,
    onResize: (id: String, rect: GridRect) -> Unit,
) {
    val density = LocalDensity.current

    val tileW = rect.cols * cellW + (rect.cols - 1) * gapPx
    val tileH = rect.rows * cellH + (rect.rows - 1) * gapPx
    val tileX = paddingPx + rect.col * stepX
    val tileY = paddingPx + rect.row * stepY

    // Per-tile pixel offset/size delta while a gesture is running. Mirrored
    // into [liveDrags] so ConsoleGrid moves the underlying widget chrome in
    // sync. We snap to grid cells on drag-end (see onDragEnd below).
    val live = liveDrags[id] ?: TileLiveDrag.Zero
    val effectiveX = tileX + live.dx
    val effectiveY = tileY + live.dy
    val effectiveW = (tileW + live.dw).coerceAtLeast(1f)
    val effectiveH = (tileH + live.dh).coerceAtLeast(1f)

    Box(
        modifier = Modifier
            .offset { IntOffset(effectiveX.roundToInt(), effectiveY.roundToInt()) }
            .size(
                width = with(density) { effectiveW.toDp() },
                height = with(density) { effectiveH.toDp() },
            ),
    ) {
        val activeRectForBorder = ghosts[id]
        val isActive = activeRectForBorder != null
        // Red border lives only on settled tiles that overlap a sibling — never
        // while a tile is mid-drag. While dragging the dashed cyan rule wins.
        val settledCollides = !isActive && overlapsAny(id, rect, placements)
        val idleAlpha by animateFloatAsState(
            targetValue = if (isHovered) 0.5f else 0.35f,
            animationSpec = tween(DwellMotion.TileHover, easing = DwellMotion.Emphasized),
            label = "edit-border-alpha",
        )
        val borderColor = when {
            isActive -> DwellColors.LumenCyan
            settledCollides -> DwellColors.StatusError
            else -> DwellColors.LumenCyan.copy(alpha = idleAlpha)
        }
        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Enter) { onHover(id) }
                .onPointerEvent(PointerEventType.Exit) { onHover(null) }
                // Hover stays default — only the active drag swaps in the
                // grabbing fist so the user sees they've grabbed the tile.
                .then(
                    if (isActive) Modifier.pointerHoverIcon(GrabbingPointerIcon)
                    else Modifier,
                )
                .drawBehind {
                    val strokeWidth = 1.5.dp.toPx()
                    val r = 12.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = if (isActive) dashEffect else null,
                        ),
                    )
                }
                .pointerInput(id, rect, stepX, stepY) {
                    detectDragGestures(
                        onDragStart = {
                            ghosts[id] = rect
                            liveDrags[id] = TileLiveDrag.Zero
                            onFocus(id)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val prev = liveDrags[id] ?: TileLiveDrag.Zero
                            val safeStepX = if (stepX > 0f) stepX else 1f
                            val safeStepY = if (stepY > 0f) stepY else 1f
                            // Pixel bounds so the tile can't leave the grid.
                            val minDx = -rect.col * stepX
                            val maxDx = (COLS - rect.cols - rect.col) * stepX
                            val minDy = -rect.row * stepY
                            val maxDy = (ROWS - rect.rows - rect.row) * stepY
                            val newDx = (prev.dx + drag.x).coerceIn(minDx, maxDx)
                            val newDy = (prev.dy + drag.y).coerceIn(minDy, maxDy)
                            liveDrags[id] = prev.copy(dx = newDx, dy = newDy)
                            val dCol = (newDx / safeStepX).roundToInt()
                            val dRow = (newDy / safeStepY).roundToInt()
                            val newCol = (rect.col + dCol).coerceIn(0, COLS - rect.cols)
                            val newRow = (rect.row + dRow).coerceIn(0, ROWS - rect.rows)
                            ghosts[id] = rect.copy(col = newCol, row = newRow)
                        },
                        onDragEnd = {
                            val finalRect = ghosts.remove(id)
                            liveDrags.remove(id)
                            if (finalRect != null && finalRect != rect &&
                                !totallyOverlapsAny(id, finalRect, placements)
                            ) {
                                onMove(id, finalRect)
                            }
                        },
                        onDragCancel = {
                            ghosts.remove(id)
                            liveDrags.remove(id)
                        },
                    )
                },
        ) {
            // Size badge top-left, visible only while actively dragging/resizing.
            val activeRect = ghosts[id]
            if (activeRect != null) {
                Text(
                    text = "${activeRect.cols}×${activeRect.rows}",
                    fontSize = 9.sp,
                    color = DwellColors.LumenCyan,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        // Bottom-right resize handle — L-bracket per mode-mockups-v2.html:
        // 16×16, inset 4px from the tile edge, 2px LumenCyan strokes on the
        // right + bottom edges only, joined by an 8px arc. 0.5 alpha at rest,
        // 1.0 while actively dragging.
        val handlePath = remember { Path() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-4).dp)
                .size(HANDLE_SIZE)
                .pointerHoverIcon(ResizeSEPointerIcon)
                .drawBehind {
                    val sw = 2.dp.toPx()
                    val r = 8.dp.toPx()
                    val color = DwellColors.LumenCyan.copy(
                        alpha = if (isActive) 1f else 0.5f,
                    )
                    handlePath.reset()
                    handlePath.moveTo(size.width - sw / 2, 0f)
                    handlePath.lineTo(size.width - sw / 2, size.height - r)
                    handlePath.arcTo(
                        rect = Rect(
                            left = size.width - 2 * r + sw / 2,
                            top = size.height - 2 * r + sw / 2,
                            right = size.width - sw / 2,
                            bottom = size.height - sw / 2,
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    handlePath.lineTo(0f, size.height - sw / 2)
                    drawPath(
                        path = handlePath,
                        color = color,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                    )
                }
                .pointerInput(id, rect, stepX, stepY, constraint) {
                    detectDragGestures(
                        onDragStart = {
                            ghosts[id] = rect
                            liveDrags[id] = TileLiveDrag.Zero
                            onFocus(id)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val prev = liveDrags[id] ?: TileLiveDrag.Zero
                            val safeStepX = if (stepX > 0f) stepX else 1f
                            val safeStepY = if (stepY > 0f) stepY else 1f
                            val maxColsAtPos = COLS - rect.col
                            val maxCols = minOf(constraint.maxCols, maxColsAtPos)
                            val maxRowsAtPos = ROWS - rect.row
                            val maxRows = minOf(constraint.maxRows, maxRowsAtPos)
                            // Pixel size bounds derived from cols/rows limits.
                            val minPxW = constraint.minCols * cellW + (constraint.minCols - 1) * gapPx - tileW
                            val maxPxW = maxCols * cellW + (maxCols - 1) * gapPx - tileW
                            val minPxH = constraint.minRows * cellH + (constraint.minRows - 1) * gapPx - tileH
                            val maxPxH = maxRows * cellH + (maxRows - 1) * gapPx - tileH
                            val newDw = (prev.dw + drag.x).coerceIn(minPxW, maxPxW)
                            val newDh = (prev.dh + drag.y).coerceIn(minPxH, maxPxH)
                            liveDrags[id] = prev.copy(dw = newDw, dh = newDh)
                            val dCols = (newDw / safeStepX).roundToInt()
                            val dRows = (newDh / safeStepY).roundToInt()
                            val newCols = (rect.cols + dCols).coerceIn(constraint.minCols, maxCols)
                            val newRows = (rect.rows + dRows).coerceIn(constraint.minRows, maxRows)
                            ghosts[id] = rect.copy(cols = newCols, rows = newRows)
                        },
                        onDragEnd = {
                            val finalRect = ghosts.remove(id)
                            liveDrags.remove(id)
                            if (finalRect != null && finalRect != rect &&
                                !totallyOverlapsAny(id, finalRect, placements)
                            ) {
                                onResize(id, finalRect)
                            }
                        },
                        onDragCancel = {
                            ghosts.remove(id)
                            liveDrags.remove(id)
                        },
                    )
                },
        )
    }
}

private fun GridRect.intersects(other: GridRect): Boolean =
    col < other.col + other.cols && other.col < col + cols &&
        row < other.row + other.rows && other.row < row + rows

private fun GridRect.intersectionArea(other: GridRect): Int {
    if (!intersects(other)) return 0
    val ox = minOf(col + cols, other.col + other.cols) - maxOf(col, other.col)
    val oy = minOf(row + rows, other.row + other.rows) - maxOf(row, other.row)
    return ox * oy
}

private fun overlapsAny(selfId: String, rect: GridRect, placements: Map<String, GridRect>): Boolean =
    placements.any { (otherId, other) -> otherId != selfId && rect.intersects(other) }

/**
 * True only when [rect] is completely covered by another tile (or completely
 * covers another). Partial overlaps are allowed — the user can fix them after
 * the drop using the red border as a hint.
 */
private fun totallyOverlapsAny(
    selfId: String,
    rect: GridRect,
    placements: Map<String, GridRect>,
): Boolean = placements.any { (otherId, other) ->
    if (otherId == selfId) return@any false
    val area = rect.intersectionArea(other)
    val rectArea = rect.cols * rect.rows
    val otherArea = other.cols * other.rows
    area == rectArea || area == otherArea
}
