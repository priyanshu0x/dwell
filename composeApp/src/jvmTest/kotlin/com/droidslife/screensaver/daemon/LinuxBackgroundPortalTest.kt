package com.droidslife.screensaver.daemon

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LinuxBackgroundPortalTest {
    @Test
    fun backgroundRequestDoesNotAskPortalToManageAutostart() {
        val command = portalRequestBackgroundCommand()
        assertEquals(
            listOf(
                "gdbus",
                "call",
                "--session",
                "--dest",
                "org.freedesktop.portal.Desktop",
                "--object-path",
                "/org/freedesktop/portal/desktop",
                "--method",
                "org.freedesktop.portal.Background.RequestBackground",
                "",
                "{'reason': <'Keeps the idle dashboard daemon ready while Dwell is hidden.'>, 'autostart': <false>}",
            ),
            command,
        )
        assertContains(command, "org.freedesktop.portal.Background.RequestBackground")
        assertContains(command.last(), "'autostart': <false>")
    }

    @Test
    fun statusCommandReportsDaemonPurpose() {
        val command = portalSetBackgroundStatusCommand()
        assertContains(command, "org.freedesktop.portal.Background.SetStatus")
        assertContains(command.last(), "Idle dashboard daemon")
    }

    @Test
    fun gVariantStringEscapesQuotedText() {
        assertEquals("<'Dwell\\'s \\\\ daemon'>", gVariantString("Dwell's \\ daemon"))
    }
}
