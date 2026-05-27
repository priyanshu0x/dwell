package com.droidslife.screensaver.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts

/** Tone of a [WidgetStatusLine], mapped to a calm Dwell status colour. */
enum class WidgetStatusSeverity {
    /** Something is broken and needs the user (auth rejected, bad config). */
    Error,

    /** A transient/degraded condition that's self-healing (offline, retrying, stale). */
    Warning,

    /** Neutral background info ("3 unsynced", "using cached"). */
    Info,
}

/**
 * The one canonical way a widget tells the user something went wrong, so every
 * tile reports problems identically instead of each hand-rolling a `Text`.
 *
 * Deliberately minimal: a single calm line pinned under the tile body. Widgets
 * keep showing their last good content above it — a status line never replaces
 * the data. Pass `null`/blank to render nothing (the common, healthy case), so
 * callers can wire it unconditionally:
 *
 * ```
 * WidgetStatusLine(errorMessage, severity = WidgetStatusSeverity.Warning)
 * ```
 */
@Composable
fun WidgetStatusLine(
    message: String?,
    modifier: Modifier = Modifier,
    severity: WidgetStatusSeverity = WidgetStatusSeverity.Error,
) {
    if (message.isNullOrBlank()) return
    val color = when (severity) {
        WidgetStatusSeverity.Error -> DwellColors.StatusError
        WidgetStatusSeverity.Warning -> DwellColors.StatusAccent
        WidgetStatusSeverity.Info -> DwellColors.TextLow
    }
    Text(
        text = message,
        fontFamily = DwellFonts.interTight(),
        fontSize = 10.sp,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
