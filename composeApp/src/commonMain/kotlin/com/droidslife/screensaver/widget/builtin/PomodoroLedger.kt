package com.droidslife.screensaver.widget.builtin

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

/** One completed work session. */
@Serializable
data class PomodoroSession(
    val startEpochMillis: Long,
    val label: String,
    val durationSeconds: Int,
)

/** A single calendar day's tally. [count] is derived so it can't drift from [sessions]. */
@Serializable
data class PomodoroDay(
    val sessions: List<PomodoroSession> = emptyList(),
) {
    val count: Int get() = sessions.size
}

/**
 * Per-day completed-session history. Pure and clock-free: every query takes the
 * relevant [LocalDate], so it is fully unit-testable. Days are keyed by ISO date
 * string (`yyyy-mm-dd`) so the map serializes cleanly.
 */
@Serializable
data class PomodoroHistory(
    val days: Map<String, PomodoroDay> = emptyMap(),
) {
    /** Adds [session] to [date]'s tally and prunes days older than [retainDays]. */
    fun record(date: LocalDate, session: PomodoroSession, retainDays: Int = 30): PomodoroHistory {
        val key = date.toString()
        val existing = days[key] ?: PomodoroDay()
        val updated = existing.copy(sessions = existing.sessions + session)
        // Keep only the most recent [retainDays] days. Corrupt or migrated keys
        // that don't parse are treated as expired rather than crashing the filter.
        val cutoff = date.minus(retainDays, DateTimeUnit.DAY)
        val pruned = (days + (key to updated)).filterKeys {
            runCatching { LocalDate.parse(it) > cutoff }.getOrDefault(false)
        }
        return PomodoroHistory(pruned)
    }

    fun countOn(date: LocalDate): Int = days[date.toString()]?.count ?: 0

    /** Total completed sessions over the last [days] calendar days, including [today]. */
    fun countInLastDays(today: LocalDate, days: Int): Int =
        (0 until days).sumOf { countOn(today.minus(it, DateTimeUnit.DAY)) }

    /** Counts for the last 7 days, oldest first, newest (today) last — for the sparkline. */
    fun last7(today: LocalDate): List<Int> =
        (6 downTo 0).map { countOn(today.minus(it, DateTimeUnit.DAY)) }
}
