package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.widget.api.GridRect

private const val COLS = 12
private const val ROWS = 6
private val GAP = 12.dp
private val PADDING = 32.dp

/**
 * 12x6 grid layout engine for Console mode. Each placement is positioned and
 * sized according to its [GridRect]. Cells are emitted lazily via [cell].
 */
/**
 * Per-tile pixel-precise transform applied on top of its [GridRect] while a
 * drag or resize is in progress. Empty/zero outside an active gesture; the
 * tile reverts to its grid-snapped position the moment the gesture ends.
 */
data class TileLiveDrag(val dx: Float, val dy: Float, val dw: Float, val dh: Float) {
    companion object {
        val Zero = TileLiveDrag(0f, 0f, 0f, 0f)
    }
}

@Composable
fun ConsoleGrid(
    placements: Map<String, GridRect>,
    modifier: Modifier = Modifier,
    liveDrags: Map<String, TileLiveDrag> = emptyMap(),
    cell: @Composable (id: String) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            placements.forEach { (id, _) ->
                Box(modifier = Modifier.layoutId(id)) { cell(id) }
            }
        },
    ) { measurables, constraints ->
        val padding = PADDING.roundToPx()
        val gap = GAP.roundToPx()
        val innerW = (constraints.maxWidth - padding * 2).coerceAtLeast(0)
        val innerH = (constraints.maxHeight - padding * 2).coerceAtLeast(0)
        val cellW = ((innerW - gap * (COLS - 1)) / COLS).coerceAtLeast(0)
        val cellH = ((innerH - gap * (ROWS - 1)) / ROWS).coerceAtLeast(0)

        val byId = measurables.associateBy { it.layoutId as String }

        val placed = placements.map { (id, rect) ->
            val baseW = rect.cols * cellW + (rect.cols - 1) * gap
            val baseH = rect.rows * cellH + (rect.rows - 1) * gap
            val live = liveDrags[id] ?: TileLiveDrag.Zero
            val w = (baseW + live.dw.toInt()).coerceAtLeast(0)
            val h = (baseH + live.dh.toInt()).coerceAtLeast(0)
            val placeable = byId[id]?.measure(Constraints.fixed(w, h))
            val baseX = padding + rect.col * (cellW + gap)
            val baseY = padding + rect.row * (cellH + gap)
            val x = baseX + live.dx.toInt()
            val y = baseY + live.dy.toInt()
            Triple(placeable, x, y)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, x, y) ->
                placeable?.placeRelative(x, y)
            }
        }
    }
}
