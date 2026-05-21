package com.droidslife.screensaver.widget.host

import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScopedStorageTest {
    @Test
    fun valuesPersistAcrossStorageInstances() = runTest {
        val widgetId = "test.widget.${UUID.randomUUID()}"
        val first = ScopedStorage(widgetId)
        val second = ScopedStorage(widgetId)

        first.write("greeting", "hello")

        assertEquals("hello", second.read("greeting", String::class.java))

        second.delete("greeting")
        assertNull(first.read("greeting", String::class.java))
    }
}
