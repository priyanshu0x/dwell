package com.droidslife.screensaver.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droidslife.screensaver.clock.ClockViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ViewModel for managing application settings.
 * @param preferencesRepository The repository for storing and retrieving preferences.
 */
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val secretStorage: SecretStorage = createSecretStorage(),
    private val startupRegistration: StartupRegistration = createStartupRegistration(),
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The current settings state.
     */
    var settings by mutableStateOf(SettingsModel())
        private set

    /**
     * Whether the settings dialog is currently open.
     */
    var isSettingsDialogOpen by mutableStateOf(false)
        private set

    init {
        // Load settings from the repository
        preferencesRepository.getSettings()
            .onEach { settings = it }
            .launchIn(viewModelScope)
    }

    /**
     * Toggles the theme between light and dark.
     * @return The new theme state (true for dark, false for light).
     */
    fun toggleTheme(): Boolean {
        val newSettings = settings.copy(isDarkTheme = !settings.isDarkTheme)
        updateSettings(newSettings)
        return newSettings.isDarkTheme
    }

    /**
     * Sets the theme to the specified value.
     * @param isDark Whether to use dark theme.
     */
    fun setTheme(isDark: Boolean) {
        updateSettings(settings.copy(isDarkTheme = isDark))
    }

    /**
     * Toggles the clock format between 12-hour and 24-hour.
     * @return The new clock format state (true for 24-hour, false for 12-hour).
     */
    fun toggleClockFormat(): Boolean {
        val newSettings = settings.copy(is24HourFormat = !settings.is24HourFormat)
        updateSettings(newSettings)
        return newSettings.is24HourFormat
    }

    /**
     * Sets the clock format to the specified value.
     * @param is24Hour Whether to use 24-hour format.
     */
    fun setClockFormat(is24Hour: Boolean) {
        updateSettings(settings.copy(is24HourFormat = is24Hour))
    }

    /**
     * Sets the current city for weather display.
     * @param city The city name.
     */
    fun setCurrentCity(city: String) {
        updateSettings(settings.copy(currentCity = city))
    }

    /**
     * Toggles the auto play status.
     * @return The new auto play status.
     */
    fun toggleAutoPlay(): Boolean {
        val newSettings = settings.copy(autoPlayEnabled = !settings.autoPlayEnabled)
        updateSettings(newSettings)
        return newSettings.autoPlayEnabled
    }

    /**
     * Sets the auto play status.
     * @param enabled Whether auto play should be enabled.
     */
    fun setAutoPlay(enabled: Boolean) {
        updateSettings(settings.copy(autoPlayEnabled = enabled))
    }

    /**
     * Toggles the shuffle status.
     * @return The new shuffle status.
     */
    fun toggleShuffle(): Boolean {
        val newSettings = settings.copy(shuffleEnabled = !settings.shuffleEnabled)
        updateSettings(newSettings)
        return newSettings.shuffleEnabled
    }

    /**
     * Sets the shuffle status.
     * @param enabled Whether shuffle should be enabled.
     */
    fun setShuffle(enabled: Boolean) {
        updateSettings(settings.copy(shuffleEnabled = enabled))
    }

    /**
     * Sets the currently selected design.
     * @param designId The design ID.
     */
    fun setSelectedDesign(designId: Int) {
        if (designId in 1..ClockViewModel.DESIGN_COUNT) {
            updateSettings(settings.copy(selectedDesignId = designId))
        }
    }

    fun setWidgetEnabled(widgetId: String, enabled: Boolean) {
        val current = effectiveEnabledWidgetIds().toMutableSet()
        if (enabled) {
            current += widgetId
        } else {
            current -= widgetId
        }
        updateSettings(settings.copy(enabledWidgetIds = current))
    }

    fun updateWidgetConfig(widgetId: String, config: JsonObject) {
        updateSettings(settings.copy(widgetConfigs = settings.widgetConfigs + (widgetId to config)))
    }

    fun updateWidgetSecret(widgetId: String, key: String, value: String) {
        val secretId = widgetSecretId(widgetId, key)
        val currentConfig = settings.widgetConfigs[widgetId] ?: JsonObject(emptyMap())
        updateSettings(settings.copy(widgetConfigs = settings.widgetConfigs + (widgetId to JsonObject(currentConfig + (key to JsonPrimitive(secretId))))))
        viewModelScope.launch {
            if (value.isBlank()) {
                secretStorage.delete(secretId)
            } else {
                secretStorage.write(secretId, value)
            }
        }
    }

    fun setIdleTimeoutMinutes(minutes: Int) {
        updateSettings(settings.copy(idleTimeoutMinutes = minutes.coerceIn(1, 240)))
    }

    fun setTrayIconEnabled(enabled: Boolean) {
        updateSettings(settings.copy(trayIconEnabled = enabled))
    }

    fun setStartWithSystem(enabled: Boolean) {
        updateSettings(settings.copy(startWithSystem = enabled))
        viewModelScope.launch {
            startupRegistration.setEnabled(enabled)
        }
    }

    fun setBackendBaseUrl(url: String) {
        updateSettings(settings.copy(backendBaseUrl = url))
    }

    fun updateBackendApiKey(value: String) {
        viewModelScope.launch {
            if (value.isBlank()) {
                secretStorage.delete(settings.backendApiKeySecretId)
            } else {
                secretStorage.write(settings.backendApiKeySecretId, value)
            }
        }
    }

    fun effectiveEnabledWidgetIds(defaultIds: Set<String> = defaultWidgetIds): Set<String> {
        return settings.enabledWidgetIds.ifEmpty { defaultIds }
    }

    /**
     * Updates the settings in the repository.
     * @param newSettings The new settings.
     */
    private fun updateSettings(newSettings: SettingsModel) {
        viewModelScope.launch {
            preferencesRepository.updateSettings(newSettings)
        }
    }

    /**
     * Opens the settings dialog.
     */
    fun openSettingsDialog() {
        isSettingsDialogOpen = true
    }

    /**
     * Closes the settings dialog.
     */
    fun closeSettingsDialog() {
        isSettingsDialogOpen = false
    }

    private companion object {
        val defaultWidgetIds = setOf(
            "com.droidslife.screensaver.clock",
            "com.droidslife.screensaver.weather",
            "com.droidslife.screensaver.todos",
            "com.droidslife.screensaver.expenses",
        )
    }
}
