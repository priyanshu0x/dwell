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
     * duration; all-day events contribute [ALL_DAY_BASELINE_MINUTES] per
     * day they cover. A timed event with no DTEND contributes 30 minutes.
     *
     * Overlapping meetings are **unioned** before summing — a 9:00-10:00
     * stacked on a 9:30-10:30 contributes 90 minutes, not 120. Multi-day
     * timed events (a conference Fri 5pm → Sun 11am) are sliced into
     * per-day chunks so each touched day gets its real busy minutes.
     */
    fun busyMinutesByDay(
        events: List<CalendarEvent>,
        year: Int,
        monthNumber: Int,
    ): Map<LocalDate, Int> {
        if (events.isEmpty()) return emptyMap()
        val month = Month.entries[(monthNumber - 1).coerceIn(0, 11)]
        val monthStart = LocalDate(year, month, 1)
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH)
        val monthLast = monthEnd.plus(-1, DateTimeUnit.DAY)

        // Collected separately so all-day baselines stack atop a fully-merged
        // timed total — birthdays + meetings on the same day each contribute.
        val intervalsByDay = mutableMapOf<LocalDate, MutableList<IntInterval>>()
        val allDayMinutesByDay = mutableMapOf<LocalDate, Int>()

        for (e in events) {
            if (e.endDate < monthStart || e.startDate >= monthEnd) continue
            if (e.allDay) {
                var d = if (e.startDate < monthStart) monthStart else e.startDate
                val last = if (e.endDate > monthLast) monthLast else e.endDate
                while (d <= last) {
                    allDayMinutesByDay[d] = (allDayMinutesByDay[d] ?: 0) + ALL_DAY_BASELINE_MINUTES
                    d = d.plus(1, DateTimeUnit.DAY)
                }
            } else {
                for (slice in sliceTimed(e)) {
                    if (slice.date < monthStart || slice.date >= monthEnd) continue
                    intervalsByDay.getOrPut(slice.date) { mutableListOf() } += IntInterval(slice.startMin, slice.endMin)
                }
            }
        }

        val out = mutableMapOf<LocalDate, Int>()
        for ((day, raw) in intervalsByDay) {
            out[day] = unionMinutes(raw)
        }
        for ((day, baseline) in allDayMinutesByDay) {
            out[day] = (out[day] ?: 0) + baseline
        }
        return out
    }

    /**
     * Slice a timed event into one (date, startMinute, endMinute) entry per
     * day it touches. Single-day timed events return a single slice; cross-
     * midnight events fan out into a first-day-tail, full middle days, and
     * a last-day-head. A timed event with no DTEND is treated as a 60-min
     * block starting at DTSTART.
     */
    private fun sliceTimed(event: CalendarEvent): List<TimedSlice> {
        val start = event.start ?: return emptyList()
        val end = event.end
        val startMin = start.time.toSecondOfDay() / 60
        if (end == null) {
            return listOf(TimedSlice(start.date, startMin, (startMin + 60).coerceAtMost(MINUTES_PER_DAY)))
        }
        if (end.date == start.date) {
            val endMin = end.time.toSecondOfDay() / 60
            return if (endMin > startMin) listOf(TimedSlice(start.date, startMin, endMin)) else emptyList()
        }
        val out = mutableListOf<TimedSlice>()
        out += TimedSlice(start.date, startMin, MINUTES_PER_DAY)
        var d = start.date.plus(1, DateTimeUnit.DAY)
        while (d < end.date) {
            out += TimedSlice(d, 0, MINUTES_PER_DAY)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        val tail = end.time.toSecondOfDay() / 60
        if (tail > 0) out += TimedSlice(end.date, 0, tail)
        return out
    }

    /**
     * Sweep-merge a list of half-open intervals [start, end) and return the
     * total covered minutes. Inputs are unsorted; the function sorts in place.
     */
    private fun unionMinutes(intervals: MutableList<IntInterval>): Int {
        if (intervals.isEmpty()) return 0
        intervals.sortBy { it.start }
        var covered = 0
        var curStart = intervals[0].start
        var curEnd = intervals[0].end
        for (i in 1 until intervals.size) {
            val iv = intervals[i]
            if (iv.start > curEnd) {
                covered += (curEnd - curStart)
                curStart = iv.start
                curEnd = iv.end
            } else if (iv.end > curEnd) {
                curEnd = iv.end
            }
        }
        covered += (curEnd - curStart)
        return covered
    }

    private const val MINUTES_PER_DAY = 24 * 60

    /** Tiny per-day interval. [start] / [end] are minutes-of-day, half-open. */
    private data class IntInterval(val start: Int, val end: Int)

    /** One (date, start-of-day, end-of-day) entry produced by [sliceTimed]. */
    private data class TimedSlice(val date: LocalDate, val startMin: Int, val endMin: Int)

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
