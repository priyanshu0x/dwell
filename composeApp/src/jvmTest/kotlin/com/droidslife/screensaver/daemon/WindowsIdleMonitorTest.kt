package com.droidslife.screensaver.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsIdleMonitorTest {
    @Test
    fun lastInputInfoCanBeReadByJna() {
        val info = LastInputInfo()

        assertTrue(info.size() > 0)
        assertEquals(info.size(), info.cbSize)
    }
}
