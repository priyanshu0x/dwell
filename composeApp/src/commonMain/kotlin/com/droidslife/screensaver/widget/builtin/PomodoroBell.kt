package com.droidslife.screensaver.widget.builtin

/**
 * Plays a brief platform-native alert when a pomodoro phase elapses.
 *
 * Implementations should swallow any failure (missing audio, headless JVM, …)
 * so an absent bell never disrupts the timer. The single-shot signature keeps
 * the contract small enough to actual-stub on Android/iOS targets when those
 * arrive.
 */
expect fun playPomodoroBell()
