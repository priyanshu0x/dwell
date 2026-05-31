package com.droidslife.screensaver.modes.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.droidslife.screensaver.settings.ConsoleWidgetBorderStyle
import com.droidslife.screensaver.settings.ConsoleVariant
import com.droidslife.screensaver.ui.DwellColors

/** Per-variant accent tokens for Console mode. */
data class ConsoleAccent(
    val primary: Color,
    val tileHoverOverlay: Color,
)

fun consoleAccentFor(variant: ConsoleVariant): ConsoleAccent = when (variant) {
    ConsoleVariant.Standard -> ConsoleAccent(
        primary = DwellColors.ConsoleGreen,
        tileHoverOverlay = DwellColors.ConsoleGreen.copy(alpha = 0.035f),
    )
    ConsoleVariant.Amber -> ConsoleAccent(
        primary = DwellColors.ConsoleAmber,
        tileHoverOverlay = DwellColors.ConsoleAmber.copy(alpha = 0.04f),
    )
    ConsoleVariant.Dark -> ConsoleAccent(
        primary = Color(0xFFB8BCC7),
        tileHoverOverlay = Color.White.copy(alpha = 0.04f),
    )
}

/**
 * Provides the current [ConsoleAccent] to widgets rendered inside Console mode,
 * so they can pick up the active variant's primary/border tint without needing
 * to know about [ConsoleVariant] directly. Default falls back to Standard green.
 */
val LocalConsoleAccent = compositionLocalOf<ConsoleAccent> {
    ConsoleAccent(
        primary = DwellColors.ConsoleGreen,
        tileHoverOverlay = DwellColors.ConsoleGreen.copy(alpha = 0.035f),
    )
}

val LocalConsoleWidgetBorderStyle = compositionLocalOf<ConsoleWidgetBorderStyle?> { null }

@Composable
fun consoleNestedSurfaceColor(
    solid: Color = DwellColors.Surface1,
): Color = solid

@Composable
fun consoleNestedBorderColor(
    solid: Color = DwellColors.Stroke,
): Color = solid
