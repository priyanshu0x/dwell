package com.droidslife.screensaver.settings

import com.droidslife.screensaver.serialization.DwellJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import io.github.xxfast.kstore.Codec
import io.github.xxfast.kstore.storeOf
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

actual fun createPreferencesRepository(): PreferencesRepository = PreferencesRepositoryImpl()

private class PreferencesRepositoryImpl : PreferencesRepository {
    private val settingsPath: Path = Path.of(
        System.getProperty("user.home"),
        ".screensaver",
        "settings.json",
    )
    private val json = DwellJson.PrettyPersisted
    private val store = storeOf(
        codec = SettingsFileCodec(settingsPath, json),
        default = SettingsModel(),
    )

    override fun getSettings(): Flow<SettingsModel> = store.updates.map { it ?: SettingsModel() }

    override suspend fun updateSettings(settings: SettingsModel) {
        store.set(settings)
    }

    override suspend fun getCurrentCity(): String? {
        val cfg = currentSettings().widgetConfigs[WEATHER_WIDGET_ID] ?: return null
        return (cfg["city"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    override suspend fun setCurrentCity(city: String) {
        store.update { existing ->
            val current = existing ?: SettingsModel()
            val cfg = current.widgetConfigs[WEATHER_WIDGET_ID] ?: JsonObject(emptyMap())
            val newCfg = JsonObject(cfg + ("city" to JsonPrimitive(city)))
            current.copy(widgetConfigs = current.widgetConfigs + (WEATHER_WIDGET_ID to newCfg))
        }
    }

    private suspend fun currentSettings(): SettingsModel = store.get() ?: SettingsModel()
}

private const val WEATHER_WIDGET_ID = "com.droidslife.screensaver.weather"

private class SettingsFileCodec(
    private val settingsPath: Path,
    private val json: Json,
) : Codec<SettingsModel> {
    override suspend fun encode(value: SettingsModel?) {
        val settings = value ?: SettingsModel()
        settingsPath.parent?.createDirectories()
        settingsPath.writeText(json.encodeToString(settings))
    }

    override suspend fun decode(): SettingsModel? {
        return runCatching {
            if (!settingsPath.exists()) return SettingsModel()
            val raw = json.parseToJsonElement(settingsPath.readText())
            val migrated = migrateJson(raw)
            json.decodeFromJsonElement<SettingsModel>(migrated)
        }.getOrDefault(SettingsModel())
    }
}

/**
 * Migrate a raw settings JsonElement before deserialization.
 *
 * Currently rewrites legacy `currentCity` into the Weather widget config and
 * `idleTimeoutMinutes` into `idleTimeoutSeconds`. Deprecated fields
 * (selectedDesignId, autoPlayEnabled, shuffleEnabled) are simply ignored at
 * decode time because the Json decoder is configured with `ignoreUnknownKeys = true`.
 */
private fun migrateJson(raw: JsonElement): JsonElement {
    if (raw !is JsonObject) return raw
    var migrated = raw

    val legacyIdleMinutes = (migrated["idleTimeoutMinutes"] as? JsonPrimitive)
        ?.contentOrNull
        ?.toIntOrNull()
    if (legacyIdleMinutes != null && migrated["idleTimeoutSeconds"] == null) {
        migrated = JsonObject(
            migrated + ("idleTimeoutSeconds" to JsonPrimitive((legacyIdleMinutes * 60).coerceIn(30, 240 * 60)))
        )
    }
    if (migrated.containsKey("idleTimeoutMinutes")) {
        migrated = JsonObject(migrated - "idleTimeoutMinutes")
    }

    val currentCity = (migrated["currentCity"] as? JsonPrimitive)?.contentOrNull
    if (currentCity.isNullOrBlank()) {
        return if (migrated.containsKey("currentCity")) JsonObject(migrated - "currentCity") else migrated
    }

    val weatherKey = "com.droidslife.screensaver.weather"
    val widgetConfigs = (migrated["widgetConfigs"] as? JsonObject) ?: JsonObject(emptyMap())
    val existingWeatherCfg = (widgetConfigs[weatherKey] as? JsonObject) ?: JsonObject(emptyMap())
    val merged = if (existingWeatherCfg["city"] != null) {
        // Weather already has a city; just drop currentCity.
        migrated - "currentCity"
    } else {
        val newWeatherCfg = JsonObject(existingWeatherCfg + ("city" to JsonPrimitive(currentCity)))
        val newWidgetConfigs = JsonObject(widgetConfigs + (weatherKey to newWeatherCfg))
        (migrated - "currentCity") + ("widgetConfigs" to newWidgetConfigs)
    }
    return JsonObject(merged)
}
