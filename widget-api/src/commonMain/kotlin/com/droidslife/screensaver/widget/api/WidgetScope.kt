package com.droidslife.screensaver.widget.api

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

/**
 * Host services passed to a widget instance at creation time.
 */
interface WidgetScope {
    /**
     * Lifecycle-bound coroutine scope for background work.
     *
     * The host cancels this scope when the widget is disabled or recreated.
     */
    val coroutineScope: CoroutineScope

    /**
     * Shared HTTP client configured by the host.
     *
     * Widgets should prefer this client over creating their own so requests use
     * the same engine, timeout, and retry behavior as the app.
     */
    val httpClient: HttpClient

    /**
     * Persistent storage scoped to the widget instance/type namespace.
     */
    val storage: WidgetStorage

    /**
     * Logger namespaced to the widget for host-visible diagnostics.
     */
    val log: WidgetLogger
}

/**
 * Small key/value persistence surface for widgets.
 */
interface WidgetStorage {
    /**
     * Reads a previously stored value, returning `null` when the key is missing
     * or cannot be decoded as [type].
     */
    suspend fun <T : Any> read(key: String, type: Class<T>): T?

    /**
     * Stores [value] at [key], replacing any existing value. Host implementations
     * may reject unsupported value types; encode structured state as a String
     * when portability matters.
     */
    suspend fun <T : Any> write(key: String, value: T)

    /**
     * Removes a stored value. Missing keys are ignored.
     */
    suspend fun delete(key: String)
}

/**
 * Widget-scoped logging bridge.
 */
interface WidgetLogger {
    /**
     * Writes an informational diagnostic message.
     */
    fun info(msg: String)

    /**
     * Writes a warning diagnostic, optionally including an exception.
     */
    fun warn(msg: String, error: Throwable? = null)

    /**
     * Writes an error diagnostic, optionally including an exception.
     */
    fun error(msg: String, error: Throwable? = null)
}
