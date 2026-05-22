package com.droidslife.screensaver.settings

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
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
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
 * Currently rewrites legacy `currentCity` field into the Weather widget's
 * own config under `widgetConfigs["com.droidslife.screensaver.weather"]["city"]`,
 * then drops `currentCity` from the root. Deprecated fields (selectedDesignId,
 * autoPlayEnabled, shuffleEnabled) are simply ignored at decode time because
 * the Json decoder is configured with `ignoreUnknownKeys = true`.
 */
private fun migrateJson(raw: JsonElement): JsonElement {
    if (raw !is JsonObject) return raw
    val currentCity = (raw["currentCity"] as? JsonPrimitive)?.contentOrNull
    if (currentCity.isNullOrBlank()) {
        // Still strip the field so we don't keep rewriting it.
        return if (raw.containsKey("currentCity")) JsonObject(raw - "currentCity") else raw
    }
    val weatherKey = "com.droidslife.screensaver.weather"
    val widgetConfigs = (raw["widgetConfigs"] as? JsonObject) ?: JsonObject(emptyMap())
    val existingWeatherCfg = (widgetConfigs[weatherKey] as? JsonObject) ?: JsonObject(emptyMap())
    val merged = if (existingWeatherCfg["city"] != null) {
        // Weather already has a city; just drop currentCity.
        raw - "currentCity"
    } else {
        val newWeatherCfg = JsonObject(existingWeatherCfg + ("city" to JsonPrimitive(currentCity)))
        val newWidgetConfigs = JsonObject(widgetConfigs + (weatherKey to newWeatherCfg))
        (raw - "currentCity") + ("widgetConfigs" to newWidgetConfigs)
    }
    return JsonObject(merged)
}
