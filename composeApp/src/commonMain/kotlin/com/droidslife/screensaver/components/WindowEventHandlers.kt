package com.droidslife.screensaver.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.widget.host.WidgetRegistry
import org.koin.compose.koinInject

data class WindowEventHandlers(
    val keyEventHandler: KeyEventHandler,
    val mouseEventModifier: Modifier,
    val toastState: ToastState,
    val exitOnMouseMovementEnabled: Boolean,
    val showHelpDialog: Boolean,
    val onHelpDialogDismiss: () -> Unit,
    val onShowHelpDialog: () -> Unit,
)

@Composable
fun rememberWindowEventHandlers(
    onExitApplication: () -> Unit,
    openSettingsOnStart: Boolean = false,
): WindowEventHandlers {
    val settingsViewModel = koinInject<SettingsViewModel>()
    val widgetRegistry = koinInject<WidgetRegistry>()

    var exitOnMouseMovementEnabled by remember { mutableStateOf(true) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val toastState = rememberToastState()

    LaunchedEffect(openSettingsOnStart) {
        if (openSettingsOnStart) {
            settingsViewModel.openSettingsDialog()
        }
    }

    val keyEventState = KeyEventState(
        exitOnMouseMovementEnabled = exitOnMouseMovementEnabled,
    )
    val onAction: (KeyEventAction) -> Unit = { action ->
        when (action) {
            is KeyEventAction.ExitApplication -> {
                onExitApplication()
            }
            is KeyEventAction.RequestExit -> {
                onExitApplication()
            }
            is KeyEventAction.ToggleExitOnMouseMovement -> {
                exitOnMouseMovementEnabled = !exitOnMouseMovementEnabled
            }
            is KeyEventAction.OpenSettings -> {
                settingsViewModel.openSettingsDialog()
            }
            is KeyEventAction.ShowHelp -> {
                showHelpDialog = true
            }
            is KeyEventAction.ToggleTheme -> {
                settingsViewModel.toggleTheme()
            }
            is KeyEventAction.ShowToast -> {
                toastState.show(action.message)
            }
            is KeyEventAction.CycleMode -> {
                settingsViewModel.cycleMode()
            }
            is KeyEventAction.JumpCinematic -> {
                settingsViewModel.setMode(Mode.Cinematic)
            }
            is KeyEventAction.JumpAmbient -> {
                settingsViewModel.setMode(Mode.Ambient)
            }
            is KeyEventAction.JumpConsole -> {
                settingsViewModel.setMode(Mode.Console)
            }
            is KeyEventAction.CycleVariant -> {
                settingsViewModel.cycleVariant()
            }
            is KeyEventAction.ToggleDrawer -> {
                if (settingsViewModel.settings.mode == Mode.Cinematic) {
                    settingsViewModel.toggleDrawer()
                }
            }
            is KeyEventAction.ToggleConsoleEdit -> {
                if (settingsViewModel.settings.mode == Mode.Console) {
                    settingsViewModel.toggleConsoleEditMode()
                }
            }
            is KeyEventAction.ReloadWidgets -> {
                widgetRegistry.reload()
                widgetRegistry.syncWithSettings(settingsViewModel.settings)
            }
        }
    }
    val keyEventHandler = rememberKeyEventHandler(
        state = keyEventState,
        onAction = onAction,
    )

    return WindowEventHandlers(
        keyEventHandler = keyEventHandler,
        mouseEventModifier = Modifier,
        toastState = toastState,
        exitOnMouseMovementEnabled = exitOnMouseMovementEnabled,
        showHelpDialog = showHelpDialog,
        onHelpDialogDismiss = { showHelpDialog = false },
        onShowHelpDialog = { showHelpDialog = true },
    )
}
