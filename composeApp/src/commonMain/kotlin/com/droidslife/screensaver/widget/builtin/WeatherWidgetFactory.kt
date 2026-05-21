package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.weather.WeatherState
import com.droidslife.screensaver.weather.WeatherViewModel
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope

class WeatherWidgetFactory(
    private val weatherViewModel: WeatherViewModel,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.weather"
    override val displayName: String = "Weather"
    override val description: String = "Current weather for a configured city"
    override val category: WidgetCategory = WidgetCategory.INFORMATION
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Text(
            key = "city",
            label = "City",
            placeholder = "Mumbai",
        )
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return WeatherWidget(config, weatherViewModel)
    }
}

private class WeatherWidget(
    private val config: WidgetConfig,
    private val weatherViewModel: WeatherViewModel,
) : Widget {
    override val preferredSpan: Int = 1

    @Composable
    override fun Content(modifier: Modifier) {
        val configuredCity = config.string("city")
        LaunchedEffect(configuredCity) {
            if (configuredCity.isNotBlank()) {
                weatherViewModel.loadWeatherDataForCity(configuredCity)
            }
        }

        when (val state = weatherViewModel.state) {
            is WeatherState.Loading -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            }
            is WeatherState.Success -> {
                val data = state.weatherData
                androidx.compose.foundation.layout.Column(modifier = modifier) {
                    Text(
                        text = data.location.name.uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
            is WeatherState.Error -> {
                Text(
                    text = "Error loading weather data",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Red),
                    modifier = modifier,
                )
            }
        }
    }
}
