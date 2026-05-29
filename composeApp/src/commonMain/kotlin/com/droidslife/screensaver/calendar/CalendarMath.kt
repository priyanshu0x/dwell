package com.droidslife.screensaver.calendar

import com.droidslife.screensaver.calendar.providers.CalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Pure date / event aggregation for the Calendar widget. Lifted out of the
 * widget composable so the calculations can be exercised by unit tests
 * without spinning up Compose.
 */
object CalendarMath {

    /**
     * All-day events have no real duration; we attribute a flat 4-hour
     * "soft commitment" to them when computing busy-time heat. Birthdays,
     * holidays, and PTO show up but don't drown out actual meeting days.
     */
    const val ALL_DAY_BASELINE_MINUTES: Int = 4 * 60

    /**
     * A day that hits or exceeds 8 hours of busy time gets full heat
     * saturation. Tuned to match a typical "meetings-back-to-back" workday.
     */
    const val FULL_HEAT_MINUTES: Int = 8 * 60

    /**
     * Count distinct events covering each day of the given month. Events
     * that span multiple days (vacations, conferences) contribute to every
     * day they touch within the month.
     */
    fun countsByDay(events: List<CalendarEvent>, year: Int, monthNumber: Int): Map<LocalDate, Int> {
        if (events.isEmpty()) return emptyMap()
        val out = mutableMapOf<LocalDate, Int>()
        val month = Month.entries[(monthNumber - 1).coerceIn(0, 11)]
        val monthStart = LocalDate(year, month, 1)
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH)
        for (e in events) {
            if (e.endDate < monthStart || e.startDate >= monthEnd) continue
            var d = if (e.startDate < monthStart) monthStart else e.startDate
            val last = if (e.endDate >= monthEnd) monthEnd.plus(-1, DateTimeUnit.DAY) else e.endDate
            while (d <= last) {
                out[d] = (out[d] ?: 0) + 1
                d = d.plus(1, DateTimeUnit.DAY)
            }
        }
        return out
    }

    /**
     * Sum busy-minutes per day. Timed events contribute their wall-clock
     * duration; all-day events contribute [ALL_DAY_BASELINE_MINUTES] per day
     * they cover. A timed event with no DTEND contributes 30 minutes.
     */
    fun busyMinutesByDay(
        events: List<CalendarEvent>,
        year: Int,
        monthNumber: Int,
    ): Map<LocalDate, Int> {
        if (events.isEmpty()) return emptyMap()
        val out = mutableMapOf<LocalDate, Int>()
        val month = Month.entries[(monthNumber - 1).coerceIn(0, 11)]
        val monthStart = LocalDate(year, month, 1)
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH)

        for (e in events) {
            if (e.endDate < monthStart || e.startDate >= monthEnd) continue
            if (e.allDay) {
                var d = if (e.startDate < monthStart) monthStart else e.startDate
                val last = if (e.endDate >= monthEnd) monthEnd.plus(-1, DateTimeUnit.DAY) else e.endDate
                while (d <= last) {
                    out[d] = (out[d] ?: 0) + ALL_DAY_BASELINE_MINUTES
                    d = d.plus(1, DateTimeUnit.DAY)
                }
            } else {
                val start = e.start ?: continue
                val day = start.date
                if (day < monthStart || day >= monthEnd) continue
                val mins = e.end?.let {
                    // Wall-clock minutes within the same day. Multi-day timed
                    // events are rare in practice; for v1 we attribute the
                    // span to the start day only.
                    val s = start.time.toSecondOfDay()
                    val en = it.time.toSecondOfDay()
                    if (it.date == start.date) ((en - s) / 60).coerceAtLeast(0) else 60
                } ?: 30
                out[day] = (out[day] ?: 0) + mins
            }
        }
        return out
    }

    /**
     * Maps a busy-minutes count to an 8-bit heat intensity (0..255) for use
     * as an alpha channel on a tinted background. Caps at
     * [FULL_HEAT_MINUTES] so a 12-hour day doesn't push past full saturation.
     */
    fun heatAlpha(busyMinutes: Int, maxAlpha: Float = 0.40f): Float {
        if (busyMinutes <= 0) return 0f
        val ratio = (busyMinutes.toFloat() / FULL_HEAT_MINUTES).coerceAtMost(1f)
        return ratio * maxAlpha
    }
}
