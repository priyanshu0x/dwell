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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.components.ShortcutToast
import com.droidslife.screensaver.components.rememberToastState
import com.droidslife.screensaver.modes.ModeHost
import com.droidslife.screensaver.settings.SettingsSidebar
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.settings.ShortcutsHelpDialog
import com.droidslife.screensaver.settings.WidgetConfigDialog
import com.droidslife.screensaver.theme.AppTheme
import com.droidslife.screensaver.widget.host.WidgetRegistry
import org.koin.compose.koinInject

@Composable
internal fun App(
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

    LaunchedEffect(settingsViewModel.settings, settingsViewModel.settingsLoaded) {
        if (settingsViewModel.settingsLoaded) {
            widgetRegistry.syncWithSettings(settingsViewModel.settings)
        }
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(settingsViewModel.settingsLoaded) {
        if (settingsViewModel.settingsLoaded) {
            visible = true
        }
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

    val welcomeToast = rememberToastState()
    LaunchedEffect(visible, settingsViewModel.settingsLoaded, settingsViewModel.settings.welcomeShown) {
        if (visible && settingsViewModel.settingsLoaded && !settingsViewModel.settings.welcomeShown) {
            // Give the fade-in time to settle, then greet the user once.
            kotlinx.coroutines.delay(900)
            welcomeToast.show(
                "Welcome to Dwell — press M to switch modes · V for variants · F1 for help",
                durationMs = 4500L,
            )
            settingsViewModel.markWelcomeShown()
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
            if (settingsViewModel.settingsLoaded) {
                Box(modifier = modifier.fillMaxSize()) {
                    ModeHost(
                        settingsViewModel = settingsViewModel,
                        registry = widgetRegistry,
                        onOpenSettings = { settingsViewModel.openSettingsDialog() },
                        onOpenHelp = onShowHelpDialog,
                    )
                    ShortcutToast(toastState = welcomeToast)

                    // Settings as an in-window sidebar — the dashboard underneath
                    // stays live so toggle effects are visible immediately.
                    if (settingsViewModel.isSettingsDialogOpen) {
                        SettingsSidebar(
                            settingsViewModel = settingsViewModel,
                            widgetRegistry = widgetRegistry,
                            onDismiss = { settingsViewModel.closeSettingsDialog() },
                        )
                    }

                    settingsViewModel.openWidgetConfigId?.let { widgetId ->
                        WidgetConfigDialog(
                            widgetId = widgetId,
                            settingsViewModel = settingsViewModel,
                            widgetRegistry = widgetRegistry,
                            onDismiss = { settingsViewModel.closeWidgetConfig() },
                        )
                    }
                }
            }
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
