package com.droidslife.screensaver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.modes.ModeHost
import com.droidslife.screensaver.settings.SettingsDialog
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.settings.ShortcutsHelpDialog
import com.droidslife.screensaver.theme.AppTheme
import com.droidslife.screensaver.widget.host.WidgetRegistry
import org.koin.compose.koinInject

@Composable
internal fun App(
    showCitySelectionDialog: Boolean = false,
    onCityDialogDismiss: () -> Unit = {},
    onShowCityDialog: () -> Unit = {},
    exitOnMouseMovementEnabled: Boolean = true,
    onExitApplication: () -> Unit = {},
    exitRequested: Boolean = false,
    onExited: () -> Unit = onExitApplication,
    showHelpDialog: Boolean = false,
    onHelpDialogDismiss: () -> Unit = {},
    onShowHelpDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settingsViewModel = koinInject<SettingsViewModel>()
    val widgetRegistry = koinInject<WidgetRegistry>()
    val widgetDescriptors by widgetRegistry.descriptors.collectAsState()

    LaunchedEffect(settingsViewModel.settings) {
        widgetRegistry.syncWithSettings(settingsViewModel.settings)
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(exitRequested) {
        if (exitRequested) {
            visible = false
        }
    }

    LaunchedEffect(visible, exitRequested) {
        if (exitRequested && !visible) {
            kotlinx.coroutines.delay(450)
            onExited()
        }
    }

    AppTheme(isDark = settingsViewModel.settings.isDarkTheme) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.96f,
            ),
            exit = fadeOut(tween(400)),
        ) {
            Box(modifier = modifier.fillMaxSize()) {
                ModeHost(
                    settingsViewModel = settingsViewModel,
                    registry = widgetRegistry,
                    onOpenSettings = { settingsViewModel.openSettingsDialog() },
                    onOpenHelp = onShowHelpDialog,
                )
            }
        }

        // Settings dialog overlay
        if (settingsViewModel.isSettingsDialogOpen) {
            SettingsDialog(
                onDismiss = { settingsViewModel.closeSettingsDialog() },
                settings = settingsViewModel.settings,
                onThemeToggle = { settingsViewModel.toggleTheme() },
                onClockFormatToggle = { settingsViewModel.toggleClockFormat() },
                onAutoPlayToggle = { settingsViewModel.toggleAutoPlay() },
                onShuffleToggle = { settingsViewModel.toggleShuffle() },
                widgetDescriptors = widgetDescriptors,
                onWidgetEnabledChange = settingsViewModel::setWidgetEnabled,
                onWidgetConfigChange = settingsViewModel::updateWidgetConfig,
                onWidgetSecretChange = settingsViewModel::updateWidgetSecret,
                onWidgetReload = {
                    widgetRegistry.reload()
                    widgetRegistry.syncWithSettings(settingsViewModel.settings)
                },
                onIdleTimeoutChange = settingsViewModel::setIdleTimeoutMinutes,
                onTrayIconToggle = settingsViewModel::setTrayIconEnabled,
                onStartWithSystemToggle = settingsViewModel::setStartWithSystem,
                onBackendBaseUrlChange = settingsViewModel::setBackendBaseUrl,
                onBackendApiKeyChange = settingsViewModel::updateBackendApiKey,
                onWeatherApiKeyChange = settingsViewModel::updateWeatherApiKey,
                backendApiKeySaved = settingsViewModel.isSecretSaved(settingsViewModel.settings.backendApiKeySecretId),
                weatherApiKeySaved = settingsViewModel.isSecretSaved(settingsViewModel.settings.weatherApiKeySecretId),
                savedSecretIds = settingsViewModel.savedSecretIds,
            )
        }

        // Help dialog overlay
        if (showHelpDialog) {
            ShortcutsHelpDialog(
                onDismiss = onHelpDialogDismiss,
                onExitApplication = onExitApplication,
            )
        }
    }
}
