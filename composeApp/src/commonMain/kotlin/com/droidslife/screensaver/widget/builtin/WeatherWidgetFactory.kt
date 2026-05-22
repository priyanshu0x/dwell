package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    override val description: String = "Current weather for a configured city"
    override val category: WidgetCategory = WidgetCategory.INFORMATION
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 5, defaultRows = 2,
        maxCols = 8, maxRows = 3,
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
            placeholder = "Mumbai",
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
                subtitle = "Couldn't load",
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val configuredCity = config.string("city")
        var pickerOpen by remember { mutableStateOf(false) }

        LaunchedEffect(configuredCity) {
            if (configuredCity.isNotBlank()) {
                weatherViewModel.loadWeatherDataForCity(configuredCity)
            }
        }

        val openPicker = { pickerOpen = true }
        val onCityPicked: (String) -> Unit = { city ->
            val trimmed = city.trim()
            if (trimmed.isNotBlank() && trimmed != configuredCity) {
                val merged = config.rawJson + ("city" to JsonPrimitive(trimmed))
                settingsViewModel.updateWidgetConfig(WIDGET_ID, JsonObject(merged))
            }
            pickerOpen = false
        }

        when (val state = weatherViewModel.state) {
            is WeatherState.Loading -> WeatherTile(
                label = "WEATHER",
                value = "—",
                subtitle = "Loading…",
                modifier = modifier,
                onLabelClick = openPicker,
                pickerOpen = pickerOpen,
                currentCity = configuredCity,
                onCityPicked = onCityPicked,
            )
            is WeatherState.Success -> {
                val current = state.current
                val cityName = current.city.ifBlank { configuredCity }
                val subtitle = buildString {
                    if (current.conditionText.isNotBlank()) {
                        append(current.conditionText)
                    } else {
                        append("—")
                    }
                    current.feelsLikeC?.let { append(" · feels ${it.toInt()}°") }
                    current.humidity?.let { append(" · humidity ${it}%") }
                }
                WeatherTile(
                    label = if (cityName.isNotBlank()) "WEATHER · ${cityName.uppercase()}" else "WEATHER",
                    value = "${current.tempC.toInt()}°",
                    subtitle = subtitle,
                    valueIsAccent = true,
                    modifier = modifier,
                    onLabelClick = openPicker,
                    pickerOpen = pickerOpen,
                    currentCity = configuredCity,
                    onCityPicked = onCityPicked,
                )
            }
            is WeatherState.Unconfigured -> WeatherTile(
                label = "WEATHER",
                value = "—",
                subtitle = "Add a WeatherAPI key to enable",
                modifier = modifier,
                onLabelClick = openPicker,
                pickerOpen = pickerOpen,
                currentCity = configuredCity,
                onCityPicked = onCityPicked,
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
            is WeatherState.Error -> WeatherTile(
                label = "WEATHER",
                value = "—",
                subtitle = "Couldn't load",
                modifier = modifier,
                onLabelClick = openPicker,
                pickerOpen = pickerOpen,
                currentCity = configuredCity,
                onCityPicked = onCityPicked,
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

/**
 * Console-style tile layout: label pinned top-start, big temperature centered
 * (so it reads at a glance from across the room), subtitle pinned bottom-start.
 * Mirrors the mockup at
 * `.superpowers/brainstorm/1398003-1779392791/content/mode-mockups.html`.
 */
@Composable
private fun WeatherTile(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueIsAccent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onLabelClick: (() -> Unit)? = null,
    pickerOpen: Boolean = false,
    currentCity: String = "",
    onCityPicked: (String) -> Unit = {},
) {
    val accent = LocalConsoleAccent.current.primary
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            Text(
                text = label,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 2.25.sp,
                color = DwellColors.TextLow,
                maxLines = 1,
                modifier = if (onLabelClick != null) {
                    Modifier.clickable(onClick = onLabelClick)
                } else Modifier,
            )
            if (pickerOpen) {
                CityPickerPopup(
                    initial = currentCity,
                    onPick = onCityPicked,
                    onDismiss = { onCityPicked(currentCity) },
                )
            }
        }
        Text(
            text = value,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Medium,
            fontSize = 44.sp,
            color = if (valueIsAccent) accent else DwellColors.TextHigh,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = subtitle,
            fontFamily = DwellFonts.interTight(),
            fontSize = 10.sp,
            color = DwellColors.TextMid,
            maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        if (trailing != null) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                trailing()
            }
        }
    }
}

@Composable
private fun CityPickerPopup(
    initial: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(DwellColors.Surface1),
    ) {
        var text by remember { mutableStateOf(initial) }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("City") },
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { onPick(text) }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 200.dp),
        )
        listOf("Mumbai", "Delhi", "Bengaluru", "London", "New York", "Tokyo").forEach { suggestion ->
            DropdownMenuItem(
                text = { Text(suggestion, color = DwellColors.TextMid) },
                onClick = { onPick(suggestion) },
            )
        }
    }
}
