package com.droidslife.screensaver.modes

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.modes.ambient.AmbientMode
import com.droidslife.screensaver.modes.cinematic.CinematicMode
import com.droidslife.screensaver.modes.console.ConsoleMode
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.widget.host.WidgetRegistry

@Composable
fun ModeHost(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = settingsViewModel.settings.mode,
        animationSpec = tween(DwellMotion.ModeChange, easing = DwellMotion.Standard),
        modifier = modifier.fillMaxSize(),
        label = "mode-crossfade",
    ) { mode ->
        when (mode) {
            Mode.Cinematic -> CinematicMode(
                settingsViewModel = settingsViewModel,
                registry = registry,
                onOpenSettings = onOpenSettings,
                onOpenHelp = onOpenHelp,
            )
            Mode.Ambient -> AmbientMode(
                settingsViewModel = settingsViewModel,
                onOpenSettings = onOpenSettings,
                onOpenHelp = onOpenHelp,
            )
            Mode.Console -> ConsoleMode(
                settingsViewModel = settingsViewModel,
                registry = registry,
                onOpenSettings = onOpenSettings,
                onOpenHelp = onOpenHelp,
            )
        }
    }
}
