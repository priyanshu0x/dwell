package com.droidslife.screensaver.modes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.modes.ambient.AmbientMode
import com.droidslife.screensaver.modes.cinematic.CinematicMode
import com.droidslife.screensaver.modes.console.ConsoleMode
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ModeHost(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = settingsViewModel.settings.mode
    var prev by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != prev) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DwellMotion.ModeChange,
                    easing = DwellMotion.Standard,
                ),
            )
            prev = target
        }
    }

    val p = progress.value
    val blurDp = (12f * sin(p * PI.toFloat())).coerceAtLeast(0f).dp

    Box(modifier.fillMaxSize()) {
        if (prev != target) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(1f - p)
                    .blur(blurDp),
            ) {
                renderMode(prev, settingsViewModel, registry, onOpenSettings, onOpenHelp)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .alpha(p)
                .blur(blurDp),
        ) {
            renderMode(target, settingsViewModel, registry, onOpenSettings, onOpenHelp)
        }
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
