package com.droidslife.screensaver.calendar

import com.droidslife.screensaver.calendar.providers.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarMathTest {

    @Test
    fun countsByDayIncludesMultiDayEventsOnEveryDayInRange() {
        val events = listOf(
            allDay(id = "vacation", from = LocalDate(2026, 6, 1), to = LocalDate(2026, 6, 3)),
            timed(id = "meeting", on = LocalDate(2026, 6, 2), 9, 0, 10, 0),
        )

        val counts = CalendarMath.countsByDay(events, year = 2026, monthNumber = 6)

        assertEquals(1, counts[LocalDate(2026, 6, 1)])
        assertEquals(2, counts[LocalDate(2026, 6, 2)]) // vacation + meeting
        assertEquals(1, counts[LocalDate(2026, 6, 3)])
    }

    @Test
    fun countsByDayIgnoresEventsOutsideMonth() {
        val events = listOf(
            allDay("a", from = LocalDate(2026, 5, 31), to = LocalDate(2026, 5, 31)),
            allDay("b", from = LocalDate(2026, 7, 1), to = LocalDate(2026, 7, 1)),
            allDay("c", from = LocalDate(2026, 6, 15), to = LocalDate(2026, 6, 15)),
        )

        val counts = CalendarMath.countsByDay(events, year = 2026, monthNumber = 6)

        assertEquals(1, counts.size)
        assertEquals(1, counts[LocalDate(2026, 6, 15)])
    }

    @Test
    fun busyMinutesSumsTimedEvents() {
        val day = LocalDate(2026, 6, 10)
        val events = listOf(
            timed("a", day, 9, 0, 10, 0),    // 60 min
            timed("b", day, 14, 0, 14, 30),  // 30 min
        )

        val busy = CalendarMath.busyMinutesByDay(events, year = 2026, monthNumber = 6)

        assertEquals(90, busy[day])
    }

    @Test
    fun busyMinutesGivesAllDayEventsBaseline() {
        val events = listOf(
            allDay("holiday", from = LocalDate(2026, 6, 4), to = LocalDate(2026, 6, 4)),
        )

        val busy = CalendarMath.busyMinutesByDay(events, year = 2026, monthNumber = 6)

        assertEquals(CalendarMath.ALL_DAY_BASELINE_MINUTES, busy[LocalDate(2026, 6, 4)])
    }

    @Test
    fun busyMinutesSkipsEventsWithoutStart() {
        val events = listOf(
            // A degenerate non-all-day event with no start should be skipped
            // rather than crash the aggregation.
            CalendarEvent(
                id = "weird",
                title = "weird",
                startDate = LocalDate(2026, 6, 5),
                allDay = false,
            ),
        )

        val busy = CalendarMath.busyMinutesByDay(events, year = 2026, monthNumber = 6)

        assertTrue(busy.isEmpty())
    }

    @Test
    fun heatAlphaRampsToCap() {
        assertEquals(0f, CalendarMath.heatAlpha(0))
        // Half a workday → half the cap.
        val half = CalendarMath.heatAlpha(CalendarMath.FULL_HEAT_MINUTES / 2)
        assertTrue(half in 0.19f..0.21f, "expected ~0.20 alpha for half day, got $half")
        // Exceeding the cap stays at the cap, not above it.
        val over = CalendarMath.heatAlpha(CalendarMath.FULL_HEAT_MINUTES * 3)
        assertEquals(0.40f, over)
    }

    private fun allDay(id: String, from: LocalDate, to: LocalDate) = CalendarEvent(
        id = id,
        title = id,
        startDate = from,
        endDate = to,
        allDay = true,
    )

    private fun timed(
        id: String,
        on: LocalDate,
        startHour: Int,
        startMin: Int,
        endHour: Int,
        endMin: Int,
    ) = CalendarEvent(
        id = id,
        title = id,
        startDate = on,
        endDate = on,
        start = LocalDateTime(on, LocalTime(startHour, startMin)),
        end = LocalDateTime(on, LocalTime(endHour, endMin)),
        allDay = false,
    )
}
