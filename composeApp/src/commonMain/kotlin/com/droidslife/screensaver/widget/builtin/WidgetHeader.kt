package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Canonical top-left label row used by every widget tile. Locks down typography
 * (9.sp / SemiBold / 2.25 sp letter-spacing / InterTight / TextLow) so that
 * individual widgets can't drift the way Weather had.
 *
 * The per-widget config gear is **not** rendered here — it lives in the
 * ConsoleMode tile chrome (`TileGearsOverlay`) so it sits above the drag/edit
 * overlay; an in-widget gear gets its clicks eaten by `ConsoleEditOverlay`'s
 * `detectDragGestures`. The [settingsId] parameter is retained for API
 * symmetry with the chrome-level gear lookup.
 */
@Composable
fun WidgetHeader(
    label: String,
    modifier: Modifier = Modifier,
    @Suppress("unused") settingsId: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
