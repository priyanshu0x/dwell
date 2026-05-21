package com.droidslife.screensaver.widget.host

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPathBinderTest {
    private val binder = JsonPathBinder()
    private val json = Json.parseToJsonElement(
        """
        {
          "location": { "name": "Mumbai" },
          "holdings": [
            { "symbol": "AAPL", "price": 192 },
            { "symbol": "GOOG", "price": 144 }
          ]
        }
        """.trimIndent()
    )

    @Test
    fun selectsNestedField() {
        assertEquals(JsonPrimitive("Mumbai"), binder.selectOne(json, "$.location.name"))
    }

    @Test
    fun selectsArrayWildcard() {
        val items = binder.select(json, "$.holdings[*]")

        assertEquals(2, items.size)
        assertEquals(JsonPrimitive("AAPL"), binder.selectOne(items.first(), "$.symbol"))
    }

    @Test
    fun returnsEmptyForUnsupportedPath() {
        assertEquals(emptyList(), binder.select(json, "location.name"))
        assertEquals(emptyList(), binder.select(json, "$.missing.value"))
    }
}
