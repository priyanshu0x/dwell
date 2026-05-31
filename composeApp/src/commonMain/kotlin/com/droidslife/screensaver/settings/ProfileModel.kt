package com.droidslife.screensaver.settings

import com.droidslife.screensaver.widget.api.GridRect
import kotlinx.serialization.Serializable

const val PROFILE_CURRENT_ID = "current"
const val PROFILE_WORK_ID = "work"
const val PROFILE_FOCUS_ID = "focus"
const val PROFILE_NIGHT_ID = "night"
const val PROFILE_PRESENTATION_ID = "presentation"
const val PROFILE_DESK_CLOCK_ID = "desk-clock"

@Serializable
enum class ProfileKind { BuiltIn, Custom }

@Serializable
data class ProfileModel(
    val id: String,
    val name: String,
    val description: String,
    val kind: ProfileKind,
    val version: Int = 1,
    val settings: ProfileSettings,
)

@Serializable
data class ProfileSettings(
    val mode: Mode = Mode.Cinematic,
    val cinematicVariant: CinematicVariant = CinematicVariant.Dusk,
    val ambientVariant: AmbientVariant = AmbientVariant.Lumen,
    val consoleVariant: ConsoleVariant = ConsoleVariant.Standard,
    val consoleWidgetBorderStyle: ConsoleWidgetBorderStyle = ConsoleWidgetBorderStyle.Bordered,
    val enabledWidgetIds: Set<String> = emptySet(),
    val widgetLayouts: Map<String, GridRect> = emptyMap(),
    val widgetOrder: List<String> = emptyList(),
    val idleTimeoutSeconds: Int = 5 * 60,
    val is24HourFormat: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val quieterLumen: Boolean = false,
    val exitOnKeypress: Boolean = true,
    val rightClickHidesDashboard: Boolean = true,
    val dashboardLocked: Boolean = false,
) {
    fun applyTo(settings: SettingsModel): SettingsModel = settings.copy(
        mode = mode,
        cinematicVariant = cinematicVariant,
        ambientVariant = ambientVariant,
        consoleVariant = consoleVariant,
        consoleWidgetBorderStyle = consoleWidgetBorderStyle,
        enabledWidgetIds = enabledWidgetIds,
        widgetLayouts = widgetLayouts,
        widgetOrder = widgetOrder,
        idleTimeoutSeconds = idleTimeoutSeconds.coerceIn(30, 240 * 60),
        is24HourFormat = is24HourFormat,
        showSeconds = showSeconds,
        showDate = showDate,
        quieterLumen = quieterLumen,
        exitOnKeypress = exitOnKeypress,
        rightClickHidesDashboard = rightClickHidesDashboard,
        dashboardLocked = dashboardLocked,
    )
}

fun SettingsModel.toProfileSettings(): ProfileSettings = ProfileSettings(
    mode = mode,
    cinematicVariant = cinematicVariant,
    ambientVariant = ambientVariant,
    consoleVariant = consoleVariant,
    consoleWidgetBorderStyle = consoleWidgetBorderStyle,
    enabledWidgetIds = enabledWidgetIds,
    widgetLayouts = widgetLayouts,
    widgetOrder = widgetOrder,
    idleTimeoutSeconds = idleTimeoutSeconds,
    is24HourFormat = is24HourFormat,
    showSeconds = showSeconds,
    showDate = showDate,
    quieterLumen = quieterLumen,
    exitOnKeypress = exitOnKeypress,
    rightClickHidesDashboard = rightClickHidesDashboard,
    dashboardLocked = dashboardLocked,
)

fun profileCatalogFor(settings: SettingsModel): List<ProfileModel> {
    val currentProfile = ProfileModel(
        id = PROFILE_CURRENT_ID,
        name = "Current",
        description = "Your current manual setup",
        kind = ProfileKind.Custom,
        settings = settings.toProfileSettings(),
    )
    return listOf(currentProfile) + builtInProfiles
}

