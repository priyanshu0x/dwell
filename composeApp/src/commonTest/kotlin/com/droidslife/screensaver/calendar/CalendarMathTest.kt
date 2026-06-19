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
    fun busyMinutesUnionsOverlappingMeetings() {
        val day = LocalDate(2026, 6, 10)
        val events = listOf(
            timed("a", day, 9, 0, 10, 0),    // 9:00-10:00
            timed("b", day, 9, 30, 10, 30),  // 9:30-10:30, overlaps a by 30 min
        )

        val busy = CalendarMath.busyMinutesByDay(events, year = 2026, monthNumber = 6)

        // Union [9:00, 10:30) = 90 minutes wall-clock, not 60+60=120.
        assertEquals(90, busy[day])
    }

    @Test
    fun busyMinutesCountsTangentMeetingsAsContinuous() {
        val day = LocalDate(2026, 6, 10)
        val events = listOf(
            timed("a", day, 9, 0, 10, 0),
            timed("b", day, 10, 0, 11, 0),
        )

        val busy = CalendarMath.busyMinutesByDay(events, year = 2026, monthNumber = 6)

        // 9-10 and 10-11 touch but don't overlap; we treat them as a single
        // 120-min block so back-to-back meetings register as continuous busy.
        assertEquals(120, busy[day])
    }

    @Test
    fun busyMinutesSlicesMultiDayTimedEventAcrossDays() {
        // A conference Fri 17:00 → Sun 11:00 should attribute the real
        // wall-clock minutes to each day it covers, not just the start day.
        val friday = LocalDate(2026, 6, 5)
        val saturday = LocalDate(2026, 6, 6)
        val sunday = LocalDate(2026, 6, 7)
        val event = CalendarEvent(
            id = "conf",
            title = "Offsite",
            startDate = friday,
            endDate = sunday,
            start = LocalDateTime(friday, LocalTime(17, 0)),
            end = LocalDateTime(sunday, LocalTime(11, 0)),
            allDay = false,
        )

        val busy = CalendarMath.busyMinutesByDay(listOf(event), year = 2026, monthNumber = 6)

        assertEquals(7 * 60, busy[friday])     // 17:00 → midnight
        assertEquals(24 * 60, busy[saturday])  // whole day
        assertEquals(11 * 60, busy[sunday])    // midnight → 11:00
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
