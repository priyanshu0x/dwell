package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts

private val IdleTimeoutSteps = listOf(1, 2, 5, 10, 15, 30, 60)

@Composable
fun TriggersSection(settingsViewModel: SettingsViewModel) {
    val settings = settingsViewModel.settings

    SectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Idle timeout")
            BodyText(
                "How long the system stays idle before Dwell takes over.",
                dim = true,
            )
            IdleTimeoutChips(
                selected = settings.idleTimeoutMinutes,
                onSelect = settingsViewModel::setIdleTimeoutMinutes,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Dismiss behavior")
            ToggleRow(
                label = "Exit on keypress",
                checked = settings.exitOnKeypress,
                onCheckedChange = settingsViewModel::setExitOnKeypress,
                description = "Any key (besides shortcuts) dismisses the dashboard",
            )
            ToggleRow(
                label = "Lock dashboard layout",
                checked = settings.dashboardLocked,
                onCheckedChange = settingsViewModel::setDashboardLocked,
                description = "Tiles ignore drag/resize until you press L to enter Edit Layout",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Startup")
            ToggleRow(
                label = "Start with system",
                checked = settings.startWithSystem,
                onCheckedChange = settingsViewModel::setStartWithSystem,
                description = "Launch Dwell automatically when you sign in",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Future triggers")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Run on lock screen",
                        color = DwellColors.TextLow,
                        fontFamily = DwellFonts.interTight(),
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Windows-only. Not yet available.",
                        color = DwellColors.TextMid,
                        fontFamily = DwellFonts.interTight(),
                        fontSize = 11.sp,
                    )
                }
                ComingSoonChip()
            }
        }
    }
}

@Composable
private fun IdleTimeoutChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IdleTimeoutSteps.forEach { minutes ->
            val isSelected = selected == minutes
            val bg = if (isSelected) DwellColors.StatusAccent.copy(alpha = 0.14f) else DwellColors.Surface1
            val fg = if (isSelected) DwellColors.TextHigh else DwellColors.TextMid
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, DwellColors.Stroke, RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(minutes) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${minutes}m",
                    color = fg,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
