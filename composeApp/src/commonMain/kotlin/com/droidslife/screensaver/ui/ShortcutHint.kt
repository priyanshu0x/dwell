package com.droidslife.screensaver.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One source of truth for keyboard-shortcut tooltips. Wrap any interactive
 * element; after [delayMillis] of hover the keymap chip appears above it.
 *
 * Pass a blank/empty [shortcut] to disable — the wrapper falls through to
 * its content with no tooltip wiring at all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutHint(
    shortcut: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = 400,
    content: @Composable () -> Unit,
) {
    if (shortcut.isBlank()) {
        Box(modifier) { content() }
        return
    }
    TooltipArea(
        tooltip = { ShortcutChip(shortcut) },
        delayMillis = delayMillis,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = androidx.compose.ui.Alignment.TopCenter,
            alignment = androidx.compose.ui.Alignment.BottomCenter,
            offset = DpOffset(0.dp, (-6).dp),
        ),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
private fun ShortcutChip(shortcut: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DwellColors.Surface0.copy(alpha = 0.96f))
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = shortcut,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )
    }
}
