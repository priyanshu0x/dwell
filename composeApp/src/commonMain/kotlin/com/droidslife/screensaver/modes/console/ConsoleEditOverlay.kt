package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
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
    onMove: (id: String, rect: GridRect) -> Unit,
    onResize: (id: String, rect: GridRect) -> Unit,
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

        placements.forEach { (id, rect) ->
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
                placements = placements,
                onMove = onMove,
                onResize = onResize,
            )
        }

        ghosts.forEach { (gid, gr) ->
            val w = gr.cols * cellW + (gr.cols - 1) * gapPx
            val h = gr.rows * cellH + (gr.rows - 1) * gapPx
            val x = paddingPx + gr.col * stepX
            val y = paddingPx + gr.row * stepY
            val collides = overlapsAny(gid, gr, placements)
            val border = if (collides) DwellColors.StatusError else DwellColors.LumenCyan
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(
                        width = with(density) { w.toDp() },
                        height = with(density) { h.toDp() },
                    )
                    .border(
                        width = 1.dp,
                        color = border.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        color = border.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                    ),
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
    placements: Map<String, GridRect>,
    onMove: (id: String, rect: GridRect) -> Unit,
    onResize: (id: String, rect: GridRect) -> Unit,
) {
    val density = LocalDensity.current

    val tileW = rect.cols * cellW + (rect.cols - 1) * gapPx
    val tileH = rect.rows * cellH + (rect.rows - 1) * gapPx
    val tileX = paddingPx + rect.col * stepX
    val tileY = paddingPx + rect.row * stepY

    // Track per-tile drag accumulators so we can snap cleanly to grid cells.
    var moveAccumX by remember(id) { mutableStateOf(0f) }
    var moveAccumY by remember(id) { mutableStateOf(0f) }
    var resizeAccumX by remember(id) { mutableStateOf(0f) }
    var resizeAccumY by remember(id) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .offset { IntOffset(tileX.roundToInt(), tileY.roundToInt()) }
            .size(
                width = with(density) { tileW.toDp() },
                height = with(density) { tileH.toDp() },
            ),
    ) {
        val activeRectForBorder = ghosts[id]
        val isActive = activeRectForBorder != null
        val collides = activeRectForBorder != null && overlapsAny(id, activeRectForBorder, placements)
        val borderColor = when {
            collides -> DwellColors.StatusError
            isActive -> DwellColors.LumenCyan
            else -> DwellColors.LumenCyan.copy(alpha = 0.35f)
        }
        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            moveAccumX = 0f
                            moveAccumY = 0f
                            ghosts[id] = rect
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            moveAccumX += drag.x
                            moveAccumY += drag.y
                            val safeStepX = if (stepX > 0f) stepX else 1f
                            val safeStepY = if (stepY > 0f) stepY else 1f
                            val dCol = (moveAccumX / safeStepX).roundToInt()
                            val dRow = (moveAccumY / safeStepY).roundToInt()
                            val newCol = (rect.col + dCol).coerceIn(0, COLS - rect.cols)
                            val newRow = (rect.row + dRow).coerceIn(0, ROWS - rect.rows)
                            ghosts[id] = rect.copy(col = newCol, row = newRow)
                        },
                        onDragEnd = {
                            val finalRect = ghosts.remove(id)
                            // Reject the move when it would land in total overlap
                            // (>=50% of either rect's area covers another tile).
                            if (finalRect != null && finalRect != rect &&
                                !totallyOverlapsAny(id, finalRect, placements)
                            ) {
                                onMove(id, finalRect)
                            }
                        },
                        onDragCancel = {
                            ghosts.remove(id)
                        },
                    )
                },
        ) {
            // Top row: size badge (left, only while actively dragging/resizing) +
            // persistent drag handle (right).
            val activeRect = ghosts[id]
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (activeRect != null) {
                    Text(
                        text = "${activeRect.cols}×${activeRect.rows}",
                        fontSize = 9.sp,
                        color = DwellColors.LumenCyan,
                        fontFamily = DwellFonts.jetBrainsMono(),
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    // Placeholder keeps the drag handle anchored to the right edge.
                    Box(Modifier)
                }
                Text(
                    text = "⋮⋮",
                    fontSize = 10.sp,
                    color = DwellColors.LumenCyan.copy(alpha = 0.85f),
                    fontFamily = DwellFonts.jetBrainsMono(),
                )
            }
        }

        // Bottom-right resize handle. Sits on top of the move surface so it wins gestures.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(HANDLE_SIZE)
                .border(
                    width = 1.dp,
                    color = DwellColors.LumenCyan,
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 12.dp),
                )
                .background(
                    color = DwellColors.LumenCyan.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 12.dp),
                )
                .pointerInput(id, rect, stepX, stepY, constraint) {
                    detectDragGestures(
                        onDragStart = {
                            resizeAccumX = 0f
                            resizeAccumY = 0f
                            ghosts[id] = rect
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            resizeAccumX += drag.x
                            resizeAccumY += drag.y
                            val safeStepX = if (stepX > 0f) stepX else 1f
                            val safeStepY = if (stepY > 0f) stepY else 1f
                            val dCols = (resizeAccumX / safeStepX).roundToInt()
                            val dRows = (resizeAccumY / safeStepY).roundToInt()
                            val maxColsAtPos = COLS - rect.col
                            val maxRowsAtPos = ROWS - rect.row
                            val newCols = (rect.cols + dCols)
                                .coerceIn(
                                    constraint.minCols,
                                    minOf(constraint.maxCols, maxColsAtPos),
                                )
                            val newRows = (rect.rows + dRows)
                                .coerceIn(
                                    constraint.minRows,
                                    minOf(constraint.maxRows, maxRowsAtPos),
                                )
                            ghosts[id] = rect.copy(cols = newCols, rows = newRows)
                        },
                        onDragEnd = {
                            val finalRect = ghosts.remove(id)
                            if (finalRect != null && finalRect != rect &&
                                !totallyOverlapsAny(id, finalRect, placements)
                            ) {
                                onResize(id, finalRect)
                            }
                        },
                        onDragCancel = {
                            ghosts.remove(id)
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

/** Treat ≥50% area overlap with any other tile as a "total" overlap. */
private fun totallyOverlapsAny(
    selfId: String,
    rect: GridRect,
    placements: Map<String, GridRect>,
): Boolean = placements.any { (otherId, other) ->
    if (otherId == selfId) return@any false
    val area = rect.intersectionArea(other)
    val threshold = 0.5 * minOf(rect.cols * rect.rows, other.cols * other.rows)
    area >= threshold
}
