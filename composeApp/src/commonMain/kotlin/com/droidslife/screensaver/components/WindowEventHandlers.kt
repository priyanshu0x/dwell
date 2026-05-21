package com.droidslife.screensaver.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.clock.ClockViewModel
import com.droidslife.screensaver.settings.SettingsViewModel
import org.koin.compose.koinInject

data class WindowEventHandlers(
    val keyEventHandler: KeyEventHandler,
    val mouseEventModifier: Modifier,
    val toastState: ToastState,
    val showCitySelectionDialog: Boolean,
    val onCityDialogDismiss: () -> Unit,
    val onShowCityDialog: () -> Unit,
    val exitOnMouseMovementEnabled: Boolean,
    val showHelpDialog: Boolean,
    val onHelpDialogDismiss: () -> Unit,
)

@Composable
fun rememberWindowEventHandlers(
    onExitApplication: () -> Unit,
    openSettingsOnStart: Boolean = false,
): WindowEventHandlers {
    val clockViewModel = koinInject<ClockViewModel>()
    val settingsViewModel = koinInject<SettingsViewModel>()

    var showCitySelectionDialog by remember { mutableStateOf(false) }
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
            is KeyEventAction.CycleClockDesign -> {
                clockViewModel.cycleClockDesign()
                println("Clock design changed to: ${clockViewModel.clockDesign}")
            }
            is KeyEventAction.ToggleAutoChange -> {
                val isEnabled = clockViewModel.toggleAutoChange()
                println("Auto-change ${if (isEnabled) "enabled" else "disabled"}")
            }
            is KeyEventAction.ToggleShuffle -> {
                val isEnabled = clockViewModel.toggleShuffleMode()
                println("Shuffle mode ${if (isEnabled) "enabled" else "disabled"}")
            }
            is KeyEventAction.ShowCityDialog -> {
                showCitySelectionDialog = true
                println("City selection dialog shown")
            }
            is KeyEventAction.ExitApplication -> {
                println("Exiting application via Ctrl+X shortcut")
                onExitApplication()
            }
            is KeyEventAction.RequestExit -> {
                println("Exit requested")
                onExitApplication()
            }
            is KeyEventAction.ToggleExitOnMouseMovement -> {
                exitOnMouseMovementEnabled = !exitOnMouseMovementEnabled
                println("Exit on mouse movement ${if (exitOnMouseMovementEnabled) "enabled" else "disabled"}")
            }
            is KeyEventAction.OpenSettings -> {
                settingsViewModel.openSettingsDialog()
                println("Settings dialog opened")
            }
            is KeyEventAction.ShowHelp -> {
                showHelpDialog = true
                println("Help dialog shown")
            }
            is KeyEventAction.ToggleTheme -> {
                settingsViewModel.toggleTheme()
                println("Theme toggled")
            }
            is KeyEventAction.ShowToast -> {
                toastState.show(action.message)
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
        showCitySelectionDialog = showCitySelectionDialog,
        onCityDialogDismiss = { showCitySelectionDialog = false },
        onShowCityDialog = { showCitySelectionDialog = true },
        exitOnMouseMovementEnabled = exitOnMouseMovementEnabled,
        showHelpDialog = showHelpDialog,
        onHelpDialogDismiss = { showHelpDialog = false },
    )
}
