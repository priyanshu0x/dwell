package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.components.WidgetStatusLine
import com.droidslife.screensaver.components.WidgetStatusSeverity
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.weather.DayForecast
import com.droidslife.screensaver.weather.ForecastState
import com.droidslife.screensaver.weather.WeatherState
import com.droidslife.screensaver.weather.WeatherSyncStatus
import com.droidslife.screensaver.weather.WeatherViewModel
import kotlinx.datetime.DayOfWeek
import com.droidslife.screensaver.weather.providers.WeatherApiProvider
import com.droidslife.screensaver.weather.providers.WttrInProvider
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary

private const val WIDGET_ID = "com.droidslife.screensaver.weather"

class WeatherWidgetFactory(
    private val weatherViewModel: WeatherViewModel,
    private val settingsViewModel: SettingsViewModel,
) : WidgetFactory {
    override val id: String = WIDGET_ID
    override val displayName: String = "Weather"
    override val description: String = "Current conditions + 5-day forecast for a configured city"
    override val category: WidgetCategory = WidgetCategory.INFORMATION
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 3, defaultRows = 2,
        maxCols = 8, maxRows = 4,
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Enum(
            key = "provider",
            label = "Source",
            options = listOf(
                ConfigField.EnumOption(WttrInProvider.ID, "wttr.in (no key)"),
                ConfigField.EnumOption(WeatherApiProvider.ID, "WeatherAPI.com"),
            ),
            default = WttrInProvider.ID,
            help = "wttr.in works out of the box. WeatherAPI.com requires a free API key.",
        ),
        ConfigField.Text(
            key = "city",
            label = "City",
            placeholder = "Rewari",
        ),
        ConfigField.Secret(
            key = "apiKey",
            label = "WeatherAPI.com API key",
            help = "Only needed if you pick WeatherAPI.com as the source.",
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return WeatherWidget(config, weatherViewModel, settingsViewModel)
    }
}

