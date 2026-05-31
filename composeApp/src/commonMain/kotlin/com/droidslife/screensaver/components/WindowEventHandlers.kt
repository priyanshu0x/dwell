package com.droidslife.screensaver.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class WindowEventHandlers(
    val keyEventHandler: KeyEventHandler,
    val mouseEventModifier: Modifier,
    val toastState: ToastState,
    val showHelpDialog: Boolean,
    val onHelpDialogDismiss: () -> Unit,
    val onShowHelpDialog: () -> Unit,
)

@Composable
fun rememberWindowEventHandlers(
    onExitApplication: () -> Unit,
    openSettingsOnStart: Boolean = false,
): WindowEventHandlers {
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val widgetRegistry = koinInject<WidgetRegistry>()
    val coroutineScope = rememberCoroutineScope()

    var showHelpDialog by remember { mutableStateOf(false) }
    val toastState = rememberToastState()

    LaunchedEffect(openSettingsOnStart) {
        if (openSettingsOnStart) {
            settingsViewModel.openSettingsDialog()
        }
    }

    val onAction: (KeyEventAction) -> Unit = { action ->
        when (action) {
            is KeyEventAction.ExitApplication -> {
                onExitApplication()
            }
            is KeyEventAction.RequestExit -> {
                onExitApplication()
            }
            is KeyEventAction.Escape -> {
                // Dismiss the top-most open overlay; exit only when nothing is
                // open. Order = visual stacking: a per-widget config dialog sits
                // above the settings sidebar, which sits above transient
                // edit/drawer overlays.
                when {
                    settingsViewModel.openWidgetConfigId != null ->
                        settingsViewModel.closeWidgetConfig()
                    settingsViewModel.isSettingsDialogOpen ->
                        settingsViewModel.closeSettingsDialog()
                    showHelpDialog ->
                        showHelpDialog = false
                    settingsViewModel.consoleEditMode ->
                        settingsViewModel.updateConsoleEditMode(false)
                    settingsViewModel.drawerVisible ->
                        settingsViewModel.updateDrawerVisible(false)
                    else ->
                        onExitApplication()
                }
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
                // L is meaningful only when the dashboard is locked. When
                // unlocked, tiles are always editable so the banner toggle is
                // a no-op.
                if (settingsViewModel.settings.mode == Mode.Console &&
                    settingsViewModel.settings.dashboardLocked
                ) {
                    settingsViewModel.toggleConsoleEditMode()
                }
            }
            is KeyEventAction.ReloadWidgets -> {
                widgetRegistry.reload()
                coroutineScope.launch {
                    widgetRegistry.syncWithSettings(settingsViewModel.settings)
                }
            }
        }
    }
    val keyEventHandler = rememberKeyEventHandler(
        onAction = onAction,
    )
    val rightClickDismissActive = isRightClickDismissActive(
        settingEnabled = settingsViewModel.settings.rightClickHidesDashboard,
        settingsDialogOpen = settingsViewModel.isSettingsDialogOpen,
        widgetConfigOpen = settingsViewModel.openWidgetConfigId != null,
        helpDialogOpen = showHelpDialog,
    )

    return WindowEventHandlers(
        keyEventHandler = keyEventHandler,
        mouseEventModifier = Modifier.rightClickDismiss(rightClickDismissActive) {
            onAction(KeyEventAction.RequestExit)
        },
        toastState = toastState,
        showHelpDialog = showHelpDialog,
        onHelpDialogDismiss = { showHelpDialog = false },
        onShowHelpDialog = { showHelpDialog = true },
    )
}

internal fun isRightClickDismissActive(
    settingEnabled: Boolean,
    settingsDialogOpen: Boolean,
    widgetConfigOpen: Boolean,
    helpDialogOpen: Boolean,
): Boolean =
    settingEnabled && !settingsDialogOpen && !widgetConfigOpen && !helpDialogOpen

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.rightClickDismiss(
    enabled: Boolean,
    onDismiss: () -> Unit,
): Modifier {
    if (!enabled) return this
    return onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Final) { event ->
        if (event.button == PointerButton.Secondary && event.changes.none { it.isConsumed }) {
            event.changes.forEach { it.consume() }
            onDismiss()
        }
    }
}
