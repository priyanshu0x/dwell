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
@Composable
fun ConsoleGrid(
    placements: Map<String, GridRect>,
    modifier: Modifier = Modifier,
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
            val w = rect.cols * cellW + (rect.cols - 1) * gap
            val h = rect.rows * cellH + (rect.rows - 1) * gap
            val placeable = byId[id]?.measure(
                Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0))
            )
            val x = padding + rect.col * (cellW + gap)
            val y = padding + rect.row * (cellH + gap)
            Triple(placeable, x, y)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, x, y) ->
                placeable?.placeRelative(x, y)
            }
        }
    }
}
