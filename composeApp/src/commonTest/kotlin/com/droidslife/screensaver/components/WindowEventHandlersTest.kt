package com.droidslife.screensaver.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowEventHandlersTest {
    @Test
    fun rightClickDismissIsActiveWhenEnabledAndDashboardIsClear() {
        assertTrue(
            isRightClickDismissActive(
                settingEnabled = true,
                settingsDialogOpen = false,
                widgetConfigOpen = false,
                helpDialogOpen = false,
            )
        )
    }

    @Test
    fun rightClickDismissIsInactiveWhenSettingIsDisabled() {
        assertFalse(
            isRightClickDismissActive(
                settingEnabled = false,
                settingsDialogOpen = false,
                widgetConfigOpen = false,
                helpDialogOpen = false,
            )
        )
    }

    @Test
    fun rightClickDismissIsInactiveWhileOverlaysAreOpen() {
        assertFalse(
            isRightClickDismissActive(
                settingEnabled = true,
                settingsDialogOpen = true,
                widgetConfigOpen = false,
                helpDialogOpen = false,
            )
        )
        assertFalse(
            isRightClickDismissActive(
                settingEnabled = true,
                settingsDialogOpen = false,
                widgetConfigOpen = true,
                helpDialogOpen = false,
            )
        )
        assertFalse(
            isRightClickDismissActive(
                settingEnabled = true,
                settingsDialogOpen = false,
                widgetConfigOpen = false,
                helpDialogOpen = true,
            )
        )
    }
}
