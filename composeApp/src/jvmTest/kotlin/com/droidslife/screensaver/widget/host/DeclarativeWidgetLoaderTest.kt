package com.droidslife.screensaver.widget.host

import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeclarativeWidgetLoaderTest {
    @Test
    fun loadsWidgetYamlFolders() {
        val root = createTempDirectory(prefix = "screen-saver-widgets")
        val widgetFolder = root.resolve("metrics").createDirectory()
        widgetFolder.resolve("widget.yaml").writeText(
            """
            id: sample.metrics
            title: Metrics
            description: Sample metrics widget
            category: information
            apiVersion: 1
            template: list
            source:
              type: file
              path: data.json
            bindings:
              items: $.items[*]
              item.label: $.label
              item.value: $.value
            """.trimIndent()
        )

        val descriptors = DeclarativeWidgetLoader(root).load()

        assertEquals(listOf("sample.metrics"), descriptors.map { it.id })
        assertTrue(descriptors.single().factory is DeclarativeWidgetFactory)
    }
}
