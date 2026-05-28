package com.droidslife.screensaver.widget.builtin

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

class PomodoroLedgerTest {

    private fun session(label: String = "x") = PomodoroSession(0L, label, 1500)

    @Test
    fun recordIncrementsDailyCount() {
        val day = LocalDate(2026, 5, 26)
        val h = PomodoroHistory()
            .record(day, session())
            .record(day, session())
        assertEquals(2, h.countOn(day))
    }

    @Test
    fun countsAreIsolatedPerDay() {
        val mon = LocalDate(2026, 5, 25)
        val tue = LocalDate(2026, 5, 26)
        val h = PomodoroHistory().record(mon, session()).record(tue, session()).record(tue, session())
        assertEquals(1, h.countOn(mon))
        assertEquals(2, h.countOn(tue))
    }

    @Test
    fun last7ReturnsSevenCountsOldestToNewest() {
        val today = LocalDate(2026, 5, 26)
        val h = PomodoroHistory()
            .record(today, session())
            .record(today, session())
            .record(today.minus(2, DateTimeUnit.DAY), session())
        val series = h.last7(today)
        assertEquals(7, series.size)
        assertEquals(2, series.last())            // today
        assertEquals(1, series[series.size - 3])  // two days ago
        assertEquals(0, series.first())           // six days ago, empty
    }

    @Test
    fun countInLastDaysSumsWindowInclusiveOfToday() {
        val today = LocalDate(2026, 5, 26)
        val h = PomodoroHistory()
            .record(today, session())
            .record(today.minus(6, DateTimeUnit.DAY), session())
            .record(today.minus(10, DateTimeUnit.DAY), session())
        assertEquals(2, h.countInLastDays(today, 7))
    }

    @Test
    fun pruningDropsDaysOlderThanRetention() {
        val today = LocalDate(2026, 5, 26)
        val old = today.minus(40, DateTimeUnit.DAY)
        val h = PomodoroHistory()
            .record(old, session())
            .record(today, session(), retainDays = 30)
        assertEquals(0, h.countOn(old))
        assertEquals(1, h.countOn(today))
    }

    @Test
    fun pruningBoundaryIsExclusive() {
        // A day exactly retainDays old falls outside the retained window.
        val today = LocalDate(2026, 5, 26)
        val edge = today.minus(30, DateTimeUnit.DAY)
        val h = PomodoroHistory()
            .record(edge, session())
            .record(today, session(), retainDays = 30)
        assertEquals(0, h.countOn(edge))
        assertEquals(1, h.countOn(today))
    }
}
