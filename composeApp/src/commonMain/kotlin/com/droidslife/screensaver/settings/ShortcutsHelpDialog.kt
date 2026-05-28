package com.droidslife.screensaver.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.ui.DwellRadius

/**
 * Keyboard shortcut help — modal centered panel grouped by category
 * (Navigation / Mode / Variant / Widgets / System). Matches the Dwell visual
 * language (Surface0/Stroke chrome, Inter Tight headings, JetBrains Mono keys).
 *
 * Closes on Esc, scrim click, or the header X.
 */
@Composable
fun ShortcutsHelpDialog(
    onDismiss: () -> Unit,
    onExitApplication: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            HelpPanel(onDismiss = onDismiss, onExitApplication = onExitApplication)
        }
    }
}

@Composable
private fun HelpPanel(
    onDismiss: () -> Unit,
    onExitApplication: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .widthIn(min = 360.dp, max = 480.dp)
            .heightIn(max = 700.dp)
            .clip(RoundedCornerShape(DwellRadius.xl))
            .background(DwellColors.Surface0)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(DwellRadius.xl)),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Keyboard shortcuts",
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = DwellColors.TextMid)
                }
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ShortcutGroup("Navigation", listOf(
                    "Esc"             to "Hide dashboard",
                    "Alt"             to "Hide dashboard",
                    "Ctrl+Q · Cmd+Q"  to "Quit Dwell",
                    "S · Ctrl+,"      to "Open Settings",
                    "F1 · ?"          to "Show this help",
                ))
                ShortcutGroup("Mode", listOf(
                    "M"   to "Cycle mode (Cinematic → Ambient → Console)",
                    "1"   to "Jump to Cinematic",
                    "2"   to "Jump to Ambient",
                    "3"   to "Jump to Console",
                ))
                ShortcutGroup("Variant", listOf(
                    "V"   to "Cycle variant within the current mode",
                ))
                ShortcutGroup("Widgets", listOf(
                    "W"        to "Toggle widget drawer (Cinematic only)",
                    "L"        to "Toggle layout edit mode (Console only)",
                    "Ctrl+R"   to "Reload widgets from ~/.screensaver/widgets/",
                ))
                TroubleshootingGroup()
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Quit Dwell entirely from the tray icon → Quit.",
                    color = DwellColors.TextLow,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                )
                Text(
                    text = "Close ↩",
                    color = DwellColors.StatusAccent,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(DwellRadius.xs))
                        .background(DwellColors.StatusAccent.copy(alpha = 0.10f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            @Suppress("UNUSED_PARAMETER") onExitApplication
        }
    }
}

@Composable
private fun TroubleshootingGroup() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "TROUBLESHOOTING",
            color = DwellColors.TextLow,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
        )
        listOf(
            "Run `./scripts/dwell doctor` to diagnose JDK / settings issues.",
            "Logs + settings live in ~/.screensaver/ (or %APPDATA%\\dwell on Windows).",
            "Tiles won't move? Settings → Triggers → unlock the dashboard.",
            "Weather stuck on \"—\"? Open Settings → Widgets → Weather and pick a city.",
        ).forEach { line ->
            Text(
                text = "• $line",
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ShortcutGroup(label: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label.uppercase(),
            color = DwellColors.TextLow,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
        )
        rows.forEach { (key, desc) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = key,
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .widthIn(min = 110.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DwellColors.Surface1)
                        .border(1.dp, DwellColors.Stroke, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    text = desc,
                    color = DwellColors.TextMid,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .background(DwellColors.Stroke)
            .height(1.dp),
    )
}

