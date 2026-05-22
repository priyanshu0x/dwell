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
    // Crossfade manages the transition state internally so we don't get the
    // first-frame race the hand-rolled Animatable had — where the new mode
    // would flash at full alpha for one frame before snapTo(0) reverted to
    // the old mode.
    Crossfade(
        targetState = settingsViewModel.settings.mode,
        animationSpec = tween(
            durationMillis = DwellMotion.ModeChange,
            easing = DwellMotion.Standard,
        ),
        modifier = modifier.fillMaxSize(),
        label = "mode-crossfade",
    ) { mode ->
        renderMode(mode, settingsViewModel, registry, onOpenSettings, onOpenHelp)
    }
}

@Composable
private fun renderMode(
    mode: Mode,
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
) {
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
