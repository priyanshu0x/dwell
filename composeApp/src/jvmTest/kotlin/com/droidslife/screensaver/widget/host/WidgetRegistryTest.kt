package com.droidslife.screensaver.widget.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.SecretStorage
import com.droidslife.screensaver.settings.SettingsModel
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetRegistryTest {
    @Test
    fun builtInFactoriesAreDiscoveredAndEnabledAfterSettingsSync() = runTest {
        val registry = WidgetRegistry(listOf(FakeWidgetFactory()), HttpClient(OkHttp))

        assertEquals(listOf("test.widget"), registry.descriptors.value.map { it.id })
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))
        assertTrue(registry.instances.value.containsKey("test.widget"))
    }

    @Test
    fun emptyEnabledWidgetSetEnablesAllBuiltInsByDefault() = runTest {
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
    fun disableAndEnableToggleInstance() = runTest {
        val registry = WidgetRegistry(listOf(FakeWidgetFactory()), HttpClient(OkHttp))
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))
        val oldInstance = registry.instances.value.getValue("test.widget")

        registry.disable("test.widget")
        assertFalse(registry.instances.value.containsKey("test.widget"))
        assertFalse(oldInstance.scope.coroutineScope.isActive)

        registry.enable("test.widget")
        assertTrue(registry.instances.value.containsKey("test.widget"))
    }

    @Test
    fun updateConfigRecreatesEnabledWidget() = runTest {
        val factory = FakeWidgetFactory()
        val registry = WidgetRegistry(listOf(factory), HttpClient(OkHttp))
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))

        registry.updateConfig("test.widget", JsonObject(mapOf("label" to JsonPrimitive("updated"))))

        assertEquals(2, factory.createCount)
    }

    @Test
    fun secretVersionChangeRecreatesEnabledWidget() = runTest {
        val factory = FakeWidgetFactory()
        val registry = WidgetRegistry(listOf(factory), HttpClient(OkHttp))
        val config = JsonObject(mapOf("apiToken" to JsonPrimitive("widget.test.widget.apiToken")))
        registry.syncWithSettings(
            SettingsModel(
                enabledWidgetIds = setOf("test.widget"),
                widgetConfigs = mapOf("test.widget" to config),
                widgetSecretVersions = mapOf("widget.test.widget.apiToken" to 1L),
            )
        )

        registry.syncWithSettings(
            SettingsModel(
                enabledWidgetIds = setOf("test.widget"),
                widgetConfigs = mapOf("test.widget" to config),
                widgetSecretVersions = mapOf("widget.test.widget.apiToken" to 2L),
            )
        )

        assertEquals(2, factory.createCount)
    }

    @Test
    fun widgetSecretsAreResolvedBeforeFactoryCreate() = runTest {
        var resolvedToken: String? = null
        val factory = FakeWidgetFactory(
            onCreate = { config -> resolvedToken = config.secret("apiToken") },
        )
        val secretStorage = FakeSecretStorage(
            mapOf("widget.test.widget.apiToken" to "token-value"),
        )
        val registry = WidgetRegistry(listOf(factory), HttpClient(OkHttp), secretStorage = secretStorage)
        val config = JsonObject(mapOf("apiToken" to JsonPrimitive("widget.test.widget.apiToken")))

        registry.enable("test.widget", config)

        assertEquals("token-value", resolvedToken)
        assertEquals(1, secretStorage.reads)
    }

    @Test
    fun factoryCreateFailureDoesNotRegisterWidgetAndCancelsScope() = runTest {
        val factory = FakeWidgetFactory(createThrows = true)
        val registry = WidgetRegistry(listOf(factory), HttpClient(OkHttp))

        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))

        assertFalse(registry.instances.value.containsKey("test.widget"))
        assertFalse(factory.lastScope?.coroutineScope?.isActive ?: true)
    }

    @Test
    fun disposeFailureStillRemovesWidgetAndCancelsScope() = runTest {
        val factory = FakeWidgetFactory(widget = ThrowingDisposeWidget)
        val registry = WidgetRegistry(listOf(factory), HttpClient(OkHttp))
        registry.syncWithSettings(SettingsModel(enabledWidgetIds = setOf("test.widget")))
        val instance = registry.instances.value.getValue("test.widget")

        registry.disable("test.widget")

        assertFalse(registry.instances.value.containsKey("test.widget"))
        assertFalse(instance.scope.coroutineScope.isActive)
    }
}

private class FakeWidgetFactory(
    override val id: String = "test.widget",
    private val createThrows: Boolean = false,
    private val widget: Widget = FakeWidget,
    private val onCreate: (WidgetConfig) -> Unit = {},
) : WidgetFactory {
    var createCount = 0
    var lastScope: WidgetScope? = null

    override val displayName: String = "Test Widget"

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        createCount += 1
        lastScope = scope
        onCreate(config)
        if (createThrows) error("create failed")
        return widget
    }
}

private class FakeSecretStorage(
    private val values: Map<String, String>,
) : SecretStorage {
    var reads = 0

    override suspend fun read(id: String): String? {
        reads += 1
        return values[id]
    }

    override suspend fun write(id: String, value: String) = Unit

    override suspend fun delete(id: String) = Unit
}

private object FakeWidget : Widget {
    @Composable
    override fun Content(modifier: Modifier) = Unit
}

private object ThrowingDisposeWidget : Widget {
    @Composable
    override fun Content(modifier: Modifier) = Unit

    override fun onDispose() {
        error("dispose failed")
    }
}
