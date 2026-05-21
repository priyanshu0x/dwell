package com.droidslife.screensaver.modes

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.ambient.AmbientMode
import com.droidslife.screensaver.modes.cinematic.CinematicMode
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
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
            Mode.Console -> PlaceholderMode(
                label = "Console — wired in Phase 6",
                onOpenSettings = onOpenSettings,
                onOpenHelp = onOpenHelp,
            )
        }
    }
}

@Composable
private fun PlaceholderMode(
    label: String,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(DwellColors.BgVoid),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            color = DwellColors.TextMid,
        )
        CornerButtons(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
