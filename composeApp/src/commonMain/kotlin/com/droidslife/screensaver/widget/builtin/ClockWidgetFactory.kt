package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ClockWidgetFactory(
    private val settingsViewModel: SettingsViewModel,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.clock"
    override val displayName: String = "Clock"
    override val description: String = "Digital clock display"
    override val category: WidgetCategory = WidgetCategory.CLOCK
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 4, minRows = 3,
        defaultCols = 7, defaultRows = 4,
        maxCols = 12, maxRows = 6,
    )
    override val configSchema: List<ConfigField> = emptyList()

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return ClockWidget(settingsViewModel)
    }
}

private class ClockWidget(
    private val settingsViewModel: SettingsViewModel,
) : Widget {
    override val preferredSpan: Int = 2

    override fun summary(): WidgetSummary {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val hh = now.hour.toString().padStart(2, '0')
        val mm = now.minute.toString().padStart(2, '0')
        return WidgetSummary(
            primaryValue = "$hh:$mm",
            primaryLabel = "Time",
            subtitle = formatDateLine(now.dayOfWeek, now.month, now.day),
        )
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val is24Hour = settingsViewModel.settings.is24HourFormat
        val showSeconds = settingsViewModel.settings.showSeconds
        val showDate = settingsViewModel.settings.showDate

        val time by produceState(initialValue = Clock.System.now()) {
            while (true) {
                delay(if (showSeconds) 1_000L else 15_000L)
                value = Clock.System.now()
            }
        }
        val now = time.toLocalDateTime(TimeZone.currentSystemDefault())
        val hourDisplay = if (is24Hour) {
            now.hour
        } else {
            (now.hour % 12).let { if (it == 0) 12 else it }
        }
        val text = buildString {
            append(hourDisplay.toString().padStart(2, '0'))
            append(':')
            append(now.minute.toString().padStart(2, '0'))
            if (showSeconds) {
                append(':')
                append(now.second.toString().padStart(2, '0'))
            }
            if (!is24Hour) append(if (now.hour < 12) " AM" else " PM")
        }

        val city = settingsViewModel.settings.widgetConfigs[
            "com.droidslife.screensaver.weather",
        ]?.get("city")?.toString()?.trim('"')
        val label = if (!city.isNullOrBlank()) "TIME · ${city.uppercase()}" else "TIME"

        Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = label,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 2.25.sp,
                color = DwellColors.TextLow,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = text,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Bold,
                fontSize = 160.sp,
                letterSpacing = (-6.4).sp,
                color = DwellColors.TextHigh,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
            if (showDate) {
                Text(
                    text = formatDateLine(now.dayOfWeek, now.month, now.day).uppercase(),
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 2.1.sp,
                    color = DwellColors.TextMid,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

private fun formatDateLine(dayOfWeek: DayOfWeek, month: Month, day: Int): String {
    val weekday = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }
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
    return "$weekday · $monthName $day"
}
