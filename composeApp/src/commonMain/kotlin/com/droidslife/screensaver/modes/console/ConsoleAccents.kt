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
    val tileBorderTint: Color,
)

data class ConsoleSurfaceStyle(
    val liquidGlass: Boolean,
    val glassOpacity: Float,
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

val LocalConsoleSurfaceStyle = compositionLocalOf {
    ConsoleSurfaceStyle(liquidGlass = false, glassOpacity = 1f)
}

@Composable
fun consoleNestedSurfaceColor(
    solid: Color = DwellColors.Surface1,
    liquidAlpha: Float = 0.18f,
): Color {
    val style = LocalConsoleSurfaceStyle.current
    return if (style.liquidGlass) {
        solid.copy(alpha = liquidAlpha * style.glassOpacity.coerceIn(0f, 1f))
    } else {
        solid
    }
}

@Composable
fun consoleNestedBorderColor(
    solid: Color = DwellColors.Stroke,
    liquidAlpha: Float = 0.26f,
): Color {
    val style = LocalConsoleSurfaceStyle.current
    return if (style.liquidGlass) {
        solid.copy(alpha = liquidAlpha * style.glassOpacity.coerceIn(0f, 1f))
    } else {
        solid
    }
}
