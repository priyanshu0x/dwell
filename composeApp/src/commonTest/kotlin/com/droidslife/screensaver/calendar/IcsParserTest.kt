package com.droidslife.screensaver.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsParserTest {

    private val windowStart = LocalDate(2026, 1, 1)
    private val windowEnd = LocalDate(2027, 1, 1)

    @Test
    fun parsesNonRecurringAllDayEvent() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:abc@example.com
            DTSTART;VALUE=DATE:20260601
            DTEND;VALUE=DATE:20260602
            SUMMARY:Team offsite
            LOCATION:HQ
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        assertEquals(1, events.size)
        val e = events.single()
        assertEquals("Team offsite", e.title)
        assertEquals("HQ", e.location)
        assertEquals(LocalDate(2026, 6, 1), e.startDate)
        assertEquals(LocalDate(2026, 6, 1), e.endDate)
        assertTrue(e.allDay)
    }

    @Test
    fun parsesTimedEvent() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:meeting@example.com
            DTSTART:20260615T140000Z
            DTEND:20260615T150000Z
            SUMMARY:1:1 with Alex
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        assertEquals(1, events.size)
        val e = events.single()
        assertEquals("1:1 with Alex", e.title)
        assertEquals(LocalDate(2026, 6, 15), e.startDate)
        assertEquals(14, e.start?.hour)
        assertEquals(15, e.end?.hour)
        assertEquals(false, e.allDay)
    }

    @Test
    fun unfoldsContinuationLines() {
        // Per RFC 5545, lines longer than 75 octets are folded with CRLF + space.
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART:20260601T090000\nSUMMARY:A long\n  title\nEND:VEVENT\nEND:VCALENDAR"
        val events = IcsParser.parse(ics, windowStart, windowEnd)
        assertEquals("A long title", events.single().title)
    }

    @Test
    fun expandsWeeklyByDayWithCount() {
        // FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=6 — should yield 6 occurrences.
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:standup@example.com
            DTSTART:20260601T090000
            DTEND:20260601T091500
            SUMMARY:Standup
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=6
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        // June 1 2026 is a Monday → Mon/Wed/Fri × 2 weeks = 6 occurrences.
        assertEquals(6, events.size)
        assertEquals(LocalDate(2026, 6, 1), events.first().startDate)
        assertEquals(LocalDate(2026, 6, 12), events.last().startDate)
    }

    @Test
    fun expandsYearlyForBirthday() {
        // Yearly birthday — should land once per year inside our window.
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:bday@example.com
            DTSTART;VALUE=DATE:20200714
            SUMMARY:Mom's birthday
            RRULE:FREQ=YEARLY
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        // Window is 2026-01-01..2027-01-01 — exactly one occurrence at 2026-07-14.
        assertEquals(1, events.size)
        assertEquals(LocalDate(2026, 7, 14), events.single().startDate)
    }

    @Test
    fun honorsUntilBound() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:daily@example.com
            DTSTART:20260601T090000
            DTEND:20260601T093000
            SUMMARY:Daily standup
            RRULE:FREQ=DAILY;UNTIL=20260605T000000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        // 06-01 through 06-04 inclusive — UNTIL stops the series, exclusive of 06-05.
        assertEquals(4, events.size)
        assertEquals(LocalDate(2026, 6, 1), events.first().startDate)
        assertEquals(LocalDate(2026, 6, 4), events.last().startDate)
    }

    @Test
    fun decodesEscapedSummary() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:esc@example.com
            DTSTART;VALUE=DATE:20260601
            SUMMARY:Quarterly review\, Q2\nbring deck
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        assertEquals("Quarterly review, Q2\nbring deck", events.single().title)
    }

    @Test
    fun dropsEventsOutsideWindow() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:past@example.com
            DTSTART;VALUE=DATE:20200101
            SUMMARY:Way before
            END:VEVENT
            BEGIN:VEVENT
            UID:in@example.com
            DTSTART;VALUE=DATE:20260601
            SUMMARY:In range
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics, windowStart, windowEnd)

        assertEquals(1, events.size)
        assertEquals("In range", events.single().title)
    }
}
