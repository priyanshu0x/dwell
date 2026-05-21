package com.droidslife.screensaver.widget.host

import co.touchlab.kermit.Logger
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WIDGET_API_VERSION
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.settings.SettingsModel
import com.droidslife.screensaver.settings.SecretStorage
import com.droidslife.screensaver.settings.createSecretStorage
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WidgetRegistry(
    builtInFactories: List<WidgetFactory>,
    private val httpClient: HttpClient,
    private val secretStorage: SecretStorage = createSecretStorage(),
    private val widgetLoader: WidgetLoader = createWidgetLoader(),
) {
    private val logger = Logger.withTag("WidgetRegistry")
    private val builtInDescriptors = builtInFactories
        .filter { it.apiVersion <= WIDGET_API_VERSION }
        .map { factory ->
            WidgetDescriptor(
                id = factory.id,
                displayName = factory.displayName,
                category = factory.category,
                factory = factory,
                source = WidgetSource.BuiltIn,
            )
        }
    private var descriptorById = emptyMap<String, WidgetDescriptor>()

    private val _descriptors = MutableStateFlow(descriptorById.values.toList())
    private val _instances = MutableStateFlow<Map<String, WidgetInstance>>(emptyMap())

    val descriptors: StateFlow<List<WidgetDescriptor>> = _descriptors
    val instances: StateFlow<Map<String, WidgetInstance>> = _instances

    init {
        reload()
    }

    fun reload() {
        val discovered = widgetLoader.discoverAll()
            .filter { it.factory.apiVersion <= WIDGET_API_VERSION }
        descriptorById = (builtInDescriptors + discovered)
            .distinctBy { it.id }
            .associateBy { it.id }
        _descriptors.value = descriptorById.values.toList()
    }

    fun enable(id: String) {
        enable(id, JsonObject(emptyMap()))
    }

    fun enable(id: String, configJson: JsonObject) {
        if (_instances.value.containsKey(id)) return
        val descriptor = descriptorById[id] ?: return
        val config = WidgetConfig(configJson) { key ->
            val secretId = (configJson[key] as? JsonPrimitive)?.content ?: return@WidgetConfig null
            runBlocking { secretStorage.read(secretId) }
        }
        val scope = WidgetScopeImpl(
            widgetId = id,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            httpClient = httpClient,
        )
        val widget = try {
            descriptor.factory.create(config, scope)
        } catch (error: Exception) {
            scope.coroutineScope.cancel()
            logger.e(error) { "Failed to create widget $id" }
            return
        }
        _instances.value = _instances.value + (id to WidgetInstance(descriptor, widget, config, scope))
    }

    fun disable(id: String) {
        val instance = _instances.value[id] ?: return
        try {
            instance.widget.onDispose()
        } catch (error: Exception) {
            logger.e(error) { "Widget $id failed during dispose" }
        } finally {
            instance.scope.coroutineScope.cancel()
            _instances.value = _instances.value - id
        }
    }

    fun updateConfig(id: String, config: JsonObject) {
        val wasEnabled = _instances.value.containsKey(id)
        if (wasEnabled) {
            disable(id)
            enable(id, config)
        }
    }

    fun syncWithSettings(settings: SettingsModel) {
        // WeatherForecast is opt-in per spec § 6.4 / Section 10.
        val offByDefault = setOf("com.droidslife.screensaver.weatherforecast")
        val defaultEnabledIds = builtInDescriptors.map { it.id }.toSet() - offByDefault
        val enabledIds = settings.enabledWidgetIds.ifEmpty { defaultEnabledIds }

        _instances.value.keys
            .filterNot { it in enabledIds }
            .forEach(::disable)

        enabledIds.forEach { id ->
            val config = settings.widgetConfigs[id] ?: JsonObject(emptyMap())
            val existing = _instances.value[id]
            if (existing == null) {
                enable(id, config)
            } else if (existing.config.rawJson != config) {
                updateConfig(id, config)
            }
        }
    }
}
