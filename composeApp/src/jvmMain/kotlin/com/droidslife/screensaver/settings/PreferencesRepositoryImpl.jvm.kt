package com.droidslife.screensaver.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    override suspend fun getCurrentCity(): String? = currentSettings().currentCity

    override suspend fun setCurrentCity(city: String) {
        store.update { (it ?: SettingsModel()).copy(currentCity = city) }
    }

    override suspend fun getAutoPlayStatus(): Boolean = currentSettings().autoPlayEnabled

    override suspend fun setAutoPlayStatus(enabled: Boolean) {
        store.update { (it ?: SettingsModel()).copy(autoPlayEnabled = enabled) }
    }

    override suspend fun getShuffleStatus(): Boolean = currentSettings().shuffleEnabled

    override suspend fun setShuffleStatus(enabled: Boolean) {
        store.update { (it ?: SettingsModel()).copy(shuffleEnabled = enabled) }
    }

    override suspend fun getSelectedDesign(): Int = currentSettings().selectedDesignId

    override suspend fun setSelectedDesign(designId: Int) {
        store.update { (it ?: SettingsModel()).copy(selectedDesignId = designId) }
    }

    private suspend fun currentSettings(): SettingsModel = store.get() ?: SettingsModel()
}

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
            json.decodeFromString<SettingsModel>(settingsPath.readText())
        }.getOrDefault(SettingsModel())
    }
}
