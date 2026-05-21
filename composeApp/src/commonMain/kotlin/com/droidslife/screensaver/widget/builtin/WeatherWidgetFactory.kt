package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.weather.WeatherState
import com.droidslife.screensaver.weather.WeatherViewModel
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary

class WeatherWidgetFactory(
    private val weatherViewModel: WeatherViewModel,
    private val settingsViewModel: SettingsViewModel,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.weather"
    override val displayName: String = "Weather"
    override val description: String = "Current weather for a configured city"
    override val category: WidgetCategory = WidgetCategory.INFORMATION
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 5, defaultRows = 2,
        maxCols = 8, maxRows = 3,
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Text(
            key = "city",
            label = "City",
            placeholder = "Mumbai",
        )
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
                val data = state.weatherData
                WidgetSummary(
                    primaryValue = "${data.current.tempC.toInt()}°",
                    primaryLabel = "Weather",
                    subtitle = "${data.current.condition.text} · ${data.location.name}",
                )
            }
            is WeatherState.Loading -> WidgetSummary(
                primaryValue = "—",
                primaryLabel = "Weather",
                subtitle = "Loading…",
            )
            is WeatherState.Error -> {
                val apiKeyConfigured = settingsViewModel.isSecretSaved(
                    settingsViewModel.settings.weatherApiKeySecretId
                )
                if (!apiKeyConfigured) {
                    WidgetSummary(
                        primaryValue = "—",
                        primaryLabel = "Weather",
                        subtitle = "API key needed",
                    )
                } else {
                    WidgetSummary(
                        primaryValue = "—",
                        primaryLabel = "Weather",
                        subtitle = "Couldn't load",
                    )
                }
            }
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val configuredCity = config.string("city")
        LaunchedEffect(configuredCity) {
            if (configuredCity.isNotBlank()) {
                weatherViewModel.loadWeatherDataForCity(configuredCity)
            }
        }

        val state = weatherViewModel.state
        val apiKeyConfigured = settingsViewModel.isSecretSaved(
            settingsViewModel.settings.weatherApiKeySecretId
        )

        when (state) {
            is WeatherState.Loading -> WeatherTile(
                label = "WEATHER",
                value = "—",
                subtitle = "Loading…",
                modifier = modifier,
            )
            is WeatherState.Success -> {
                val data = state.weatherData
                val cityName = data.location.name.ifBlank { configuredCity }
                val subtitle = buildString {
                    append(data.current.condition.text)
                    append(" · feels ${data.current.feelslikeC.toInt()}°")
                    append(" · humidity ${data.current.humidity}%")
                }
                WeatherTile(
                    label = if (cityName.isNotBlank()) "WEATHER · ${cityName.uppercase()}" else "WEATHER",
                    value = "${data.current.tempC.toInt()}°",
                    subtitle = subtitle,
                    valueIsAccent = true,
                    modifier = modifier,
                )
            }
            is WeatherState.Error -> {
                if (!apiKeyConfigured) {
                    WeatherTile(
                        label = "WEATHER",
                        value = "—",
                        subtitle = "Add a WeatherAPI key to enable",
                        modifier = modifier,
                        trailing = {
                            TextButton(
                                onClick = { settingsViewModel.openSettingsDialog() },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 0.dp,
                                    vertical = 0.dp,
                                ),
                            ) {
                                Text(
                                    "Open Settings",
                                    fontSize = 11.sp,
                                    color = DwellColors.StatusAccent,
                                    fontFamily = DwellFonts.interTight(),
                                )
                            }
                        },
                    )
                } else {
                    WeatherTile(
                        label = "WEATHER",
                        value = "—",
                        subtitle = "Couldn't load",
                        modifier = modifier,
                        trailing = {
                            IconButton(
                                onClick = {
                                    if (configuredCity.isNotBlank()) {
                                        weatherViewModel.loadWeatherDataForCity(configuredCity)
                                    } else {
                                        weatherViewModel.loadWeatherData()
                                    }
                                },
                                modifier = Modifier.padding(0.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry",
                                    tint = DwellColors.TextMid,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherTile(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueIsAccent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalConsoleAccent.current.primary
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 2.25.sp,
            color = DwellColors.TextLow,
            maxLines = 1,
        )
        Text(
            text = value,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Medium,
            fontSize = 44.sp,
            color = if (valueIsAccent) accent else DwellColors.TextHigh,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtitle,
                fontFamily = DwellFonts.interTight(),
                fontSize = 10.sp,
                color = DwellColors.TextMid,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (trailing != null) trailing()
        }
    }
}
