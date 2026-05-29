package com.droidslife.screensaver.widget.host

import co.touchlab.kermit.Logger
import com.droidslife.screensaver.widget.api.ConfigField
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val syncMutex = Mutex()

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

    suspend fun enable(id: String) {
        enable(id, JsonObject(emptyMap()))
    }

    suspend fun enable(
        id: String,
        configJson: JsonObject,
        secretVersions: Map<String, Long> = emptyMap(),
    ) {
        if (_instances.value.containsKey(id)) return
        val descriptor = descriptorById[id] ?: return
        val secretValues = resolveSecrets(configJson, descriptor.factory.configSchema)
        val config = WidgetConfig(configJson) { key ->
            val secretId = (configJson[key] as? JsonPrimitive)?.content ?: return@WidgetConfig null
            secretValues[secretId]
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
        _instances.value = _instances.value + (id to WidgetInstance(descriptor, widget, config, secretVersions, scope))
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

    suspend fun updateConfig(id: String, config: JsonObject) {
        val wasEnabled = _instances.value.containsKey(id)
        if (wasEnabled) {
            disable(id)
            enable(id, config)
        }
    }

    suspend fun syncWithSettings(settings: SettingsModel) = syncMutex.withLock {
        // Pomodoro + Idle counter are opt-in (specialty use). Standalone
        // Weather is opt-in because the default Clock tile now includes it.
        val offByDefault = setOf(
            "com.droidslife.screensaver.pomodoro",
            "com.droidslife.screensaver.idle",
            "com.droidslife.screensaver.weather",
        )
        val defaultEnabledIds = builtInDescriptors.map { it.id }.toSet() - offByDefault
        val enabledIds = settings.enabledWidgetIds.ifEmpty { defaultEnabledIds }

        _instances.value.keys
            .filterNot { it in enabledIds }
            .forEach(::disable)

        enabledIds.forEach { id ->
            val config = settings.widgetConfigs[id] ?: JsonObject(emptyMap())
            val secretVersions = secretVersionsFor(config, settings)
            val existing = _instances.value[id]
            if (existing == null) {
                enable(id, config, secretVersions)
            } else if (existing.config.rawJson != config || existing.secretVersions != secretVersions) {
                disable(id)
                enable(id, config, secretVersions)
            }
        }
    }

    private suspend fun resolveSecrets(
        configJson: JsonObject,
        configSchema: List<ConfigField>,
    ): Map<String, String> {
        val secretKeys = configSchema
            .filterIsInstance<ConfigField.Secret>()
            .map { it.key }
            .toSet()
        val secretIds = configJson.entries
            .mapNotNull { (key, element) ->
                val value = (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                value.takeIf { key in secretKeys || it.startsWith(WIDGET_SECRET_PREFIX) }
            }
            .distinct()
        return secretIds.mapNotNull { secretId ->
            runCatching { secretStorage.read(secretId) }
                .getOrNull()
                ?.let { secretId to it }
        }.toMap()
    }

    private fun secretVersionsFor(
        config: JsonObject,
        settings: SettingsModel,
    ): Map<String, Long> =
        config.values
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { it.startsWith(WIDGET_SECRET_PREFIX) }
            .associateWith { settings.widgetSecretVersions[it] ?: 0L }

    private companion object {
        const val WIDGET_SECRET_PREFIX = "widget."
    }
}
