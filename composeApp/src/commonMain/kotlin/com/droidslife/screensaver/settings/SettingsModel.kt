package com.droidslife.screensaver.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class Mode { Cinematic, Ambient, Console }

@Serializable
enum class CinematicVariant { Dusk, Noir }

@Serializable
enum class AmbientVariant { Lumen, Borealis }

@Serializable
enum class ConsoleVariant { Standard, Amber }

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

    /**
     * Secret id for the WeatherAPI.com key stored outside settings JSON.
     */
    val weatherApiKeySecretId: String = WEATHER_API_KEY_SECRET_ID,

    val mode: Mode = Mode.Cinematic,
    val cinematicVariant: CinematicVariant = CinematicVariant.Dusk,
    val ambientVariant: AmbientVariant = AmbientVariant.Lumen,
    val consoleVariant: ConsoleVariant = ConsoleVariant.Standard,
    val quieterLumen: Boolean = false,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val widgetLayouts: Map<String, com.droidslife.screensaver.widget.api.GridRect> = emptyMap(),
    val widgetOrder: List<String> = emptyList(),
    val exitOnKeypress: Boolean = true,

    /**
     * When true, Console tiles ignore drag / resize gestures until the user
     * explicitly enters edit mode (press `L`). When false (default), tiles are
     * always editable; the edit-mode banner and size badges stay hidden.
     */
    val dashboardLocked: Boolean = false,

    /** Flipped after the first-run welcome toast is shown so we don't repeat it. */
    val welcomeShown: Boolean = false,
)
