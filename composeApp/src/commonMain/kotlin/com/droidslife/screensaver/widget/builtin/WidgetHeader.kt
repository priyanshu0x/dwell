package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts

/**
 * Trailing space the header keeps clear for the chrome-level config gear.
 * `TileGearsOverlay` insets the 24dp gear 12dp from the tile border (36dp of
 * total inset) while the tile content is padded 16dp, so the gear overlaps the
 * rightmost 20dp of the header; 24dp leaves a small gap on top of that.
 */
private val GEAR_RESERVE = 24.dp

/**
 * Header-row height that lands the (vertically-centered) trailing slot on the
 * gear's center line. The tile pads content 14dp from its top and the gear's
 * center sits 24dp down, so a 20dp row centers exactly there (14 + 20/2 = 24).
 * Without it the row height is left to the IconButton's touch-target sizing,
 * which drifts a couple px off the gear.
 */
private val GEAR_ALIGNED_HEIGHT = 20.dp

/**
 * Canonical top-left label row used by every widget tile. Locks down typography
 * (9.sp / SemiBold / 2.25 sp letter-spacing / InterTight / TextLow) so that
 * individual widgets can't drift the way Weather had.
 *
 * The per-widget config gear is **not** rendered here — it lives in the
 * ConsoleMode tile chrome (`TileGearsOverlay`) so it sits above the drag/edit
 * overlay; an in-widget gear gets its clicks eaten by `ConsoleEditOverlay`'s
 * `detectDragGestures`. Passing a non-null [settingsId] signals that a gear is
 * drawn over this tile's top-right corner, so the header reserves [GEAR_RESERVE]
 * on its trailing edge to keep the label / trailing icons from sliding under it.
 */
@Composable
fun WidgetHeader(
    label: String,
    modifier: Modifier = Modifier,
    settingsId: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (settingsId != null) Modifier.height(GEAR_ALIGNED_HEIGHT) else Modifier)
            .padding(end = if (settingsId != null) GEAR_RESERVE else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 2.25.sp,
            color = DwellColors.TextLow,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
