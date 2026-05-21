package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.SettingsViewModel
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
import kotlinx.coroutines.delay

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
            is WeatherState.Loading -> WeatherLoadingState(modifier)
            is WeatherState.Success -> WeatherSuccessState(state, modifier)
            is WeatherState.Error -> {
                if (!apiKeyConfigured) {
                    WeatherUnconfiguredState(
                        modifier = modifier,
                        onOpenSettings = { settingsViewModel.openSettingsDialog() },
                    )
                } else {
                    WeatherFailureState(
                        modifier = modifier,
                        onRetry = {
                            if (configuredCity.isNotBlank()) {
                                weatherViewModel.loadWeatherDataForCity(configuredCity)
                            } else {
                                weatherViewModel.loadWeatherData()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherSuccessState(state: WeatherState.Success, modifier: Modifier) {
    val data = state.weatherData
    Column(modifier = modifier) {
        Text(
            text = data.location.name.uppercase(),
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${data.current.tempC.toInt()}°C",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = data.current.condition.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                ),
            )
        }
    }
}

@Composable
private fun WeatherLoadingState(modifier: Modifier) {
    // Debounce: only show skeleton if loading takes longer than ~250ms to avoid flicker.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        visible = true
    }
    if (!visible) {
        // Render an empty placeholder of similar height to avoid layout jumps.
        Box(modifier = modifier.height(64.dp))
        return
    }
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(120.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(placeholderColor),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .height(16.dp)
                .width(180.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(placeholderColor),
        )
    }
}

@Composable
private fun WeatherUnconfiguredState(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(modifier = modifier) {
        Text(
            text = "Weather",
            style = MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add a WeatherAPI key to enable weather",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenSettings,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        ) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun WeatherFailureState(modifier: Modifier, onRetry: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Couldn't load weather",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            ),
            modifier = Modifier.padding(end = 8.dp),
        )
        IconButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Retry",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}
