package com.droidslife.screensaver.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Model class for application settings.
 */
@Serializable
data class SettingsModel(
    /**
     * Whether the app should use dark theme.
     */
    val isDarkTheme: Boolean = true,

    /**
     * Whether the clock should use 24-hour format.
     * If false, 12-hour format with AM/PM will be used.
     */
    val is24HourFormat: Boolean = true,

    /**
     * The current city for weather display.
     * If null, the app will try to determine the city based on the timezone.
     */
    val currentCity: String? = null,

    /**
     * Whether auto play is enabled.
     */
    val autoPlayEnabled: Boolean = false,

    /**
     * Whether shuffle is enabled.
     */
    val shuffleEnabled: Boolean = false,

    /**
     * The ID of the currently selected design.
     */
    val selectedDesignId: Int = 1,

    /**
     * Enabled widget IDs. Empty means use the built-in default set.
     */
    val enabledWidgetIds: Set<String> = emptySet(),

    /**
     * Per-widget JSON configuration keyed by widget ID.
     */
    val widgetConfigs: Map<String, JsonObject> = emptyMap(),

    /**
     * Minutes of OS idle time before the daemon opens the dashboard.
     */
    val idleTimeoutMinutes: Int = 5,

    /**
     * Whether the daemon should try to show a system tray icon.
     */
    val trayIconEnabled: Boolean = true,

    /**
     * Whether the app should register itself for login startup.
     */
    val startWithSystem: Boolean = false,

    /**
     * Optional backend base URL for future sync-capable widgets.
     */
    val backendBaseUrl: String = "",

    /**
     * Secret id for the backend API token stored outside settings JSON.
     */
    val backendApiKeySecretId: String = "backend.apiKey",
)
