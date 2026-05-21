package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WIDGET_API_VERSION
import com.droidslife.screensaver.widget.api.WidgetConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

class WidgetRegistry(
    builtInFactories: List<WidgetFactory>,
    private val httpClient: HttpClient,
    private val widgetLoader: WidgetLoader = createWidgetLoader(),
) {
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

    private val configs = mutableMapOf<String, JsonObject>()
    private val _descriptors = MutableStateFlow(descriptorById.values.toList())
    private val _instances = MutableStateFlow<Map<String, WidgetInstance>>(emptyMap())

    val descriptors: StateFlow<List<WidgetDescriptor>> = _descriptors
    val instances: StateFlow<Map<String, WidgetInstance>> = _instances

    init {
        reload()
        builtInDescriptors.forEach { enable(it.id) }
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
        if (_instances.value.containsKey(id)) return
        val descriptor = descriptorById[id] ?: return
        val config = WidgetConfig(configs[id] ?: JsonObject(emptyMap()))
        val scope = WidgetScopeImpl(
            widgetId = id,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            httpClient = httpClient,
        )
        val widget = descriptor.factory.create(config, scope)
        _instances.value = _instances.value + (id to WidgetInstance(descriptor, widget, config, scope))
    }

    fun disable(id: String) {
        val instance = _instances.value[id] ?: return
        instance.widget.onDispose()
        _instances.value = _instances.value - id
    }

    fun updateConfig(id: String, config: JsonObject) {
        configs[id] = config
        val wasEnabled = _instances.value.containsKey(id)
        if (wasEnabled) {
            disable(id)
            enable(id)
        }
    }
}
