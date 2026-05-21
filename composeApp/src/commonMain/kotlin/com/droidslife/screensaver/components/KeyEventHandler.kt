package com.droidslife.screensaver.components

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.*

/**
 * Class to handle all keyboard shortcuts in the application
 * Uses UiAction and State patterns to decouple from direct ViewModel dependencies
 */
class KeyEventHandler(
    private val state: KeyEventState,
    private val onAction: (KeyEventAction) -> Unit
) {
    /**
     * Handle window-level key events (from main.kt)
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun handleWindowKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            onAction(KeyEventAction.RequestExit)
            return true
        }

        if (event.type == KeyEventType.KeyDown && event.key == Key.F1) {
            onAction(KeyEventAction.ShowHelp)
            onAction(KeyEventAction.ShowToast("F1"))
            return true
        }

        // '?' (Shift + /) opens help on US keyboards.
        if (event.type == KeyEventType.KeyDown && event.isShiftPressed && event.key == Key.Slash) {
            onAction(KeyEventAction.ShowHelp)
            onAction(KeyEventAction.ShowToast("?"))
            return true
        }

        if (event.type == KeyEventType.KeyDown && (event.isMetaPressed) && event.key == Key.Q) {
            onAction(KeyEventAction.RequestExit)
            onAction(KeyEventAction.ShowToast("Cmd + Q"))
            return true
        }

        // Ctrl+, or Cmd+, opens Settings sheet
        if (event.type == KeyEventType.KeyDown &&
            (event.isCtrlPressed || event.isMetaPressed) &&
            event.key == Key.Comma
        ) {
            onAction(KeyEventAction.OpenSettings)
            onAction(KeyEventAction.ShowToast(if (event.isMetaPressed) "Cmd + ," else "Ctrl + ,"))
            return true
        }

        // Ctrl+R or Cmd+R reloads widgets
        if (event.type == KeyEventType.KeyDown &&
            (event.isCtrlPressed || event.isMetaPressed) &&
            event.key == Key.R
        ) {
            onAction(KeyEventAction.ReloadWidgets)
            onAction(KeyEventAction.ShowToast(if (event.isMetaPressed) "Cmd + R" else "Ctrl + R"))
            return true
        }

        // Plain (unmodified) shortcuts. Only handle when no Ctrl/Meta is pressed so they
        // don't shadow OS / window-level chords.
        if (event.type == KeyEventType.KeyDown && !event.isCtrlPressed && !event.isMetaPressed) {
            when (event.key) {
                Key.M -> {
                    onAction(KeyEventAction.CycleMode)
                    onAction(KeyEventAction.ShowToast("M"))
                    return true
                }
                Key.One -> {
                    onAction(KeyEventAction.JumpCinematic)
                    onAction(KeyEventAction.ShowToast("1"))
                    return true
                }
                Key.Two -> {
                    onAction(KeyEventAction.JumpAmbient)
                    onAction(KeyEventAction.ShowToast("2"))
                    return true
                }
                Key.Three -> {
                    onAction(KeyEventAction.JumpConsole)
                    onAction(KeyEventAction.ShowToast("3"))
                    return true
                }
                Key.V -> {
                    onAction(KeyEventAction.CycleVariant)
                    onAction(KeyEventAction.ShowToast("V"))
                    return true
                }
                Key.W -> {
                    onAction(KeyEventAction.ToggleDrawer)
                    onAction(KeyEventAction.ShowToast("W"))
                    return true
                }
                Key.L -> {
                    onAction(KeyEventAction.ToggleConsoleEdit)
                    onAction(KeyEventAction.ShowToast("L"))
                    return true
                }
                else -> { /* fall through */ }
            }
        }

        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
            when (event.key) {
                Key.N -> {
                    // Cycle through clock designs
                    onAction(KeyEventAction.CycleClockDesign)
                    onAction(KeyEventAction.ShowToast("Ctrl + N"))
                    return true
                }
                Key.P -> {
                    // Toggle auto-changing of clock design
                    onAction(KeyEventAction.ToggleAutoChange)
                    onAction(KeyEventAction.ShowToast("Ctrl + P"))
                    return true
                }
                Key.S -> {
                    // Show city selection dialog
                    onAction(KeyEventAction.ShowCityDialog)
                    onAction(KeyEventAction.ShowToast("Ctrl + S"))
                    return true
                }
                Key.X -> {
                    // Exit the application if exit functionality is enabled
                    onAction(KeyEventAction.ShowToast("Ctrl + X"))
                    if (state.exitOnMouseMovementEnabled) {
                        onAction(KeyEventAction.RequestExit)
                    } else {
                        onAction(KeyEventAction.ShowToast("Enable exit with Ctrl + Z"))
                    }
                    return true
                }
                Key.Z -> {
                    // Toggle exit on mouse movement functionality
                    onAction(KeyEventAction.ToggleExitOnMouseMovement)
                    onAction(KeyEventAction.ShowToast("Ctrl + Z"))
                    return true
                }
                Key.C -> {
                    // Open settings dialog
                    onAction(KeyEventAction.OpenSettings)
                    onAction(KeyEventAction.ShowToast("Ctrl + C"))
                    return true
                }
                Key.H -> {
                    // Show help dialog
                    onAction(KeyEventAction.ShowHelp)
                    onAction(KeyEventAction.ShowToast("Ctrl + H"))
                    return true
                }
                Key.Q -> {
                    // Quit the application
                    onAction(KeyEventAction.RequestExit)
                    onAction(KeyEventAction.ShowToast("Ctrl + Q"))
                    return true
                }
                Key.T -> {
                    // Toggle theme
                    onAction(KeyEventAction.ToggleTheme)
                    onAction(KeyEventAction.ShowToast("Ctrl + T"))
                    return true
                }
                else -> return false
            }
        }
        return false
    }

}

/**
 * Remember a KeyEventHandler instance
 */
@Composable
fun rememberKeyEventHandler(
    state: KeyEventState,
    onAction: (KeyEventAction) -> Unit
): KeyEventHandler {
    return remember(state, onAction) {
        KeyEventHandler(state, onAction)
    }
}
