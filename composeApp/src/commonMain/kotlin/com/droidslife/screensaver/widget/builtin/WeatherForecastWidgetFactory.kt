package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.weather.DayForecast
import com.droidslife.screensaver.weather.ForecastState
import com.droidslife.screensaver.weather.WeatherViewModel
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.datetime.DayOfWeek

class WeatherForecastWidgetFactory(
    private val weatherViewModel: WeatherViewModel,
    private val settingsViewModel: SettingsViewModel,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.weatherforecast"
    override val displayName: String = "Weather Forecast"
    override val description: String = "5-day weather forecast"
    override val category: WidgetCategory = WidgetCategory.INFORMATION
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 8, minRows = 1,
        defaultCols = 12, defaultRows = 1,
        maxCols = 12, maxRows = 2,
    )
    override val configSchema: List<ConfigField> = emptyList()

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return WeatherForecastWidget(weatherViewModel, settingsViewModel)
    }
}

private class WeatherForecastWidget(
    private val weatherViewModel: WeatherViewModel,
    private val settingsViewModel: SettingsViewModel,
) : Widget {

    override fun summary(): WidgetSummary {
        val state = weatherViewModel.forecast.value
        return when (state) {
            is ForecastState.Loaded -> {
                val days = state.days
                if (days.isEmpty()) {
                    WidgetSummary(primaryValue = "—", primaryLabel = "Forecast")
                } else {
                    val first = days.first()
                    WidgetSummary(
                        primaryValue = "${first.high}°/${first.low}°",
                        primaryLabel = "Forecast",
                        subtitle = days.take(5).joinToString(" · ") { "H${it.high}" },
                    )
                }
            }
            ForecastState.Loading -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Forecast",
                subtitle = "Loading…",
            )
            ForecastState.Failed -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Forecast",
                subtitle = "Couldn't load",
            )
            ForecastState.Unconfigured -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Forecast",
                subtitle = "API key needed",
            )
        }
    }

    @Composable
    override fun Render(target: WidgetRenderTarget, scope: WidgetScope, modifier: Modifier) {
        EnsureForecastLoaded()
        val state by weatherViewModel.forecast.collectAsState()
        when (target) {
            WidgetRenderTarget.Tile -> ForecastTile(state, modifier)
            WidgetRenderTarget.Chip -> ForecastChip(state, modifier)
            WidgetRenderTarget.Minimal -> ForecastMinimal(state, modifier)
        }
    }

    @Composable
    private fun EnsureForecastLoaded() {
        // On widget mount, if no forecast has been loaded yet, kick a refresh.
        val selectedCity = weatherViewModel.selectedCity
        LaunchedEffect(selectedCity) {
            if (!selectedCity.isNullOrBlank() &&
                weatherViewModel.forecast.value is ForecastState.Loading
            ) {
                weatherViewModel.refreshForecast(selectedCity)
            }
        }
    }

    @Composable
    private fun ForecastTile(state: ForecastState, modifier: Modifier) {
        Box(modifier = modifier) {
            when (state) {
                ForecastState.Loading -> ForecastSkeletons()
                ForecastState.Failed -> ForecastFailed(onRetry = {
                    weatherViewModel.selectedCity?.let { weatherViewModel.refreshForecast(it) }
                })
                ForecastState.Unconfigured -> ForecastUnconfigured(
                    onOpenSettings = { settingsViewModel.openSettingsDialog() },
                )
                is ForecastState.Loaded -> ForecastLoadedRow(state.days)
            }
        }
    }

    @Composable
    private fun ForecastLoadedRow(days: List<DayForecast>) {
        Column {
            Text(
                text = "FORECAST · 5 DAYS",
                fontSize = 9.sp,
                letterSpacing = 2.25.sp,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                days.take(5).forEachIndexed { index, day ->
                    DayCard(day, isToday = index == 0)
                }
            }
        }
    }

    @Composable
    private fun DayCard(day: DayForecast, isToday: Boolean) {
        val accent = LocalConsoleAccent.current.primary
        val borderColor = if (isToday) accent else DwellColors.Stroke
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(DwellColors.Surface1)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = weekdayShort(day.date.dayOfWeek),
                fontSize = 10.sp,
                color = if (isToday) accent else DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = conditionGlyph(day.conditionCode),
                fontSize = 18.sp,
                color = DwellColors.TextHigh,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "H${day.high}°",
                fontSize = 12.sp,
                color = DwellColors.TextHigh,
                fontFamily = DwellFonts.jetBrainsMono(),
            )
            Text(
                text = "L${day.low}°",
                fontSize = 11.sp,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.jetBrainsMono(),
            )
            // Use the border color as a thin underline accent for today.
            if (isToday) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(borderColor),
                )
            }
        }
    }

    @Composable
    private fun ForecastSkeletons() {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(5) {
                Box(
                    Modifier
                        .size(width = 60.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DwellColors.Surface1.copy(alpha = 0.5f)),
                )
            }
        }
    }

    @Composable
    private fun ForecastFailed(onRetry: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Couldn't load forecast",
                fontSize = 12.sp,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                modifier = Modifier.padding(end = 6.dp),
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    tint = DwellColors.TextMid,
                )
            }
        }
    }

    @Composable
    private fun ForecastUnconfigured(onOpenSettings: () -> Unit) {
        Column {
            Text(
                text = "Forecast",
                fontSize = 11.sp,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Add a WeatherAPI key",
                fontSize = 12.sp,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
            )
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = DwellColors.StatusAccent, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ForecastChip(state: ForecastState, modifier: Modifier) {
        Row(
            modifier = modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "5d:",
                fontSize = 10.sp,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
            when (state) {
                is ForecastState.Loaded -> {
                    val line = state.days.take(5).joinToString(" ") { "H${it.high}" }
                    Text(
                        text = line,
                        fontSize = 13.sp,
                        color = DwellColors.TextHigh,
                        fontFamily = DwellFonts.jetBrainsMono(),
                    )
                }
                else -> Text(
                    text = "—",
                    fontSize = 13.sp,
                    color = DwellColors.TextMid,
                    fontFamily = DwellFonts.jetBrainsMono(),
                )
            }
        }
    }

    @Composable
    private fun ForecastMinimal(state: ForecastState, modifier: Modifier) {
        val text = when (state) {
            is ForecastState.Loaded -> {
                val days = state.days
                if (days.size < 2) {
                    "Forecast unavailable"
                } else {
                    val tomorrow = days[1]
                    val tomorrowLine =
                        "Tomorrow: ${tomorrow.high}° ${tomorrow.conditionText.lowercase()}"
                    if (days.size >= 3) {
                        val dayAfter = days[2]
                        "$tomorrowLine · ${weekdayShort(dayAfter.date.dayOfWeek)} " +
                            "${dayAfter.high}° ${dayAfter.conditionText.lowercase()}"
                    } else {
                        tomorrowLine
                    }
                }
            }
            ForecastState.Loading -> "forecast loading…"
            ForecastState.Failed -> "forecast unavailable"
            ForecastState.Unconfigured -> "forecast · add WeatherAPI key"
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = DwellColors.TextMid,
            fontFamily = DwellFonts.interTight(),
            modifier = modifier,
        )
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

/**
 * Maps WeatherAPI condition codes to a small unicode glyph.
 * Documented codes: https://www.weatherapi.com/docs/weather_conditions.json
 */
private fun conditionGlyph(code: Int): String = when (code) {
    1000 -> "☀"                                  // Sunny / Clear
    1003 -> "⛅"                                  // Partly cloudy
    1006, 1009 -> "☁"                            // Cloudy / Overcast
    1030, 1135, 1147 -> "🌫"                // Mist / Fog
    in 1063..1201 -> "☔"                         // Rain-ish
    in 1204..1237 -> "🌨"                   // Sleet / Snow
    in 1240..1264 -> "☔"                         // Showers
    in 1273..1282 -> "⛈"                         // Thunder
    else -> "☁"
}
