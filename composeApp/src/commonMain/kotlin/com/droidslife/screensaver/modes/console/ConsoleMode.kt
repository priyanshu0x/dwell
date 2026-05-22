package com.droidslife.screensaver.modes.console

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.ui.DwellRadius
import com.droidslife.screensaver.widget.api.GridRect
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.host.WidgetRegistry

private val defaultLayouts: Map<String, GridRect> = mapOf(
    "com.droidslife.screensaver.clock"           to GridRect(0, 0, 7, 4),
    "com.droidslife.screensaver.weather"         to GridRect(7, 0, 5, 2),
    "com.droidslife.screensaver.todos"           to GridRect(7, 2, 5, 2),
    "com.droidslife.screensaver.expenses"        to GridRect(0, 4, 4, 2),
    "com.droidslife.screensaver.calendar"        to GridRect(4, 4, 4, 2),
    "com.droidslife.screensaver.weatherforecast" to GridRect(8, 4, 4, 2),
    "com.droidslife.screensaver.idle"            to GridRect(8, 4, 4, 2), // opt-in fallback slot
    "com.droidslife.screensaver.pomodoro"        to GridRect(8, 4, 4, 2), // opt-in fallback slot
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConsoleMode(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val instances by registry.instances.collectAsState()
    val userLayouts = settingsViewModel.settings.widgetLayouts
    val placements = remember(instances, userLayouts) {
        instances.keys.associateWith { id ->
            userLayouts[id] ?: defaultLayouts[id] ?: GridRect(0, 0, 4, 2)
        }
    }

    val accent = consoleAccentFor(settingsViewModel.settings.consoleVariant)
    val locked = settingsViewModel.settings.dashboardLocked
    val editing = settingsViewModel.consoleEditMode
    // Tiles are editable when the dashboard is unlocked, OR when the user has
    // explicitly entered edit mode while locked. The visible EDIT LAYOUT banner
    // and size badges only show in the locked-and-editing case so they don't
    // clutter the everyday view.
    val tilesEditable = !locked || editing
    val showEditChrome = locked && editing
    // Per-tile live pixel offsets, populated by ConsoleEditOverlay while a
    // gesture is in progress so the tile chrome (rendered by ConsoleGrid)
    // tracks the cursor pixel-by-pixel instead of jumping cell-to-cell.
    val liveDrags = remember { mutableStateMapOf<String, TileLiveDrag>() }
    // Tracks the most recently interacted tile so we can render it last and
    // ensure it draws above overlapping siblings.
    var lastFocused by remember { mutableStateOf<String?>(null) }
    var hoveredTile by remember { mutableStateOf<String?>(null) }

    // Reorder so the focused id renders LAST (Compose draws in placement order,
    // last-on-top). Map preserves insertion order.
    val orderedPlacements = remember(placements, lastFocused) {
        val focused = lastFocused
        if (focused != null && focused in placements) {
            (placements - focused) + (focused to placements.getValue(focused))
        } else placements
    }
    CompositionLocalProvider(LocalConsoleAccent provides accent) {
        Box(modifier = modifier.fillMaxSize().background(DwellColors.Surface0)) {
            ConsoleGrid(
                placements = orderedPlacements,
                liveDrags = liveDrags,
                modifier = Modifier.fillMaxSize(),
            ) { id ->
                val instance = instances[id] ?: return@ConsoleGrid
                val baseBorder = if (accent.tileBorderTint == Color.Transparent) {
                    DwellColors.Stroke
                } else {
                    // Composite the accent tint over the base stroke so Amber gets a
                    // barely-perceptible warm cast on borders.
                    accent.tileBorderTint.compositeOver(DwellColors.Stroke)
                }
                val isHovered = hoveredTile == id
                val targetBorder = if (isHovered) {
                    accent.primary.copy(alpha = 0.6f).compositeOver(baseBorder)
                } else baseBorder
                val targetBg = if (isHovered) {
                    Color.White.copy(alpha = 0.04f).compositeOver(DwellColors.Surface1)
                } else DwellColors.Surface1
                val borderColor by animateColorAsState(
                    targetValue = targetBorder,
                    animationSpec = tween(DwellMotion.TileHover, easing = DwellMotion.Emphasized),
                    label = "tile-border",
                )
                val backgroundColor by animateColorAsState(
                    targetValue = targetBg,
                    animationSpec = tween(DwellMotion.TileHover, easing = DwellMotion.Emphasized),
                    label = "tile-bg",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(DwellRadius.m))
                        .background(backgroundColor)
                        .border(1.dp, borderColor, RoundedCornerShape(DwellRadius.m))
                        .onPointerEvent(PointerEventType.Enter) { hoveredTile = id }
                        .onPointerEvent(PointerEventType.Exit) {
                            if (hoveredTile == id) hoveredTile = null
                        }
                        .clickable { lastFocused = id },
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        instance.widget.Render(
                            target = WidgetRenderTarget.Tile,
                            scope = instance.scope,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (tilesEditable) {
                val sizeConstraints: Map<String, WidgetSize> = remember(instances) {
                    instances.mapValues { it.value.descriptor.factory.preferredSize }
                }
                ConsoleEditOverlay(
                    placements = orderedPlacements,
                    sizeConstraints = sizeConstraints,
                    liveDrags = liveDrags,
                    hoveredTile = hoveredTile,
                    onHover = { id -> hoveredTile = id },
                    onFocus = { id -> lastFocused = id },
                    showBanner = showEditChrome,
                    onMove = { id, rect -> settingsViewModel.setWidgetLayout(id, rect) },
                    onResize = { id, rect -> settingsViewModel.setWidgetLayout(id, rect) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            CornerButtons(
                onSettings = onOpenSettings,
                onHelp = onOpenHelp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

