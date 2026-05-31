package com.droidslife.screensaver.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
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
) : ViewModel() {

    // Secret writes are serialized so a per-keystroke sequence ("t" -> "to" ->
    // "tok") can't land out of order and leave a stale prefix in storage.
    private val secretWriteMutex = Mutex()
    private val settingsWriteMutex = Mutex()
    private var secretWriteSequence = 0L
    private val latestWidgetSecretSequence = mutableMapOf<Pair<String, String>, Long>()
    private var settingsDraftBase: SettingsModel? = null
    private var pendingBackendApiKey: String? = null
    private var pendingWeatherApiKey: String? = null
    private val pendingWidgetSecrets = mutableMapOf<Pair<String, String>, String>()

    /**
     * The current settings state.
     */
    var settings by mutableStateOf(SettingsModel())
        private set

    var settingsLoaded by mutableStateOf(false)
        private set

    /**
     * Whether the settings dialog is currently open.
     */
    var isSettingsDialogOpen by mutableStateOf(false)
        private set

    /**
     * Id of the widget whose dedicated config dialog is open, or null when no
     * per-widget dialog is showing. Driven by the gear icon in [WidgetHeader].
     */
    var openWidgetConfigId by mutableStateOf<String?>(null)
        private set

    var savedSecretIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Runtime-only flag for Console layout edit mode. Not persisted.
     */
    // Runtime-only: shows the EDIT LAYOUT banner + size badges. Meaningful only
    // when dashboardLocked is true (toggles via `L`). When unlocked, tiles are
    // always editable but this stays false so no banner / badges clutter.
    var consoleEditMode by mutableStateOf(false)
        private set

    fun toggleConsoleEditMode() {
        consoleEditMode = !consoleEditMode
    }

    fun updateConsoleEditMode(on: Boolean) {
        consoleEditMode = on
    }

    /**
     * Runtime-only flag that forces the Cinematic widget drawer visible (e.g. from the W
     * keyboard shortcut). The drawer's hover/auto-hide logic still applies on top.
     */
    var drawerVisible by mutableStateOf(false)
        private set

    fun toggleDrawer() {
        drawerVisible = !drawerVisible
    }

    fun updateDrawerVisible(on: Boolean) {
        drawerVisible = on
    }

    init {
        // Load settings from the repository
        preferencesRepository.getSettings()
            .onEach {
                settings = it
                settingsLoaded = true
                refreshSecretStatuses(it)
            }
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
        val sequenceKey = widgetId to key
        if (settingsDraftBase != null) {
            pendingWidgetSecrets[sequenceKey] = value
            val currentConfig = settings.widgetConfigs[widgetId] ?: JsonObject(emptyMap())
            updateSettings(settings.copy(widgetConfigs = settings.widgetConfigs + (widgetId to JsonObject(currentConfig + (key to JsonPrimitive(secretId))))))
            return
        }
        val sequence = ++secretWriteSequence
        latestWidgetSecretSequence[sequenceKey] = sequence
        val currentConfig = settings.widgetConfigs[widgetId] ?: JsonObject(emptyMap())
        updateSettings(settings.copy(widgetConfigs = settings.widgetConfigs + (widgetId to JsonObject(currentConfig + (key to JsonPrimitive(secretId))))))
        writeSecret(secretId, value) {
            if (latestWidgetSecretSequence[sequenceKey] == sequence) {
                bumpWidgetSecretVersion(secretId)
            }
        }
    }

    fun widgetSecretReference(widgetId: String, key: String): String? =
        (settings.widgetConfigs[widgetId]?.get(key) as? JsonPrimitive)
            ?.content
            ?.takeIf { it.isNotBlank() }

    // All secret persistence funnels through here so writes stay serialized.
    private fun writeSecret(
        secretId: String,
        value: String,
        onSaved: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            secretWriteMutex.withLock {
                if (value.isBlank()) {
                    secretStorage.delete(secretId)
                    savedSecretIds -= secretId
                } else {
                    secretStorage.write(secretId, value)
                    savedSecretIds += secretId
                }
            }
            onSaved?.invoke()
        }
    }

    private fun bumpWidgetSecretVersion(secretId: String) {
        val nextVersion = (settings.widgetSecretVersions[secretId] ?: 0L) + 1L
        updateSettings(settings.copy(widgetSecretVersions = settings.widgetSecretVersions + (secretId to nextVersion)))
    }

    fun setIdleTimeoutSeconds(seconds: Int) {
        updateSettings(settings.copy(idleTimeoutSeconds = seconds.coerceIn(30, 240 * 60)))
    }

    fun setTrayIconEnabled(enabled: Boolean) {
        updateSettings(settings.copy(trayIconEnabled = enabled))
    }

    fun setStartWithSystem(enabled: Boolean) {
        updateSettings(settings.copy(startWithSystem = enabled))
        if (settingsDraftBase == null) {
            viewModelScope.launch {
                startupRegistration.setEnabled(enabled)
            }
        }
    }

    fun setBackendBaseUrl(url: String) {
        updateSettings(settings.copy(backendBaseUrl = url))
    }

    fun updateBackendApiKey(value: String) {
        if (settingsDraftBase != null) {
            pendingBackendApiKey = value
            return
        }
        writeSecret(settings.backendApiKeySecretId, value)
    }

    fun updateWeatherApiKey(value: String) {
        if (settingsDraftBase != null) {
            pendingWeatherApiKey = value
            return
        }
        writeSecret(settings.weatherApiKeySecretId, value)
    }

    fun isSecretSaved(secretId: String): Boolean = secretId in savedSecretIds

    fun effectiveEnabledWidgetIds(defaultIds: Set<String> = defaultWidgetIds): Set<String> {
        return settings.enabledWidgetIds.ifEmpty { defaultIds }
    }

    fun setMode(mode: Mode) {
        updateSettings(settings.copy(mode = mode))
    }

    fun setCinematicVariant(variant: CinematicVariant) {
        updateSettings(settings.copy(cinematicVariant = variant))
    }

    fun setAmbientVariant(variant: AmbientVariant) {
        updateSettings(settings.copy(ambientVariant = variant))
    }

    fun setConsoleVariant(variant: ConsoleVariant) {
        updateSettings(settings.copy(consoleVariant = variant))
    }

    fun setConsoleWidgetBorderStyle(style: ConsoleWidgetBorderStyle) {
        updateSettings(settings.copy(consoleWidgetBorderStyle = style))
    }

    fun setQuieterLumen(enabled: Boolean) {
        updateSettings(settings.copy(quieterLumen = enabled))
    }

    fun setShowSeconds(enabled: Boolean) {
        updateSettings(settings.copy(showSeconds = enabled))
    }

    fun setShowDate(enabled: Boolean) {
        updateSettings(settings.copy(showDate = enabled))
    }

    fun setExitOnKeypress(enabled: Boolean) {
        updateSettings(settings.copy(exitOnKeypress = enabled))
    }

    fun setDashboardLocked(locked: Boolean) {
        updateSettings(settings.copy(dashboardLocked = locked))
        if (!locked) consoleEditMode = false
    }

    /** Mark the first-run welcome toast as shown so it doesn't repeat. */
    fun markWelcomeShown() {
        if (!settings.welcomeShown) updateSettings(settings.copy(welcomeShown = true))
    }

    fun setWidgetLayout(widgetId: String, rect: com.droidslife.screensaver.widget.api.GridRect) {
        updateSettings(settings.copy(widgetLayouts = settings.widgetLayouts + (widgetId to rect)))
    }

    fun resetWidgetLayouts() {
        updateSettings(settings.copy(widgetLayouts = emptyMap()))
    }

    fun cycleMode() {
        val modes = Mode.entries
        val next = modes[(modes.indexOf(settings.mode) + 1) % modes.size]
        setMode(next)
    }

    fun cycleVariant() {
        val newSettings = when (settings.mode) {
            Mode.Cinematic -> {
                val variants = CinematicVariant.entries
                val next = variants[(variants.indexOf(settings.cinematicVariant) + 1) % variants.size]
                settings.copy(cinematicVariant = next)
            }
            Mode.Ambient -> {
                val variants = AmbientVariant.entries
                val next = variants[(variants.indexOf(settings.ambientVariant) + 1) % variants.size]
                settings.copy(ambientVariant = next)
            }
            Mode.Console -> {
                val variants = ConsoleVariant.entries
                val next = variants[(variants.indexOf(settings.consoleVariant) + 1) % variants.size]
                settings.copy(consoleVariant = next)
            }
        }
        updateSettings(newSettings)
    }

    /**
     * Updates the settings in the repository.
     *
     * Eagerly updates the in-memory [settings] state so the dashboard reflects
     * the change on the next frame. Disk writes stay serialized so dependent
     * updates (for example a widget secret reference followed by its revision)
     * cannot be re-emitted out of order.
     */
    private fun updateSettings(newSettings: SettingsModel) {
        settings = newSettings
        if (settingsDraftBase != null) return
        viewModelScope.launch {
            settingsWriteMutex.withLock {
                preferencesRepository.updateSettings(newSettings)
            }
        }
    }

    /**
     * Opens the settings dialog.
     */
    fun openSettingsDialog() {
        beginSettingsDraft()
        isSettingsDialogOpen = true
    }

    /**
     * Closes the settings dialog.
     */
    fun closeSettingsDialog() {
        cancelSettingsDraft()
        isSettingsDialogOpen = false
    }

    fun openWidgetConfig(widgetId: String) {
        beginSettingsDraft()
        openWidgetConfigId = widgetId
    }

    fun closeWidgetConfig() {
        cancelSettingsDraft()
        openWidgetConfigId = null
    }

    fun beginSettingsDraft() {
        if (settingsDraftBase == null) {
            settingsDraftBase = settings
            pendingBackendApiKey = null
            pendingWeatherApiKey = null
            pendingWidgetSecrets.clear()
        }
    }

    fun saveSettingsDraft() {
        val snapshot = settings
        settingsDraftBase = null
        viewModelScope.launch {
            settingsWriteMutex.withLock {
                preferencesRepository.updateSettings(snapshot)
            }
            startupRegistration.setEnabled(snapshot.startWithSystem)
        }
        pendingBackendApiKey?.let { writeSecret(snapshot.backendApiKeySecretId, it) }
        pendingWeatherApiKey?.let { writeSecret(snapshot.weatherApiKeySecretId, it) }
        pendingWidgetSecrets.forEach { (key, value) ->
            val (widgetId, configKey) = key
            val secretId = widgetSecretId(widgetId, configKey)
            writeSecret(secretId, value) { bumpWidgetSecretVersion(secretId) }
        }
        pendingBackendApiKey = null
        pendingWeatherApiKey = null
        pendingWidgetSecrets.clear()
    }

    fun cancelSettingsDraft() {
        val base = settingsDraftBase ?: return
        settingsDraftBase = null
        pendingBackendApiKey = null
        pendingWeatherApiKey = null
        pendingWidgetSecrets.clear()
        settings = base
    }

    private suspend fun refreshSecretStatuses(currentSettings: SettingsModel) {
        val ids = buildSet {
            add(currentSettings.backendApiKeySecretId)
            add(currentSettings.weatherApiKeySecretId)
            currentSettings.widgetConfigs.values
                .flatMap { it.values }
                .mapNotNull { it.secretIdOrNull() }
                .forEach(::add)
        }
        savedSecretIds = ids
            .filter { secretStorage.read(it)?.isNotBlank() == true }
            .toSet()
    }

    private fun JsonElement.secretIdOrNull(): String? {
        val value = (this as? JsonPrimitive)?.content ?: return null
        return value.takeIf { it.startsWith("widget.") }
    }

    private companion object {
        val defaultWidgetIds = setOf(
            "com.droidslife.screensaver.clock",
            "com.droidslife.screensaver.todos",
            "com.droidslife.screensaver.expenses",
            "com.droidslife.screensaver.calendar",
        )
    }
}
