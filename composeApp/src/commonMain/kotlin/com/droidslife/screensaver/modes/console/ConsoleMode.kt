package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.widget.api.GridRect
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.host.WidgetRegistry

private val defaultLayouts: Map<String, GridRect> = mapOf(
    "com.droidslife.screensaver.clock"    to GridRect(0, 0, 7, 4),
    "com.droidslife.screensaver.weather"  to GridRect(7, 0, 5, 2),
    "com.droidslife.screensaver.todos"    to GridRect(7, 2, 5, 2),
    "com.droidslife.screensaver.expenses" to GridRect(0, 4, 4, 2),
    "com.droidslife.screensaver.calendar" to GridRect(4, 4, 4, 2),
    "com.droidslife.screensaver.idle"     to GridRect(8, 4, 4, 2),
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
    CompositionLocalProvider(LocalConsoleAccent provides accent) {
        Box(modifier = modifier.fillMaxSize().background(DwellColors.Surface0)) {
            ConsoleGrid(placements = placements, modifier = Modifier.fillMaxSize()) { id ->
                val instance = instances[id] ?: return@ConsoleGrid
                instance.widget.Render(
                    target = WidgetRenderTarget.Tile,
                    scope = instance.scope,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (settingsViewModel.consoleEditMode) {
                val sizeConstraints: Map<String, WidgetSize> = remember(instances) {
                    instances.mapValues { it.value.descriptor.factory.preferredSize }
                }
                ConsoleEditOverlay(
                    placements = placements,
                    sizeConstraints = sizeConstraints,
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