private class WeatherWidget(
    private val config: WidgetConfig,
    private val weatherViewModel: WeatherViewModel,
    private val settingsViewModel: SettingsViewModel,
) : Widget {
    override val preferredSpan: Int = 1

    override fun summary(): WidgetSummary {
        val state = weatherViewModel.state
        return when (state) {
            is WeatherState.Success -> {
                val current = state.current
                WidgetSummary(
                    primaryValue = "${current.tempC.toInt()}°",
                    primaryLabel = "Weather",
                    subtitle = "${current.conditionText} · ${current.city}",
                )
            }
            is WeatherState.Loading -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Weather",
                subtitle = "Loading…",
            )
            is WeatherState.Unconfigured -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Weather",
                subtitle = "API key needed",
            )
            is WeatherState.Error -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Weather",
                subtitle = weatherErrorCopy(weatherViewModel.syncStatus.value, state.message),
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val configuredCity = config.string("city")
        val provider = config.enum("provider", WttrInProvider.ID)
        val apiKeyReference = config.raw("apiKey")

        LaunchedEffect(configuredCity, provider, apiKeyReference) {
            if (configuredCity.isNotBlank()) {
                weatherViewModel.loadWeatherDataForCity(configuredCity, forceRefresh = true)
            }
        }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val forecastState by weatherViewModel.forecast.collectAsState()
            val days = (forecastState as? ForecastState.Loaded)?.days.orEmpty()
            val showForecast = days.isNotEmpty() && maxHeight >= 200.dp
            WeatherStack(
                weatherViewModel = weatherViewModel,
                settingsViewModel = settingsViewModel,
                configuredCity = configuredCity,
                forecastDays = if (showForecast) days else emptyList(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WeatherStack(
    weatherViewModel: WeatherViewModel,
    settingsViewModel: SettingsViewModel,
    configuredCity: String,
    forecastDays: List<DayForecast>,
    modifier: Modifier = Modifier,
) {
    val state = weatherViewModel.state
    val label: String
    val value: String
    val subtitle: String
    val valueIsAccent: Boolean
    val trailing: (@Composable () -> Unit)?
    val syncStatus by weatherViewModel.syncStatus.collectAsState()
    when (state) {
        is WeatherState.Loading -> {
            label = "WEATHER"; value = "—"; subtitle = "Loading…"
            valueIsAccent = false; trailing = null
        }
        is WeatherState.Success -> {
            val c = state.current
            // Prefer the user-typed city — wttr.in's `nearest_area` resolves
            // big-city queries to a neighborhood (e.g. Mumbai → Ballard Estate),
            // which reads as "wrong city" in the label.
            val cityName = configuredCity.ifBlank { c.city }
            label = if (cityName.isNotBlank()) "WEATHER · ${cityName.uppercase()}" else "WEATHER"
            value = "${c.tempC.toInt()}°"
            valueIsAccent = true
            subtitle = buildString {
                append(c.conditionText.ifBlank { "—" })
                c.feelsLikeC?.let { append(" · feels ${it.toInt()}°") }
                c.humidity?.let { append(" · humidity ${it}%") }
            }
            trailing = null
        }
        is WeatherState.Unconfigured -> {
            label = "WEATHER"; value = "—"
            subtitle = "Needs a WeatherAPI.com key — or switch to wttr.in (no key) in settings"
            valueIsAccent = false
            trailing = {
                TextButton(
                    onClick = { settingsViewModel.openWidgetConfig(WIDGET_ID) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        "Open weather settings", fontSize = 11.sp,
                        color = DwellColors.StatusAccent,
                        fontFamily = DwellFonts.interTight(),
                    )
                }
            }
        }
        is WeatherState.Error -> {
            label = "WEATHER"; value = "—"
            subtitle = weatherErrorCopy(syncStatus, state.message)
            valueIsAccent = false
            trailing = {
                IconButton(
                    onClick = {
                        if (configuredCity.isNotBlank()) weatherViewModel.loadWeatherDataForCity(configuredCity)
                        else weatherViewModel.loadWeatherData()
                    },
                    modifier = Modifier.padding(0.dp),
                ) {
                    Icon(Icons.Filled.Refresh, "Retry", tint = DwellColors.TextMid)
                }
            }
        }
    }
    // Status line is decoupled from `state`: a refresh can fail while we keep
    // rendering the cached Success above, so the user always sees a signal
    // instead of stale data masquerading as live.
    val (statusMessage, statusSeverity) = when (syncStatus) {
        WeatherSyncStatus.Healthy -> null to WidgetStatusSeverity.Info
        WeatherSyncStatus.Offline ->
            "Weather offline — showing last update" to WidgetStatusSeverity.Warning
        WeatherSyncStatus.Unconfigured ->
            "Needs a WeatherAPI.com key — or switch to wttr.in (no key)" to WidgetStatusSeverity.Warning
        WeatherSyncStatus.CredentialFailed ->
            "WeatherAPI key/account problem - update it in settings" to WidgetStatusSeverity.Error
        WeatherSyncStatus.Failed ->
            "Weather unavailable — check network/API key" to WidgetStatusSeverity.Error
    }

    val accent = LocalConsoleAccent.current.primary
    Column(modifier = modifier) {
        WidgetHeader(
            label = label,
            settingsId = WIDGET_ID,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Light,
            fontSize = 88.sp,
            lineHeight = 88.sp,
            letterSpacing = (-0.02).em,
            color = if (valueIsAccent) accent else DwellColors.TextHigh,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtitle,
                fontFamily = DwellFonts.interTight(),
                fontSize = 14.sp,
                color = DwellColors.TextMid,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        if (forecastDays.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            ForecastStrip(forecastDays, modifier = Modifier.fillMaxWidth())
        }
        // Only alongside real data: the Unconfigured/Error branches render their
        // own full-tile messaging, so a status line there would just repeat it.
        // With a cached Success showing, the line adds the "stale/offline" context
        // the state machine can't express on its own.
        WidgetStatusLine(
            statusMessage.takeIf { state is WeatherState.Success },
            severity = statusSeverity,
        )
    }
}

private fun weatherErrorCopy(syncStatus: WeatherSyncStatus, fallback: String): String = when (syncStatus) {
    WeatherSyncStatus.CredentialFailed -> "WeatherAPI key/account problem - update it in settings"
    WeatherSyncStatus.Unconfigured -> "Needs a WeatherAPI.com key"
    else -> fallback.takeIf { it.isNotBlank() } ?: "Couldn't load"
}

/**
 * Five-day forecast row appended below the current-weather tile when the
 * widget is tall enough. Compact day cards (Mon · ☀ · 28°/18°) so the row
 * fits in ~78 dp.
 */
@Composable
private fun ForecastStrip(days: List<DayForecast>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.take(5).forEachIndexed { index, day ->
            ForecastDayCard(
                day = day,
                isToday = index == 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ForecastDayCard(
    day: DayForecast,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = LocalConsoleAccent.current.primary
    val bg = if (isToday) accent.copy(alpha = 0.10f) else DwellColors.Surface1.copy(alpha = 0.6f)
    val border = if (isToday) accent.copy(alpha = 0.45f) else DwellColors.Stroke
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (isToday) "TODAY" else weekdayShort(day.date.dayOfWeek).uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) accent else DwellColors.TextLow,
            fontFamily = DwellFonts.interTight(),
        )
        Text(
            text = conditionGlyph(day.conditionCode),
            fontSize = 28.sp,
            color = DwellColors.TextHigh,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${day.high}°",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = DwellColors.TextHigh,
                fontFamily = DwellFonts.jetBrainsMono(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${day.low}°",
                fontSize = 13.sp,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.jetBrainsMono(),
            )
        }
    }
}

private fun weekdayShort(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun conditionGlyph(code: Int): String = when (code) {
    1000 -> "☀"
    1003 -> "⛅"
    1006, 1009 -> "☁"
    1030, 1135, 1147 -> "🌫"
    in 1063..1201 -> "☔"
    in 1204..1237 -> "🌨"
    in 1240..1264 -> "☔"
    in 1273..1282 -> "⛈"
    else -> "☁"
}
