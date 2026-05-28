package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class CalendarWidgetFactory : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.calendar"
    override val displayName: String = "Calendar"
    override val description: String = "Simple month grid with today highlighted"
    override val category: WidgetCategory = WidgetCategory.PRODUCTIVITY
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 4, defaultRows = 2,
        maxCols = 6, maxRows = 4,
    )
    override val configSchema: List<ConfigField> = emptyList()

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = CalendarWidget()
}

private class CalendarWidget : Widget {
    override fun summary(): WidgetSummary {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return WidgetSummary(
            primaryValue = today.day.toString(),
            primaryLabel = monthShortName(today.month),
            subtitle = today.year.toString(),
        )
    }

    @Composable
    override fun Content(modifier: Modifier) {
        CalendarMonth(modifier)
    }
}

@Composable
private fun CalendarMonth(modifier: Modifier) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val firstOfMonth = LocalDate(today.year, today.month, 1)
    // ISO: Monday=1..Sunday=7. We render Sunday-first; offset so column 0 is Sunday.
    val firstWeekday = firstOfMonth.dayOfWeek
    val leadingBlanks = when (firstWeekday) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
    }
    val daysInMonth = daysInMonth(today.year, today.month)
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7 // up to 6 rows

    val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    val monthYear = "${monthShortName(today.month).uppercase()} ${today.year}"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WidgetHeader(label = monthYear)
        Spacer(Modifier.height(4.dp))
        // weekday header row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekdayLabels.forEach { label ->
                Box(modifier = Modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = DwellColors.TextLow,
                        fontFamily = DwellFonts.interTight(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val isToday = dayNumber == today.day
                        DayCell(dayNumber, isToday)
                    } else {
                        Box(modifier = Modifier.padding(horizontal = 2.dp).padding(vertical = 2.dp)) {
                            Text(
                                text = " ",
                                fontSize = 11.sp,
                                color = DwellColors.TextFaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, isToday: Boolean) {
    val accent = LocalConsoleAccent.current.primary
    val bg = if (isToday) accent.copy(alpha = 0.08f) else Color.Transparent
    val textColor = if (isToday) accent else DwellColors.TextFaint
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            fontSize = 11.sp,
            color = textColor,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

private fun daysInMonth(year: Int, month: Month): Int {
    val firstNextMonth = if (month == Month.DECEMBER) {
        LocalDate(year + 1, Month.JANUARY, 1)
    } else {
        LocalDate(year, Month.entries[month.ordinal + 1], 1)
    }
    val lastOfMonth = LocalDate(firstNextMonth.year, firstNextMonth.month, 1)
    // Compute as days between first-of-month and first-of-next-month.
    val first = LocalDate(year, month, 1)
    return (lastOfMonth.toEpochDays() - first.toEpochDays()).toInt()
}

private fun monthShortName(month: Month): String = when (month) {
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
