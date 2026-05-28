package com.droidslife.screensaver.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the synchronous preference round-trip the Todos list/matrix toggle
 * relies on: a value written by `write` must be readable by a later `read`
 * (i.e. it actually reached disk, no coroutine in the loop).
 */
class WidgetPrefsTest {

    @Test
    fun writeThenReadReturnsValue() {
        val scope = "test.widget.${System.nanoTime()}"
        assertNull(WidgetPrefs.read(scope, "view"))

        WidgetPrefs.write(scope, "view", "matrix")
        assertEquals("matrix", WidgetPrefs.read(scope, "view"))

        // Overwrite wins (last write persists).
        WidgetPrefs.write(scope, "view", "list")
        assertEquals("list", WidgetPrefs.read(scope, "view"))
    }
}
