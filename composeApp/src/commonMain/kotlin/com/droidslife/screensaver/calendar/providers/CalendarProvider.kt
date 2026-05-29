package com.droidslife.screensaver.calendar.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Provider-agnostic representation of a calendar event.
 *
 * Times are stored as wall-clock [LocalDateTime] in the user's current zone for
 * timed events. All-day events use [LocalDate] anchors and leave [start]/[end]
 * null so the widget can render them as a banner instead of a time slot.
 *
 * Recurring events are expanded by the provider before they reach the widget —
 * each occurrence appears as its own [CalendarEvent] with a distinct [id]
 * derived from `<seriesId>::<startDate>`.
 */
data class CalendarEvent(
    /** Stable per-occurrence id. */
    val id: String,
    val title: String,
    val location: String = "",
    /** First day the event covers (always present). */
    val startDate: LocalDate,
    /** Last day the event covers; same as [startDate] for single-day events. */
    val endDate: LocalDate = startDate,
    /** Wall-clock start time, or null for all-day events. */
    val start: LocalDateTime? = null,
    /** Wall-clock end time, or null for all-day events. */
    val end: LocalDateTime? = null,
    /** True when [start]/[end] are null and the event covers whole days. */
    val allDay: Boolean = start == null,
    /**
     * Optional public link to the event (Google Cal / Outlook event page).
     * Used by the widget for tap-through into the source calendar.
     */
    val url: String = "",
)

/**
 * Port for the Calendar widget. Implementations adapt a specific upstream (an
 * ICS feed, a manual config list, a future Google Cal OAuth client, …) into
 * the shared [CalendarEvent] domain type.
 *
 * The widget subscribes to [watch] and renders the latest snapshot; it never
 * mutates events (read-only by design in v1). Providers that fetch remotely
 * decide their own poll cadence — the widget does not drive refresh timing.
 *
 * Implementations should never throw out of [watch]; transport / auth failures
 * should be logged and surfaced as an empty (or stale-cached) snapshot, with
 * the problem reported through [syncStatus].
 */
interface CalendarProvider {
    /** Stable provider id persisted in widget config (e.g. `"none"`, `"ics"`). */
    val id: String

    /** Human-readable label shown in the Settings provider picker. */
    val displayName: String

    /**
     * Live snapshot of every known event, across recent history through the
     * provider's forward window. Sorted by start; recurring series are pre-
     * expanded to one entry per occurrence.
     */
    fun watch(): Flow<List<CalendarEvent>>

    /**
     * Live transport/auth health, so the widget can surface a calm one-line
     * hint instead of the failure only landing in logs. Defaults to always-
     * healthy for purely local providers.
     */
    fun syncStatus(): Flow<CalendarSyncStatus> = flowOf(CalendarSyncStatus.Healthy)
}

/**
 * Health of a provider's background refresh, surfaced as a subtle hint in the
 * widget. Mirrors [com.droidslife.screensaver.todos.providers.TodosSyncStatus]:
 * a single calm line, never a stack trace, with the last good snapshot still
 * visible underneath.
 */
sealed interface CalendarSyncStatus {
    data object Healthy : CalendarSyncStatus

    /** Transient transport problem — retrying. Last snapshot stays visible. */
    data class Offline(val message: String) : CalendarSyncStatus

    /** Configuration is missing / malformed and the user has to act on it. */
    data class Unconfigured(val message: String) : CalendarSyncStatus
}
