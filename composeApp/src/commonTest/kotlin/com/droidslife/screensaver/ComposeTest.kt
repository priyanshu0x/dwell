package com.droidslife.screensaver

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeTest {
    @Test
    fun simpleCheck() {
        var text = "Go"
        repeat(3) { text += "." }
        assertEquals("Go...", text)
    }
}
