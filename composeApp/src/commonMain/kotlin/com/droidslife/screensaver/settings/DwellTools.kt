package com.droidslife.screensaver.settings

/**
 * Surfaces a handful of CLI / filesystem utilities to the in-app Settings
 * sidebar so non-CLI users can reach the same diagnostics the docs reference.
 *
 * Each platform target supplies its own implementation; non-JVM stubs may
 * return a friendly "not supported" message rather than throw.
 */

/** Runs `dwell doctor` and returns its combined stdout/stderr. */
expect suspend fun runDwellDoctor(): String

/** Reveals the directory holding Dwell's settings + logs in the OS file browser. */
expect fun openDwellConfigFolder(): Boolean
