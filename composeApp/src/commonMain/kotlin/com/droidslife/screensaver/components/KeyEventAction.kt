package com.droidslife.screensaver.components

/**
 * Sealed class representing all possible UI actions that can be triggered by key events.
 * This decouples the KeyEventHandler from direct ViewModel dependencies.
 */
sealed class KeyEventAction {
    /**
     * Action to exit the application.
     */
    object ExitApplication : KeyEventAction()

    /**
     * Action to request the host-level exit flow.
     */
    object RequestExit : KeyEventAction()

    /**
     * Action to toggle exit on mouse movement functionality.
     */
    object ToggleExitOnMouseMovement : KeyEventAction()

    /**
     * Action to open settings dialog.
     */
    object OpenSettings : KeyEventAction()

    /**
     * Action to show help dialog.
     */
    object ShowHelp : KeyEventAction()

    /**
     * Action to toggle theme.
     */
    object ToggleTheme : KeyEventAction()

    /**
     * Action to show a toast notification.
     * @param message The message to show in the toast.
     */
    data class ShowToast(val message: String) : KeyEventAction()

    /** Cycle through display modes (Cinematic → Ambient → Console → Cinematic). */
    object CycleMode : KeyEventAction()

    /** Jump directly to Cinematic mode. */
    object JumpCinematic : KeyEventAction()

    /** Jump directly to Ambient mode. */
    object JumpAmbient : KeyEventAction()

    /** Jump directly to Console mode. */
    object JumpConsole : KeyEventAction()

    /** Cycle to the next variant within the current mode. */
    object CycleVariant : KeyEventAction()

    /** Toggle the Cinematic widget drawer. */
    object ToggleDrawer : KeyEventAction()

    /** Toggle Console layout edit mode. */
    object ToggleConsoleEdit : KeyEventAction()

    /**
     * Toggle the dashboard lock. Locking removes the Console edit overlay so
     * widget controls (e.g. Pomodoro Start) become tappable; unlocking returns
     * to drag-to-arrange.
     */
    object ToggleDashboardLock : KeyEventAction()

    /** Reload widgets (factories + instances) from disk. */
    object ReloadWidgets : KeyEventAction()
}

/**
 * Data class representing the current UI state that the KeyEventHandler needs to know about.
 * This decouples the KeyEventHandler from direct ViewModel dependencies.
 */
data class KeyEventState(
    /**
     * Whether exit on mouse movement is enabled.
     */
    val exitOnMouseMovementEnabled: Boolean = true
)
