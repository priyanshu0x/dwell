package com.droidslife.screensaver.widget.builtin

/**
 * A phase-change alert. [playSound] and [showNotification] mirror the widget's
 * config toggles, so the platform actual can honor them without re-reading
 * config.
 */
data class PomodoroAlert(
    val title: String,
    val message: String,
    val playSound: Boolean,
    val showNotification: Boolean,
)

/**
 * Plays an audible chime and/or posts a desktop notification when a pomodoro
 * phase elapses. Implementations MUST swallow every failure (no audio device,
 * headless JVM, no system tray) so an absent alert never disrupts the timer,
 * and MUST NOT block the calling thread.
 */
expect fun firePomodoroAlert(alert: PomodoroAlert)
