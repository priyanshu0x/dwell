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
import androidx.compose.ui.draw.clip
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
                onMove = onMove,
                onResize = onResize,
            )
        }

        // Ghost rectangles for in-progress drags.
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
                    .border(
                        width = 1.dp,
                        color = DwellColors.LumenCyan.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        color = DwellColors.LumenCyan.copy(alpha = 0.08f),
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
        // Drag-to-move surface (covers tile, excluding the bottom-right resize handle).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = DwellColors.LumenCyan.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                )
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
                            if (finalRect != null && finalRect != rect) {
                                onMove(id, finalRect)
                            }
                        },
                        onDragCancel = {
                            ghosts.remove(id)
                        },
                    )
                },
        ) {
            // Top row: size badge (left) + drag handle (right).
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${rect.cols}×${rect.rows}",
                    fontSize = 9.sp,
                    color = DwellColors.LumenCyan,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                )
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
                            if (finalRect != null && finalRect != rect) {
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
