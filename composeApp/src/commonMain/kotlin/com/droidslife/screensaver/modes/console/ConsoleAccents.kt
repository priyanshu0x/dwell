package com.droidslife.screensaver.modes.console

import androidx.compose.ui.graphics.Color
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
