package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.DigitalClock
import com.droidslife.screensaver.DigitalClock10
import com.droidslife.screensaver.DigitalClock11
import com.droidslife.screensaver.DigitalClock3
import com.droidslife.screensaver.DigitalClock4
import com.droidslife.screensaver.DigitalClock5
import com.droidslife.screensaver.DigitalClock6
import com.droidslife.screensaver.DigitalClock7
import com.droidslife.screensaver.DigitalClock8
import com.droidslife.screensaver.DigitalClock9
import com.droidslife.screensaver.clock.ClockViewModel
import com.droidslife.screensaver.clockdigits.DigitalClockDigit2
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock

class ClockWidgetFactory(
    private val clockViewModel: ClockViewModel,
    private val settingsViewModel: SettingsViewModel,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.clock"
    override val displayName: String = "Clock"
    override val description: String = "Digital clock display"
    override val category: WidgetCategory = WidgetCategory.CLOCK
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Enum(
            key = "design",
            label = "Clock design",
            options = (1..ClockViewModel.DESIGN_COUNT).map {
                ConfigField.EnumOption(it.toString(), "Design $it")
            },
            default = "1",
        ),
        ConfigField.Bool("autoCycle", "Auto-cycle designs"),
        ConfigField.Bool("shuffle", "Shuffle designs"),
        ConfigField.Duration("cycleInterval", "Cycle interval", default = "10s"),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return ClockWidget(config, clockViewModel, settingsViewModel)
    }
}

private class ClockWidget(
    private val config: WidgetConfig,
    private val clockViewModel: ClockViewModel,
    private val settingsViewModel: SettingsViewModel,
) : Widget {
    override val preferredSpan: Int = 2

    @Composable
    override fun Content(modifier: Modifier) {
        val configuredDesign = config.enum("design", clockViewModel.clockDesign.toString()).toIntOrNull()
        val autoCycle = config.bool("autoCycle")
        val shuffle = config.bool("shuffle")
        val cycleInterval = config.durationMillis("cycleInterval", 10_000L).coerceAtLeast(1_000L)

        LaunchedEffect(configuredDesign) {
            if (configuredDesign != null) {
                clockViewModel.updateClockDesign(configuredDesign)
            }
        }

        LaunchedEffect(autoCycle, shuffle, cycleInterval) {
            if (!autoCycle) return@LaunchedEffect

            while (true) {
                delay(cycleInterval)
                if (shuffle) {
                    val next = (1..ClockViewModel.DESIGN_COUNT)
                        .filterNot { it == clockViewModel.clockDesign }
                        .random(Random)
                    clockViewModel.updateClockDesign(next)
                } else {
                    clockViewModel.cycleClockDesign()
                }
            }
        }

        val time by produceState(initialValue = Clock.System.now()) {
            while (true) {
                delay(1000L)
                value = Clock.System.now()
            }
        }
        val localDateTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
        val is24Hour = settingsViewModel.settings.is24HourFormat
        val hour = if (is24Hour) localDateTime.hour else localDateTime.hour % 12
        val hourDisplay = if (!is24Hour && hour == 0) 12 else hour
        val minute = localDateTime.minute
        val isAm = localDateTime.hour < 12

        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (clockViewModel.clockDesign) {
                1 -> DigitalClock(hourDisplay, minute)
                2 -> DigitalClock2(hourDisplay, minute)
                3 -> DigitalClock3(hourDisplay, minute)
                4 -> DigitalClock4(hourDisplay, minute)
                5 -> DigitalClock5(hourDisplay, minute)
                6 -> DigitalClock6(hourDisplay, minute)
                7 -> DigitalClock7(hourDisplay, minute)
                8 -> DigitalClock8(hourDisplay, minute)
                9 -> DigitalClock9(hourDisplay, minute)
                10 -> DigitalClock10(hourDisplay, minute)
                11 -> DigitalClock11(hourDisplay, minute)
            }

            if (!is24Hour) {
                Text(
                    text = if (isAm) "AM" else "PM",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DigitalClock2(hour: Int, minute: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DigitalClockDigit2((hour / 10) % 10)
        DigitalClockDigit2(hour % 10)
        Spacer(modifier = Modifier.width(8.dp))
        DigitalClockDigit2((minute / 10) % 10)
        DigitalClockDigit2(minute % 10)
    }
}
