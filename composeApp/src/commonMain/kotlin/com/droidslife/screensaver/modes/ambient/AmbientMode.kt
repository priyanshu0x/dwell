package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.AmbientVariant
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DashboardActionBar
import com.droidslife.screensaver.ui.DashboardActionBarReservedHeight
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.host.WidgetRegistry

@Composable
fun AmbientMode(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (settingsViewModel.settings.ambientVariant) {
            AmbientVariant.Lumen -> Lumen(settingsViewModel)
            AmbientVariant.Borealis -> Borealis(settingsViewModel)
        }

        // Dim minimal lines for any enabled widget that opts in (Pomodoro today).
        val instances by registry.instances.collectAsState()
        val minimal = instances.values.filter { it.widget.rendersOwnMinimal }
        if (minimal.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = DashboardActionBarReservedHeight),
            ) {
                minimal.forEach { instance ->
                    key(instance.scope) {
                        instance.widget.Render(WidgetRenderTarget.Minimal, instance.scope, Modifier)
                    }
                }
            }
        }

        DashboardActionBar(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
