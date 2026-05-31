package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.ui.DashboardActionBarReservedHeight
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.widget.api.GridRect
import com.droidslife.screensaver.widget.host.WidgetInstance
import kotlin.math.roundToInt

private const val COLS = 12
private const val ROWS = 6
private val GAP = 12.dp
private val PADDING = 32.dp
private val BOTTOM_PADDING = DashboardActionBarReservedHeight

/**
 * Renders a per-widget config gear floating at the top-right corner of each
 * tile that declares a non-empty config schema. Drawn **after**
 * [ConsoleEditOverlay] in the parent Box so its clicks aren't eaten by the
 * tile-level drag detector.
 *
 * The grid math is duplicated from [ConsoleGrid], [ConsoleEditOverlay], and
 * [ConsoleMode]
 * because Kotlin private consts don't cross file boundaries; these files need
 * to stay in sync.
 */
@Composable
fun TileGearsOverlay(
    placements: Map<String, GridRect>,
    instances: Map<String, WidgetInstance>,
    onGearClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val paddingPx = with(density) { PADDING.toPx() }
        val bottomPaddingPx = with(density) { BOTTOM_PADDING.toPx() }
        val gapPx = with(density) { GAP.toPx() }
        val innerW = (constraints.maxWidth - paddingPx * 2).coerceAtLeast(0f)
        val innerH = (constraints.maxHeight - paddingPx - bottomPaddingPx).coerceAtLeast(0f)
        val cellW = ((innerW - gapPx * (COLS - 1)) / COLS).coerceAtLeast(0f)
        val cellH = ((innerH - gapPx * (ROWS - 1)) / ROWS).coerceAtLeast(0f)
        val stepX = cellW + gapPx
        val stepY = cellH + gapPx

        placements.forEach { (id, rect) ->
            val instance = instances[id] ?: return@forEach
            if (instance.descriptor.factory.configSchema.isEmpty()) return@forEach

            val tileW = rect.cols * cellW + (rect.cols - 1) * gapPx
            val tileX = paddingPx + rect.col * stepX
            val tileY = paddingPx + rect.row * stepY

            // Inset 12dp from the tile's top-right corner so the gear sits
            // inside the tile chrome, not on top of the rounded border.
            val insetPx = with(density) { 12.dp.toPx() }
            val gearSizePx = with(density) { 24.dp.toPx() }
            val gx = (tileX + tileW - insetPx - gearSizePx).roundToInt()
            val gy = (tileY + insetPx).roundToInt()

            Gear(
                onClick = { onGearClick(id) },
                modifier = Modifier.offset { IntOffset(gx, gy) },
            )
        }
    }
}

@Composable
private fun Gear(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val tint: Color = if (hovered) DwellColors.TextHigh else DwellColors.TextFaint
    val bg: Color = if (hovered) DwellColors.Surface1 else Color.Transparent
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
    ) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Widget settings",
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
