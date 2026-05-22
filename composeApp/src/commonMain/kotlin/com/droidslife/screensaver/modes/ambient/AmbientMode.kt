package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.AmbientVariant
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons

@Composable
fun AmbientMode(
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (settingsViewModel.settings.ambientVariant) {
            AmbientVariant.Lumen -> Lumen(settingsViewModel)
            AmbientVariant.Borealis -> Borealis(settingsViewModel)
        }
        CornerButtons(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
