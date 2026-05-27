package com.droidslife.screensaver.ui

/**
 * Tiny synchronous key/value store for per-widget UI preferences (e.g. the
 * Todos list/matrix toggle).
 *
 * Deliberately synchronous and coroutine-free: a preference must be on disk by
 * the time the user's click handler returns, so it survives the widget being
 * disposed or the process exiting immediately afterward — the failure mode that
 * made the async `WidgetStorage` path lose the value. Values are tiny, so the
 * blocking I/O on the caller's thread is negligible.
 */
expect object WidgetPrefs {
    /** Returns the stored value for [scopeId]/[key], or null if unset. */
    fun read(scopeId: String, key: String): String?

    /** Persists [value] synchronously before returning. */
    fun write(scopeId: String, key: String, value: String)
}
