package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
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
    CompositionLocalProvider(LocalConsoleAccent provides accent) {
        Box(modifier = modifier.fillMaxSize().background(DwellColors.Surface0)) {
            ConsoleGrid(placements = placements, modifier = Modifier.fillMaxSize()) { id ->
                val instance = instances[id] ?: return@ConsoleGrid
                val borderColor = if (accent.tileBorderTint == androidx.compose.ui.graphics.Color.Transparent) {
                    DwellColors.Stroke
                } else {
                    // Composite the accent tint over the base stroke so Amber gets a
                    // barely-perceptible warm cast on borders.
                    accent.tileBorderTint.compositeOver(DwellColors.Stroke)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(DwellRadius.m))
                        .background(DwellColors.Surface1)
                        .border(1.dp, borderColor, RoundedCornerShape(DwellRadius.m)),
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
                    placements = placements,
                    sizeConstraints = sizeConstraints,
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

