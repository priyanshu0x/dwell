package com.droidslife.screensaver.widget.host

import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestParserTest {
    @Test
    fun parsesDeclarativeManifest() {
        val manifest = ManifestParser().parse(
            """
            id: com.example.stocks
            title: Stock Ticker
            description: Real-time stock prices
            category: finance
            apiVersion: 1
            template: list
            preferredSpan: 2
            refresh: 60s
            source:
              type: command
              command: ["python3", "fetch.py"]
              timeout: 10s
            bindings:
              items: "$.holdings[*]"
              item.label: "$.symbol"
              item.value: "$.price"
            config:
              - key: symbols
                label: Symbols
                type: text
                default: "AAPL,GOOG"
            """.trimIndent()
        )

        assertEquals("com.example.stocks", manifest.id)
        assertEquals("Stock Ticker", manifest.title)
        assertEquals("finance", manifest.category)
        assertEquals(2, manifest.preferredSpan)
        assertEquals("command", manifest.source.type)
        assertEquals(listOf("python3", "fetch.py"), manifest.source.command)
        assertEquals("$.holdings[*]", manifest.bindings.getValue("items"))
        assertEquals("symbols", manifest.config.single().key)
    }
}