fun profileById(settings: SettingsModel, profileId: String): ProfileModel? =
    profileCatalogFor(settings).firstOrNull { it.id == profileId }

private val builtInProfiles: List<ProfileModel> = listOf(
    ProfileModel(
        id = PROFILE_WORK_ID,
        name = "Work",
        description = "Console layout with planning widgets visible",
        kind = ProfileKind.BuiltIn,
        settings = ProfileSettings(
            mode = Mode.Console,
            consoleVariant = ConsoleVariant.Dark,
            consoleWidgetBorderStyle = ConsoleWidgetBorderStyle.Bordered,
            enabledWidgetIds = setOf(CLOCK_WIDGET_ID, TODOS_WIDGET_ID, CALENDAR_WIDGET_ID, WEATHER_WIDGET_ID),
            idleTimeoutSeconds = 5 * 60,
        ),
    ),
    ProfileModel(
        id = PROFILE_FOCUS_ID,
        name = "Focus",
        description = "Task-forward, calmer, and pomodoro-ready",
        kind = ProfileKind.BuiltIn,
        settings = ProfileSettings(
            mode = Mode.Console,
            consoleVariant = ConsoleVariant.Dark,
            consoleWidgetBorderStyle = ConsoleWidgetBorderStyle.Borderless,
            enabledWidgetIds = setOf(CLOCK_WIDGET_ID, TODOS_WIDGET_ID, POMODORO_WIDGET_ID),
            idleTimeoutSeconds = 10 * 60,
            dashboardLocked = true,
        ),
    ),
    ProfileModel(
        id = PROFILE_NIGHT_ID,
        name = "Night",
        description = "Ambient, quiet, and low information density",
        kind = ProfileKind.BuiltIn,
        settings = ProfileSettings(
            mode = Mode.Ambient,
            ambientVariant = AmbientVariant.Borealis,
            enabledWidgetIds = setOf(CLOCK_WIDGET_ID, WEATHER_WIDGET_ID),
            idleTimeoutSeconds = 15 * 60,
            showSeconds = false,
            showDate = true,
        ),
    ),
    ProfileModel(
        id = PROFILE_PRESENTATION_ID,
        name = "Presentation",
        description = "Privacy-safe widgets and no dense personal lists",
        kind = ProfileKind.BuiltIn,
        settings = ProfileSettings(
            mode = Mode.Console,
            consoleVariant = ConsoleVariant.Dark,
            consoleWidgetBorderStyle = ConsoleWidgetBorderStyle.Borderless,
            enabledWidgetIds = setOf(CLOCK_WIDGET_ID, WEATHER_WIDGET_ID, IDLE_WIDGET_ID),
            idleTimeoutSeconds = 240 * 60,
            rightClickHidesDashboard = true,
            dashboardLocked = true,
        ),
    ),
    ProfileModel(
        id = PROFILE_DESK_CLOCK_ID,
        name = "Desk Clock",
        description = "Clock and weather first, with low interaction noise",
        kind = ProfileKind.BuiltIn,
        settings = ProfileSettings(
            mode = Mode.Cinematic,
            cinematicVariant = CinematicVariant.Noir,
            enabledWidgetIds = setOf(CLOCK_WIDGET_ID, WEATHER_WIDGET_ID),
            idleTimeoutSeconds = 5 * 60,
            showDate = true,
        ),
    ),
)

private const val CLOCK_WIDGET_ID = "com.droidslife.screensaver.clock"
private const val WEATHER_WIDGET_ID = "com.droidslife.screensaver.weather"
private const val TODOS_WIDGET_ID = "com.droidslife.screensaver.todos"
private const val CALENDAR_WIDGET_ID = "com.droidslife.screensaver.calendar"
private const val IDLE_WIDGET_ID = "com.droidslife.screensaver.idle"
private const val POMODORO_WIDGET_ID = "com.droidslife.screensaver.pomodoro"
