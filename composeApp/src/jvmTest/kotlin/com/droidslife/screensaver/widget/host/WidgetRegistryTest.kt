package com.droidslife.screensaver.widget.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.SettingsModel
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetRegistryTest {
    @Test
    fun builtInFactoriesAreDiscoveredAndEnabledAfterSettingsSync() {
        val registry = WidgetRegistry(listOf(FakeWidgetFactory()), HttpClient(OkHttp))

        assertEquals(listOf("test.widget"), registry.descriptors.value.map { it.id })
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))
        assertTrue(registry.instances.value.containsKey("test.widget"))
    }

    @Test
    fun emptyEnabledWidgetSetEnablesAllBuiltInsByDefault() {
        val registry = WidgetRegistry(
            listOf(
                FakeWidgetFactory(id = "test.clock"),
                FakeWidgetFactory(id = "test.todos"),
            ),
            HttpClient(OkHttp),
        )

        registry.syncWithSettings(SettingsModel())

        assertEquals(setOf("test.clock", "test.todos"), registry.instances.value.keys)
    }

    @Test
    fun disableAndEnableToggleInstance() {
        val registry = WidgetRegistry(listOf(FakeWidgetFactory()), HttpClient(OkHttp))
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))

        registry.disable("test.widget")
        assertFalse(registry.instances.value.containsKey("test.widget"))

        registry.enable("test.widget")
        assertTrue(registry.instances.value.containsKey("test.widget"))
    }

    @Test
    fun updateConfigRecreatesEnabledWidget() {
        val factory = FakeWidgetFactory()
        val registry = WidgetRegistry(listOf(factory), HttpClient(OkHttp))
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))

        registry.updateConfig("test.widget", JsonObject(mapOf("label" to JsonPrimitive("updated"))))

        assertEquals(2, factory.createCount)
    }
}

private class FakeWidgetFactory(
    override val id: String = "test.widget",
) : WidgetFactory {
    var createCount = 0

    override val displayName: String = "Test Widget"

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        createCount += 1
        return FakeWidget
    }
}

private object FakeWidget : Widget {
    @Composable
    override fun Content(modifier: Modifier) = Unit
}
