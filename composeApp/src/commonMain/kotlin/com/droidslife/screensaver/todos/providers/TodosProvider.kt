package com.droidslife.screensaver.todos.providers

import kotlinx.coroutines.flow.Flow
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

    /** Append a new task. Result carries the underlying error on failure. */
    suspend fun add(text: String): Result<Unit>

    /** Mark a task done / undone. */
    suspend fun toggleDone(id: String, done: Boolean): Result<Unit>

    /** Delete a task. Providers that don't support delete may return failure. */
    suspend fun delete(id: String): Result<Unit>
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
