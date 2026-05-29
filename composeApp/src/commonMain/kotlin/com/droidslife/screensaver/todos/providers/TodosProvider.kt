package com.droidslife.screensaver.todos.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * Provider-agnostic representation of a single todo.
 *
 * The id space is provider-defined: [LocalTodosProvider] uses synthesized
 * `todo-<timestamp>-<n>` strings, while [TodoistProvider] passes through
 * Todoist's numeric task ids. Widgets must treat ids as opaque.
 */
data class Todo(
    val id: String,
    val text: String,
    val done: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * Todoist priority on the API's own scale: 1 = normal (UI "p4") …
     * 4 = urgent (UI "p1"). Providers without a notion of priority (e.g.
     * [LocalTodosProvider]) always report 1.
     */
    val priority: Int = 1,
    /** Optional scheduled date, surfaced as an overdue / today / upcoming chip. */
    val due: TodoDue? = null,
    /** Parent task id when this is a subtask; null for a top-level task. */
    val parentId: String? = null,
    /** Longer description / notes shown in the task detail view. */
    val note: String = "",
)

/**
 * A task's schedule. [date] is always present; [time] is set only when the
 * task is due at a specific time of day. The widget derives overdue / today /
 * upcoming by comparing [date] to the local "today".
 */
data class TodoDue(
    val date: LocalDate,
    val time: LocalTime? = null,
)

/**
 * Port for the Todos widget. Implementations adapt a specific upstream
 * (this machine's storage, Todoist, etc.) into the shared [Todo] domain type.
 *
 * The widget subscribes to [watch] and renders the latest snapshot; mutations
 * go through [add] / [toggleDone] / [delete] and are expected to surface
 * in the next emission. Providers that talk to remote APIs may poll on
 * their own cadence — the widget does not drive refresh timing.
 *
 * Implementations should never throw out of [watch]; transport / auth failures
 * should be logged and surfaced as an empty (or stale-cached) snapshot. The
 * mutating methods return [Result] so the widget can react to per-operation
 * failures (e.g. show a transient "couldn't save" hint) without losing the
 * subscription.
 */
interface TodosProvider {
    /** Stable provider id persisted in widget config (e.g. `"local"`, `"todoist"`). */
    val id: String

    /** Human-readable label shown in the Settings provider picker. */
    val displayName: String

    /** Whether the provider requires the user to configure an API key secret. */
    val requiresApiKey: Boolean

    /**
     * Live snapshot of the current list. A new snapshot is emitted whenever
     * the underlying source changes (local mutation, remote poll tick, etc.).
     *
     * The flow stays hot for the lifetime of the provider; cancellation is
     * driven by the collecting coroutine scope.
     */
    fun watch(): Flow<List<Todo>>

    /**
     * Live transport/auth health of the provider's background refresh, so the
     * widget can surface a calm one-line hint instead of the failure only
     * landing in logs. Defaults to always-healthy for purely local providers;
     * remote providers override this to report auth / connectivity problems.
     */
    fun syncStatus(): Flow<TodosSyncStatus> = flowOf(TodosSyncStatus.Healthy)

    /** Append a new task. Result carries the underlying error on failure. */
    suspend fun add(text: String, priority: Int = 1, due: TodoDue? = null): Result<Unit>

    /** Edit an existing task's text. */
    suspend fun update(id: String, text: String): Result<Unit>

    /** Set a task's importance (Todoist priority scale: 1 = normal … 4 = urgent). */
    suspend fun setPriority(id: String, priority: Int): Result<Unit>

    /** Set or clear a task's due date (null clears it). */
    suspend fun setDue(id: String, due: TodoDue?): Result<Unit>

    /** Mark a task done / undone. */
    suspend fun toggleDone(id: String, done: Boolean): Result<Unit>

    /** Delete a task. Providers that don't support delete may return failure. */
    suspend fun delete(id: String): Result<Unit>
}

/**
 * Health of a provider's background refresh, surfaced as a subtle hint in the
 * widget. Deliberately small: the widget shows one calm line, never a stack
 * trace, and keeps showing the last good snapshot underneath.
 */
sealed interface TodosSyncStatus {
    /** Polling is succeeding (or the provider is purely local). */
    data object Healthy : TodosSyncStatus

    /**
     * A transient transport problem; the provider keeps retrying on its own
     * cadence and the last snapshot stays visible. [message] is user-facing.
     */
    data class Offline(val message: String) : TodosSyncStatus

    /**
     * The credentials were rejected (e.g. HTTP 401) — retrying won't help until
     * the user enters a fresh token. [message] is user-facing.
     */
    data class AuthFailed(val message: String) : TodosSyncStatus
}

/**
 * Thrown by provider implementations when the upstream API requires
 * credentials that haven't been configured. The widget maps this to a
 * "Add an API token in Settings" guidance state.
 */
class TodosProviderUnconfigured(message: String) : Exception(message)

/**
 * Thrown by provider implementations when the upstream rejected the
 * configured credentials (e.g. HTTP 401 from Todoist). The widget treats
 * this the same as [TodosProviderUnconfigured] for messaging purposes —
 * the only fix is to enter a fresh token — but the distinct type lets
 * future UI surface a "token expired" hint.
 */
class TodosProviderUnauthorized(message: String) : Exception(message)
