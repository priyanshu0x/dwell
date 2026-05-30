package com.droidslife.screensaver.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxIdleMonitorTest {
    @Test
    fun parsesGnomeMutterIdleMonitorOutput() {
        assertEquals(39L, parseGnomeIdleMillis("(uint64 39,)"))
        assertEquals(123_456L, parseGnomeIdleMillis("(uint64 123456,)"))
    }

    @Test
    fun doesNotTreatTheUint64TypeNameAsIdleTime() {
        assertNull(parseGnomeIdleMillis("(true,)"))
    }
}
