package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.components.WidgetStatusLine
import com.droidslife.screensaver.components.WidgetStatusSeverity
import com.droidslife.screensaver.location.FALLBACK_CITY
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.settings.SettingsModel
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.weather.DayForecast
import com.droidslife.screensaver.weather.ForecastState
import com.droidslife.screensaver.weather.WeatherState
import com.droidslife.screensaver.weather.WeatherSyncStatus
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

const val CLOCK_WIDGET_ID: String = "com.droidslife.screensaver.clock"
private const val WEATHER_WIDGET_ID = "com.droidslife.screensaver.weather"
private const val VARIANT_SIDECAR = "sidecar"
private const val VARIANT_CONSOLE = "console"
private const val VARIANT_DEVELOPER_LEGACY = "developer"
private const val VARIANT_CLOCK_ONLY = "clock"
private const val VARIANT_WEATHER_ONLY = "weather"

private val MockPanel = Color(0xFF101014)
private val MockStroke = Color(0xFF315F74)
private val MockMint = Color(0xFFA9DFAD)

class ClockWidgetFactory(
    private val settingsViewModel: SettingsViewModel,
    private val weatherViewModel: WeatherViewModel,
) : WidgetFactory {
    override val id: String = CLOCK_WIDGET_ID
    override val displayName: String = "Clock + Weather"
    override val description: String = "Responsive time, weather, and forecast display"
    override val category: WidgetCategory = WidgetCategory.CLOCK
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 4, minRows = 3,
        defaultCols = 12, defaultRows = 4,
        maxCols = 12, maxRows = 5,
        // At 3 rows the side-by-side layout needs at least 6 cols to keep the
        // weather card legible. 4×3 / 5×3 are forbidden; 6×3+ and 4×4+ are OK.
        minColsAtRowCount = mapOf(3 to 6),
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Enum(
            key = "variant",
            label = "Variant",
            options = listOf(
                ConfigField.EnumOption(VARIANT_SIDECAR, "Sidecar"),
                ConfigField.EnumOption(VARIANT_CONSOLE, "Console"),
                ConfigField.EnumOption(VARIANT_CLOCK_ONLY, "Clock only"),
                ConfigField.EnumOption(VARIANT_WEATHER_ONLY, "Weather only"),
            ),
            default = VARIANT_SIDECAR,
            help = "Choose the clock/weather composition for this tile.",
        ),
        ConfigField.Text(
            key = "city",
            label = "Primary city",
            placeholder = FALLBACK_CITY,
            help = "Used for weather and the primary time label. Leave blank to reuse the Weather widget city.",
        ),
        ConfigField.StringList(
            key = "locations",
            label = "Other cities / time zones",
            help = "Optional comma-separated entries. Use City or City|Area/Zone, e.g. London|Europe/London.",
        ),
        ConfigField.Enum(
            key = "provider",
            label = "Weather source",
            options = listOf(
                ConfigField.EnumOption(WttrInProvider.ID, "wttr.in (no key)"),
                ConfigField.EnumOption(WeatherApiProvider.ID, "WeatherAPI.com"),
            ),
            default = WttrInProvider.ID,
            help = "wttr.in works out of the box. WeatherAPI.com requires a free API key.",
        ),
        ConfigField.Secret(
            key = "apiKey",
            label = "WeatherAPI.com API key",
            help = "Only needed if you pick WeatherAPI.com as the source.",
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget =
        ClockWeatherWidget(config, settingsViewModel, weatherViewModel)
}

private class ClockWeatherWidget(
    private val config: WidgetConfig,
    private val settingsViewModel: SettingsViewModel,
    private val weatherViewModel: WeatherViewModel,
) : Widget {
    override val preferredSpan: Int = 2

    override fun summary(): WidgetSummary {
        val variant = normalizedVariant(config.enum("variant", VARIANT_SIDECAR))
        val state = weatherViewModel.state
        if (variant == VARIANT_WEATHER_ONLY && state is WeatherState.Success) {
            return WidgetSummary(
                primaryValue = "${state.current.tempC.toInt()}°",
                primaryLabel = "Weather",
                subtitle = "${state.current.conditionText} · ${state.current.city}",
            )
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return WidgetSummary(
            primaryValue = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
            primaryLabel = if (variant == VARIANT_CLOCK_ONLY) "Time" else "Time + Weather",
            subtitle = formatDateLine(now.dayOfWeek, now.month, now.day),
        )
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val settings = settingsViewModel.settings
        val variant = normalizedVariant(config.enum("variant", VARIANT_SIDECAR))
        val locations = remember(config.rawJson, settings.widgetConfigs) {
            resolveLocations(config, settings)
        }
        val primary = locations.first()
        val provider = weatherProvider(config, settings)
        val weatherSecretRef = weatherApiKeyReference(config, settings)
        val needsWeather = variant != VARIANT_CLOCK_ONLY

        LaunchedEffect(needsWeather, primary.city, provider, weatherSecretRef) {
            if (needsWeather && primary.city.isNotBlank()) {
                weatherViewModel.loadWeatherDataForCity(primary.city, forceRefresh = true)
            }
        }

        val time by produceState(initialValue = Clock.System.now(), settings.showSeconds) {
            while (true) {
                kotlinx.coroutines.delay(if (settings.showSeconds) 1_000L else 15_000L)
                value = Clock.System.now()
            }
        }

        val now = time.toLocalDateTime(primary.timeZone)
        val timeText = formatTime(now.hour, now.minute, now.second, settings.is24HourFormat, settings.showSeconds)
        val dateText = formatDateLine(now.dayOfWeek, now.month, now.day).uppercase()
        val syncStatus by weatherViewModel.syncStatus.collectAsState()
        val forecastState by weatherViewModel.forecast.collectAsState()
        val weather = weatherSnapshot(
            state = weatherViewModel.state,
            syncStatus = syncStatus,
            configuredCity = primary.city,
            sourceLabel = providerLabel(provider),
        )
        val forecastDays = (forecastState as? ForecastState.Loaded)?.days.orEmpty()

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val compact = maxWidth < 500.dp || maxHeight < 240.dp
            val showMetrics = maxWidth >= 360.dp && maxHeight >= 260.dp
            val showForecast = maxWidth >= 360.dp && maxHeight >= 320.dp && forecastDays.isNotEmpty()
            val showLocations = locations.size > 1 && maxWidth >= 420.dp && maxHeight >= 300.dp
            // ≤3-row widgets go horizontal (weather right of clock); ≥4-row
            // widgets stay stacked. At 1.0× DPI a 3-row tile is ~413dp tall;
            // a 4-row tile is ~568dp, so 460dp is the safe split point.
            val sideBySide = maxHeight < 460.dp

            when (variant) {
                VARIANT_CONSOLE -> ConsoleVariant(
                    primary = primary,
                    locations = locations,
                    instant = time,
                    timeText = timeText,
                    dateText = dateText,
                    weather = weather,
                    forecastDays = if (showForecast) forecastDays else emptyList(),
                    showLocations = showLocations,
                    compact = compact,
                    sideBySide = sideBySide,
                    modifier = Modifier.fillMaxSize(),
                )
                VARIANT_CLOCK_ONLY -> ClockOnlyVariant(
                    primary = primary,
                    locations = locations,
                    instant = time,
                    timeText = timeText,
                    dateText = dateText,
                    showDate = settings.showDate,
                    showLocations = showLocations,
                    compact = compact,
                    modifier = Modifier.fillMaxSize(),
                )
                VARIANT_WEATHER_ONLY -> WeatherOnlyVariant(
                    primary = primary,
                    weather = weather,
                    forecastDays = if (showForecast) forecastDays else emptyList(),
                    syncStatus = syncStatus,
                    compact = compact,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { weatherViewModel.loadWeatherDataForCity(primary.city, forceRefresh = true) },
                    onSettings = { settingsViewModel.openWidgetConfig(CLOCK_WIDGET_ID) },
                )
                else -> SidecarVariant(
                    primary = primary,
                    locations = locations,
                    instant = time,
                    timeText = timeText,
                    dateText = dateText,
                    showDate = settings.showDate,
                    weather = weather,
                    forecastDays = if (showForecast) forecastDays else emptyList(),
                    syncStatus = syncStatus,
                    showMetrics = showMetrics,
                    showLocations = showLocations,
                    compact = compact,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { weatherViewModel.loadWeatherDataForCity(primary.city, forceRefresh = true) },
                    onSettings = { settingsViewModel.openWidgetConfig(CLOCK_WIDGET_ID) },
                )
            }
        }
    }
}

@Composable
private fun SidecarVariant(
    primary: ClockLocation,
    locations: List<ClockLocation>,
    instant: Instant,
    timeText: String,
    dateText: String,
    showDate: Boolean,
    weather: WeatherSnapshot,
    forecastDays: List<DayForecast>,
    syncStatus: WeatherSyncStatus,
    showMetrics: Boolean,
    showLocations: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    MockFrame(modifier = modifier, accent = MockStroke) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            WidgetHeader(
                label = headerLabel("TIME + WEATHER", primary.label),
                settingsId = CLOCK_WIDGET_ID,
            )

            if (compact) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MockTimeBlock(
                        timeText = timeText,
                        dateText = dateText,
                        quiet = weather.quietLine,
                        showDate = showDate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WeatherCard(
                        weather = weather,
                        compact = true,
                        onRetry = onRetry,
                        onSettings = onSettings,
                    )
                }
            } else {
                // Top-aligned + Row sizes by content; CenterVertically was leaving a
                // dead gap above the metrics row because the Row had weight(1f).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    MockTimeBlock(
                        timeText = timeText,
                        dateText = dateText,
                        quiet = weather.quietLine,
                        showDate = showDate,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    WeatherCard(
                        weather = weather,
                        compact = false,
                        onRetry = onRetry,
                        onSettings = onSettings,
                        modifier = Modifier.widthIn(min = 156.dp, max = 184.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            if (showMetrics) WeatherMetrics(weather = weather, modifier = Modifier.fillMaxWidth())
            if (showLocations) LocationRail(locations.drop(1), instant, Modifier.fillMaxWidth())
            if (forecastDays.isNotEmpty()) ForecastStrip(forecastDays, dense = compact, modifier = Modifier.fillMaxWidth())
            WeatherDegradedLine(syncStatus, weather.state)
        }
    }
}

@Composable
private fun ConsoleVariant(
    primary: ClockLocation,
    locations: List<ClockLocation>,
    instant: Instant,
    timeText: String,
    dateText: String,
    weather: WeatherSnapshot,
    forecastDays: List<DayForecast>,
    showLocations: Boolean,
    compact: Boolean,
    sideBySide: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = LocalConsoleAccent.current.primary
    MockFrame(modifier = modifier, accent = accent, variant = MockVariant.Console) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            WidgetHeader(
                label = headerLabel("SYSTEM VIEW", "${primary.label} · ${timeZoneLabel(primary.timeZone)}"),
                settingsId = CLOCK_WIDGET_ID,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.16f)),
            )

            // Inner console-screen takes all the slack between the header and the
            // forecast row. In compact mode the weather card inside gets weight(1f)
            // so it grows to fill that slack instead of leaving a dead gap.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.025f))
                    .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                    .padding(if (compact) 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            ) {
                val syncSubLine = when (weather.state) {
                    WeatherSnapshotState.Live -> if (weather.isLive) "synced just now" else "showing cached"
                    WeatherSnapshotState.Loading -> "syncing…"
                    WeatherSnapshotState.Unconfigured -> "not configured"
                    WeatherSnapshotState.Error -> "weather offline"
                }
                if (sideBySide) {
                    // Short widgets: time left, weather card right. Time takes
                    // horizontal flex; clock auto-sizes to the row's full height.
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ConsoleTimeReadout(
                            timeText = timeText,
                            dateText = dateText,
                            syncSubLine = syncSubLine,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        ConsoleWeatherReadout(
                            weather = weather,
                            modifier = Modifier.width(170.dp).fillMaxHeight(),
                        )
                    }
                } else {
                    // Tall widgets: clock fills full width, weather card stacks below.
                    ConsoleTimeReadout(
                        timeText = timeText,
                        dateText = dateText,
                        syncSubLine = syncSubLine,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    ConsoleWeatherReadout(
                        weather = weather,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Pack content tight — pinning the forecast to the bottom left a big
            // dead band the user called out. Any extra vertical room lives below
            // the forecast instead.
            if (showLocations) LocationRail(locations.drop(1), instant, Modifier.fillMaxWidth())
            if (forecastDays.isNotEmpty()) {
                ForecastStrip(
                    days = forecastDays,
                    dense = compact,
                    accent = accent,
                    showIcon = false,
                    stackHighLow = compact,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ClockOnlyVariant(
    primary: ClockLocation,
    locations: List<ClockLocation>,
    instant: Instant,
    timeText: String,
    dateText: String,
    showDate: Boolean,
    showLocations: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    MockFrame(modifier = modifier, accent = MockStroke) {
        Column(modifier = Modifier.fillMaxSize()) {
            WidgetHeader(
                label = "TIME · ${primary.label.uppercase()}",
                settingsId = CLOCK_WIDGET_ID,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                MockDisplayTime(timeText = timeText)
            }
            if (showDate) {
                DateRow(dateText = dateText, quiet = null)
            }
            if (showLocations) {
                Spacer(Modifier.height(10.dp))
                LocationRail(locations.drop(1), instant, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WeatherOnlyVariant(
    primary: ClockLocation,
    weather: WeatherSnapshot,
    forecastDays: List<DayForecast>,
    syncStatus: WeatherSyncStatus,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    MockFrame(modifier = modifier, accent = MockStroke) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            WidgetHeader(
                label = "WEATHER · ${primary.label.uppercase()}",
                settingsId = CLOCK_WIDGET_ID,
            )

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Auto-sized hero temperature so "29°" grows with the tile
                    // height. weight(1f) on the Box gives it the leftover area
                    // after the subtitle / action row.
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        MockDisplayTime(
                            timeText = weather.temperature,
                            minFontSize = 56.sp,
                            maxFontSize = 220.sp,
                            fontFamily = DwellFonts.jetBrainsMono(),
                            fontWeight = FontWeight.Light,
                            color = if (weather.isLive) MockMint else DwellColors.TextHigh,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = weather.subtitle,
                        color = DwellColors.TextMid,
                        fontFamily = DwellFonts.interTight(),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WeatherAction(weather, onRetry, onSettings)
                }
                if (!compact) {
                    WeatherGlyph(
                        code = weather.conditionCode,
                        fontSize = 120.sp,
                        alpha = if (weather.isLive) 1f else 0.5f,
                    )
                }
            }

            if (forecastDays.isNotEmpty()) ForecastStrip(forecastDays, dense = compact, modifier = Modifier.fillMaxWidth())
            WeatherDegradedLine(syncStatus, weather.state)
        }
    }
}

// The widget sits inside ConsoleMode's tile chrome (rounded surface, gear
// overlay, hover background). We don't paint our own frame on top — that's
// what made the merged widget visually inconsistent with every other tile.
@Composable
private fun MockFrame(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accent: Color = MockStroke,
    @Suppress("UNUSED_PARAMETER") variant: MockVariant = MockVariant.Sidecar,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
    }
}

// Combine an eyebrow and a place into one canonical label, matching the way
// main's Clock and Weather widgets format their headers (e.g. "TIME · REWARI").
private fun headerLabel(eyebrow: String, place: String?): String =
    if (place.isNullOrBlank()) eyebrow else "$eyebrow · ${place.uppercase()}"

@Composable
private fun MockTimeBlock(
    timeText: String,
    dateText: String,
    quiet: String?,
    showDate: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Top) {
        // BasicText.autoSize needs a bounded Box to size into — weight(1f)
        // here gives the clock all the height left after the date row below.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            MockDisplayTime(timeText = timeText)
        }
        if (showDate) {
            Spacer(Modifier.height(8.dp))
            DateRow(dateText = dateText, quiet = quiet)
        }
    }
}

// Shared auto-sized clock used by every variant. Wrap in a bounded Box (weight
// or fixed size) so BasicText/autoSize has both axes to fit into. Variants
// customize the look by swapping fontFamily / fontWeight / tracking; defaults
// are the Sidecar's Impact-style Inter Tight Black.
@Composable
private fun MockDisplayTime(
    timeText: String,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 48.sp,
    maxFontSize: TextUnit = 240.sp,
    fontFamily: androidx.compose.ui.text.font.FontFamily = DwellFonts.interTight(),
    fontWeight: FontWeight = FontWeight.Black,
    letterSpacingEm: Float = -0.04f,
    color: Color = DwellColors.TextHigh,
) {
    BasicText(
        text = timeText,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            stepSize = 2.sp,
        ),
        style = androidx.compose.ui.text.TextStyle(
            color = color,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            letterSpacing = letterSpacingEm.em,
        ),
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun DateRow(dateText: String, quiet: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dateText.split(" · ").forEach { part ->
            Text(
                text = part,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
                letterSpacing = 1.2.sp,
                maxLines = 1,
            )
        }
        if (!quiet.isNullOrBlank()) {
            Text(
                text = quiet,
                color = DwellColors.TextFaint,
                fontFamily = DwellFonts.interTight(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherSnapshot,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = if (compact) 128.dp else 178.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(MockMint.copy(alpha = 0.08f))
            .border(1.dp, MockMint.copy(alpha = 0.22f), RoundedCornerShape(15.dp))
            .padding(if (compact) 14.dp else 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        WeatherGlyph(
            code = weather.conditionCode,
            fontSize = if (compact) 30.sp else 40.sp,
            alpha = if (weather.isLive) 1f else 0.5f,
        )
        Column {
            WeatherTemperature(weather, fontSize = if (compact) 54.sp else 70.sp)
            Text(
                text = weather.condition,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontSize = if (compact) 13.sp else 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            WeatherAction(weather, onRetry, onSettings)
        }
    }
}

@Composable
private fun WeatherTemperature(
    weather: WeatherSnapshot,
    fontSize: TextUnit,
    accent: Color = MockMint,
) {
    Text(
        text = weather.temperature,
        color = if (weather.isLive) accent else DwellColors.TextHigh,
        fontFamily = DwellFonts.jetBrainsMono(),
        fontWeight = FontWeight.Light,
        fontSize = fontSize,
        lineHeight = fontSize,
        maxLines = 1,
    )
}

@Composable
private fun WeatherAction(
    weather: WeatherSnapshot,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    when (weather.state) {
        WeatherSnapshotState.Unconfigured -> InlineAction("Open weather settings", onSettings)
        WeatherSnapshotState.Error -> InlineAction("Retry", onRetry)
        else -> Unit
    }
}

@Composable
private fun InlineAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            color = DwellColors.StatusAccent,
            fontFamily = DwellFonts.interTight(),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun WeatherMetrics(weather: WeatherSnapshot, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCell("Feels", weather.feelsLike ?: "—", Modifier.weight(1f))
        MetricCell("Humidity", weather.humidity ?: "—", Modifier.weight(1f))
        MetricCell("Source", weather.source, Modifier.weight(1f))
    }
}

@Composable
private fun ConsoleMetrics(weather: WeatherSnapshot, modifier: Modifier = Modifier) {
    // Feels-like + Live status already appear in the weather readout above this
    // row, so the 4 telemetry cells stick to facts the readout doesn't cover.
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell("Humidity", weather.humidity ?: "—", Modifier.weight(1f))
        MetricCell("Feels", weather.feelsLike ?: "—", Modifier.weight(1f))
        MetricCell("Condition", weather.condition, Modifier.weight(1f))
        MetricCell("Source", weather.source, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MockMint,
) {
    Column(
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.018f))
            .border(1.dp, DwellColors.TextLow.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            color = DwellColors.TextFaint,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Numeric values (28°, 48%, 6km/h) want bold display sizing; text values
        // (WeatherAPI, Mist) need a smaller size to fit without ellipsis.
        val isNumeric = value.firstOrNull()?.isDigit() == true || value == "—"
        Text(
            text = value,
            color = if (value == "Live") accent else DwellColors.TextHigh,
            fontFamily = DwellFonts.interTight(),
            fontWeight = if (isNumeric) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = if (isNumeric) 17.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConsoleTimeReadout(
    timeText: String,
    dateText: String,
    syncSubLine: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Top) {
        // (LOCAL TIME label hidden per user request.)
        // The Box gets the leftover vertical space inside the inner Column
        // (after the date line, and the size-to-content weather card), and
        // BasicText/autoSize picks the largest font that fits both axes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Same shared autoSize clock used by Sidecar / Clock-only — Console
            // just swaps the font family + weight for its terminal-mono look.
            MockDisplayTime(
                timeText = timeText,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.Bold,
                letterSpacingEm = -0.06f,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append(dateText.toLowerCasePretty())
                if (syncSubLine.isNotBlank()) {
                    append(" · ")
                    append(syncSubLine)
                }
            },
            color = DwellColors.TextMid,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// "FRI · MAY 29" → "Fri · May 29"
private fun String.toLowerCasePretty(): String =
    split(" ").joinToString(" ") { word ->
        if (word.length <= 1) word
        else word.first() + word.drop(1).lowercase()
    }

@Composable
private fun ConsoleWeatherReadout(
    weather: WeatherSnapshot,
    modifier: Modifier = Modifier,
) {
    val accent = LocalConsoleAccent.current.primary
    Column(
        modifier = modifier
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConsoleLabel("Weather")
        val extras = buildList {
            weather.wind?.let { add("wind $it") }
            weather.visibility?.let { add("vis $it") }
        }
        // Pick layout from the card's actual width:
        //   • wide (≥ 320dp): temp · pill · meta column inline (one tall row)
        //   • narrow: temp · pill on top, meta lines beneath (two rows)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wideEnoughForInlineMeta = maxWidth >= 320.dp
            if (wideEnoughForInlineMeta) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeatherTemperature(weather, fontSize = 46.sp, accent = accent)
                    StatusPill(weather.condition, dotIsLive = weather.isLive, accent = accent)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = weather.subtitleDetails,
                            color = DwellColors.TextMid,
                            fontFamily = DwellFonts.jetBrainsMono(),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (extras.isNotEmpty()) {
                            Text(
                                text = extras.joinToString(" · "),
                                color = DwellColors.TextFaint,
                                fontFamily = DwellFonts.jetBrainsMono(),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WeatherTemperature(weather, fontSize = 42.sp, accent = accent)
                        StatusPill(weather.condition, dotIsLive = weather.isLive, accent = accent)
                    }
                    Text(
                        text = weather.subtitleDetails,
                        color = DwellColors.TextMid,
                        fontFamily = DwellFonts.jetBrainsMono(),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (extras.isNotEmpty()) {
                        Text(
                            text = extras.joinToString(" · "),
                            color = DwellColors.TextFaint,
                            fontFamily = DwellFonts.jetBrainsMono(),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = DwellColors.TextFaint,
        fontFamily = DwellFonts.jetBrainsMono(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        maxLines = 1,
    )
}

@Composable
private fun StatusPill(
    label: String,
    dotIsLive: Boolean = true,
    accent: Color = MockMint,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (dotIsLive) accent else DwellColors.TextLow),
        )
        Text(
            text = label,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

// Shared 5-day forecast row. Variants tweak: accent colour, whether to show
// the per-day weather icon, and whether high/low temps stack or inline.
@Composable
private fun ForecastStrip(
    days: List<DayForecast>,
    dense: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MockMint,
    showIcon: Boolean = true,
    stackHighLow: Boolean = false,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (showIcon) 10.dp else 8.dp)) {
        days.take(5).forEachIndexed { index, day ->
            ForecastDayCard(
                day = day,
                isToday = index == 0,
                dense = dense,
                accent = accent,
                showIcon = showIcon,
                stackHighLow = stackHighLow,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// (ConsoleForecast removed — Console now uses ForecastStrip with the active
// console accent, no weather icon, and stacked high/low values in compact tiles.)

@Composable
private fun ForecastDayCard(
    day: DayForecast,
    isToday: Boolean,
    dense: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    stackHighLow: Boolean = false,
) {
    // Size by content so high+low always fit. Props let each variant choose
    // accent colour, whether to draw the weather glyph, and whether the
    // high/low pair stacks vertically (narrow tiles) or sits inline.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) accent.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.20f))
            .border(
                1.dp,
                if (isToday) accent.copy(alpha = 0.44f) else DwellColors.TextLow.copy(alpha = 0.14f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = if (showIcon) 10.dp else 12.dp, vertical = if (dense) 9.dp else 12.dp),
        horizontalAlignment = if (showIcon) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (showIcon) 6.dp else 4.dp),
    ) {
        Text(
            text = if (isToday) "TODAY" else weekdayShort(day.date.dayOfWeek).uppercase(),
            color = if (isToday) accent else DwellColors.TextLow,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            maxLines = 1,
        )
        if (showIcon) {
            WeatherGlyph(code = day.conditionCode, fontSize = if (dense) 22.sp else 28.sp)
        }
        if (stackHighLow) {
            Text(
                "${day.high}°",
                color = DwellColors.TextHigh,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.SemiBold,
                fontSize = if (dense) 18.sp else 22.sp,
                maxLines = 1,
            )
            Text(
                "${day.low}°",
                color = DwellColors.TextFaint,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = if (dense) 12.sp else 14.sp,
                maxLines = 1,
            )
            return@Column
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${day.high}°",
                color = DwellColors.TextHigh,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.SemiBold,
                fontSize = if (dense) 16.sp else 18.sp,
                maxLines = 1,
            )
            Text(
                "${day.low}°",
                color = DwellColors.TextFaint,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = if (dense) 12.sp else 14.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LocationRail(
    locations: List<ClockLocation>,
    instant: Instant,
    modifier: Modifier = Modifier,
) {
    if (locations.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        locations.take(3).forEach { location ->
            val local = instant.toLocalDateTime(location.timeZone)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.018f))
                    .border(1.dp, DwellColors.TextLow.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = location.label.uppercase(),
                    color = DwellColors.TextFaint,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}",
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

// Per-condition emoji glyph kept in lockstep with the standalone Weather widget
// so the two surfaces feel like one widget family.
@Composable
private fun WeatherGlyph(
    code: Int?,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Text(
        text = conditionGlyph(code),
        fontSize = fontSize,
        lineHeight = fontSize,
        color = DwellColors.TextHigh.copy(alpha = alpha),
        maxLines = 1,
        modifier = modifier,
    )
}

private fun conditionGlyph(code: Int?): String = when (code) {
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

@Composable
private fun WeatherDegradedLine(syncStatus: WeatherSyncStatus, state: WeatherSnapshotState) {
    val (message, severity) = when (syncStatus) {
        WeatherSyncStatus.Healthy -> null to WidgetStatusSeverity.Info
        WeatherSyncStatus.Offline -> "Weather offline - showing last update" to WidgetStatusSeverity.Warning
        WeatherSyncStatus.Unconfigured -> "Needs a WeatherAPI.com key - or switch to wttr.in" to WidgetStatusSeverity.Warning
        WeatherSyncStatus.CredentialFailed -> "WeatherAPI key/account problem - update it in settings" to WidgetStatusSeverity.Error
        WeatherSyncStatus.RateLimited -> "WeatherAPI rate limit reached - showing last update" to WidgetStatusSeverity.Warning
        WeatherSyncStatus.Failed -> "Weather unavailable - check network/API key" to WidgetStatusSeverity.Error
    }
    WidgetStatusLine(
        message.takeIf { state == WeatherSnapshotState.Live },
        severity = severity,
    )
}

private enum class MockVariant { Sidecar, Console }

private data class ClockLocation(
    val label: String,
    val city: String,
    val timeZone: TimeZone,
)

private data class WeatherSnapshot(
    val temperature: String,
    val condition: String,
    val subtitle: String,
    // Same as [subtitle] but without the leading condition phrase, e.g.
    // "feels 28° · humidity 48%". Used in Console where the condition shows
    // separately in the status pill.
    val subtitleDetails: String,
    val quietLine: String?,
    val feelsLike: String?,
    val humidity: String?,
    // Pre-formatted "6 km/h" / "3.8 km". Null when the active provider doesn't
    // supply the field — the UI omits the row instead of rendering "—".
    val wind: String?,
    val visibility: String?,
    val conditionCode: Int?,
    val source: String,
    val statusLabel: String,
    val isLive: Boolean,
    val state: WeatherSnapshotState,
)

private enum class WeatherSnapshotState { Loading, Live, Unconfigured, Error }

private fun normalizedVariant(value: String): String = when (value) {
    VARIANT_CONSOLE, VARIANT_CLOCK_ONLY, VARIANT_WEATHER_ONLY -> value
    VARIANT_DEVELOPER_LEGACY -> VARIANT_CONSOLE
    else -> VARIANT_SIDECAR
}

private fun resolveLocations(config: WidgetConfig, settings: SettingsModel): List<ClockLocation> {
    val weatherCity = settings.widgetConfigs[WEATHER_WIDGET_ID]
        ?.get("city")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        .orEmpty()
    val primaryCity = config.string("city")
        .ifBlank { weatherCity }
        .ifBlank { FALLBACK_CITY }
    val primary = parseLocation(primaryCity) ?: ClockLocation(
        label = primaryCity,
        city = primaryCity,
        timeZone = TimeZone.currentSystemDefault(),
    )
    val extras = config.stringList("locations")
        .mapNotNull(::parseLocation)
        .filterNot { it.city.equals(primary.city, ignoreCase = true) }
    return listOf(primary) + extras
}

private fun parseLocation(raw: String): ClockLocation? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val parts = trimmed.split('|', limit = 2)
    val city = parts.first().trim().ifBlank { return null }
    val timeZone = parts.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }
        ?: TimeZone.currentSystemDefault()
    return ClockLocation(label = city, city = city, timeZone = timeZone)
}

private fun weatherProvider(config: WidgetConfig, settings: SettingsModel): String {
    val local = config.string("provider").takeIf { it.isNotBlank() }
    if (local != null) return local
    return settings.widgetConfigs[WEATHER_WIDGET_ID]
        ?.get("provider")
        ?.jsonPrimitive
        ?.contentOrNull
        ?: WttrInProvider.ID
}

private fun weatherApiKeyReference(config: WidgetConfig, settings: SettingsModel): String? {
    val local = (config.raw("apiKey") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    if (local != null) return local
    return settings.widgetConfigs[WEATHER_WIDGET_ID]
        ?.get("apiKey")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

private fun weatherSnapshot(
    state: WeatherState,
    syncStatus: WeatherSyncStatus,
    configuredCity: String,
    sourceLabel: String,
): WeatherSnapshot = when (state) {
    is WeatherState.Loading -> WeatherSnapshot(
        temperature = "—",
        condition = "Loading",
        subtitle = "Fetching weather...",
        subtitleDetails = "",
        quietLine = null,
        feelsLike = null,
        humidity = null,
        wind = null,
        visibility = null,
        conditionCode = null,
        source = sourceLabel,
        statusLabel = "Loading",
        isLive = false,
        state = WeatherSnapshotState.Loading,
    )
    is WeatherState.Success -> {
        val current = state.current
        val condition = current.conditionText.ifBlank { "Weather" }
        val details = buildString {
            current.feelsLikeC?.let { append("feels ${it.toInt()}°") }
            current.humidity?.let {
                if (isNotEmpty()) append(" · ")
                append("humidity ${it}%")
            }
        }
        WeatherSnapshot(
            temperature = "${current.tempC.toInt()}°",
            condition = condition,
            subtitle = buildString {
                append(condition)
                if (details.isNotEmpty()) append(" · ").append(details)
            },
            subtitleDetails = details,
            quietLine = "${condition.lowercase()} outside",
            feelsLike = current.feelsLikeC?.let { "${it.toInt()}°" },
            humidity = current.humidity?.let { "$it%" },
            wind = current.windKph?.let { "${it.toInt()} km/h" },
            visibility = current.visKm?.let {
                if (it >= 10.0) "${it.toInt()} km" else "${(it * 10).toInt() / 10.0} km"
            },
            conditionCode = current.conditionCode,
            source = if (syncStatus == WeatherSyncStatus.Offline) "Cache" else sourceLabel,
            statusLabel = if (syncStatus == WeatherSyncStatus.Offline) "Cached" else "Live",
            isLive = syncStatus == WeatherSyncStatus.Healthy,
            state = WeatherSnapshotState.Live,
        )
    }
    is WeatherState.Unconfigured -> WeatherSnapshot(
        temperature = "—",
        condition = "API key needed",
        subtitle = "Add a WeatherAPI key or switch to wttr.in.",
        subtitleDetails = "",
        quietLine = "not configured",
        feelsLike = null,
        humidity = null,
        wind = null,
        visibility = null,
        conditionCode = null,
        source = "Config",
        statusLabel = "Config",
        isLive = false,
        state = WeatherSnapshotState.Unconfigured,
    )
    is WeatherState.Error -> WeatherSnapshot(
        temperature = "—",
        condition = "Unavailable",
        subtitle = weatherErrorCopy(syncStatus, state.message),
        subtitleDetails = "",
        quietLine = "weather unavailable",
        feelsLike = null,
        humidity = null,
        wind = null,
        visibility = null,
        conditionCode = null,
        source = sourceLabel,
        statusLabel = "Error",
        isLive = false,
        state = WeatherSnapshotState.Error,
    )
}

private fun formatTime(hour: Int, minute: Int, second: Int, is24Hour: Boolean, showSeconds: Boolean): String {
    val hourDisplay = if (is24Hour) hour else (hour % 12).let { if (it == 0) 12 else it }
    return buildString {
        append(hourDisplay.toString().padStart(2, '0'))
        append(':')
        append(minute.toString().padStart(2, '0'))
        if (showSeconds) {
            append(':')
            append(second.toString().padStart(2, '0'))
        }
        if (!is24Hour) append(if (hour < 12) " AM" else " PM")
    }
}

private fun weatherErrorCopy(syncStatus: WeatherSyncStatus, fallback: String): String = when (syncStatus) {
    WeatherSyncStatus.CredentialFailed -> "WeatherAPI key/account problem - update it in settings"
    WeatherSyncStatus.RateLimited -> "WeatherAPI rate limit reached"
    WeatherSyncStatus.Unconfigured -> "Needs a WeatherAPI.com key"
    else -> fallback.takeIf { it.isNotBlank() } ?: "Couldn't load weather"
}

private fun providerLabel(provider: String): String = when (provider) {
    WeatherApiProvider.ID -> "WeatherAPI"
    WttrInProvider.ID -> "wttr.in"
    else -> provider
}

private fun timeZoneLabel(timeZone: TimeZone): String =
    timeZone.id.substringAfterLast('/').replace('_', ' ').ifBlank { "Local" }

private fun formatDateLine(dayOfWeek: DayOfWeek, month: Month, day: Int): String {
    val monthName = when (month) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Feb"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Apr"
        Month.MAY -> "May"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Aug"
        Month.SEPTEMBER -> "Sep"
        Month.OCTOBER -> "Oct"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dec"
    }
    return "${weekdayShort(dayOfWeek)} · $monthName $day"
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
