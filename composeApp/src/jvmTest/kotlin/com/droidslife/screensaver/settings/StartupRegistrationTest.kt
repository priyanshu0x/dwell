package com.droidslife.screensaver.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupRegistrationTest {
    @Test
    fun windowsRunCommandQuotesExecutablePath() {
        assertEquals(
            "\"C:\\Program Files\\Screen Saver App\\Screen Saver App.exe\" --daemon",
            windowsRunCommand("C:\\Program Files\\Screen Saver App\\Screen Saver App.exe", "--daemon"),
        )
    }

    @Test
    fun desktopExecCommandQuotesPathsWithSpaces() {
        assertEquals(
            "\"/opt/Screen Saver App/bin/screensaver-app\" --daemon",
            desktopExecCommand("/opt/Screen Saver App/bin/screensaver-app", "--daemon"),
        )
    }

    @Test
    fun desktopExecCommandLeavesSimplePathsUnquoted() {
        assertEquals(
            "/usr/bin/screensaver-app --daemon",
            desktopExecCommand("/usr/bin/screensaver-app", "--daemon"),
        )
    }
}
