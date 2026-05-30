package com.droidslife.screensaver.modes.console

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.droidslife.screensaver.settings.ConsoleWidgetBorderStyle
import com.droidslife.screensaver.settings.ConsoleVariant
import com.droidslife.screensaver.ui.DwellColors

/** Per-variant accent tokens for Console mode. */
data class ConsoleAccent(
    val primary: Color,
    val tileBorderTint: Color,
)

fun consoleAccentFor(variant: ConsoleVariant): ConsoleAccent = when (variant) {
    ConsoleVariant.Standard -> ConsoleAccent(
        primary = DwellColors.ConsoleGreen,
        tileBorderTint = Color.Transparent,
    )
    ConsoleVariant.Amber -> ConsoleAccent(
        primary = DwellColors.ConsoleAmber,
        tileBorderTint = DwellColors.ConsoleAmber.copy(alpha = 0.02f),
    )
}

/**
 * Provides the current [ConsoleAccent] to widgets rendered inside Console mode,
 * so they can pick up the active variant's primary/border tint without needing
 * to know about [ConsoleVariant] directly. Default falls back to Standard green.
 */
val LocalConsoleAccent = compositionLocalOf<ConsoleAccent> {
    ConsoleAccent(primary = DwellColors.ConsoleGreen, tileBorderTint = Color.Transparent)
}

val LocalConsoleWidgetBorderStyle = compositionLocalOf<ConsoleWidgetBorderStyle?> { null }
