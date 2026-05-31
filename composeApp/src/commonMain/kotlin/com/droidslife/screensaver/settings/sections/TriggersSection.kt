package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellChoiceChip
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts

private data class IdleTimeoutOption(
    val seconds: Int,
    val label: String,
)

private val IdleTimeoutOptions = listOf(
    IdleTimeoutOption(30, "30s"),
    IdleTimeoutOption(60, "1m"),
    IdleTimeoutOption(2 * 60, "2m"),
    IdleTimeoutOption(5 * 60, "5m"),
    IdleTimeoutOption(10 * 60, "10m"),
    IdleTimeoutOption(15 * 60, "15m"),
    IdleTimeoutOption(30 * 60, "30m"),
    IdleTimeoutOption(60 * 60, "60m"),
)

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
                selected = settings.idleTimeoutSeconds,
                onSelect = settingsViewModel::setIdleTimeoutSeconds,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Dismiss behavior")
            ToggleRow(
                label = "Exit on keypress",
                checked = settings.exitOnKeypress,
                onCheckedChange = settingsViewModel::setExitOnKeypress,
                description = "Any key (besides shortcuts) dismisses the dashboard",
            )
            ToggleRow(
                label = "Right-click hides dashboard",
                checked = settings.rightClickHidesDashboard,
                onCheckedChange = settingsViewModel::setRightClickHidesDashboard,
                description = "Secondary-click anywhere on the dashboard to dismiss it",
            )
            ToggleRow(
                label = "Lock dashboard layout",
                checked = settings.dashboardLocked,
                onCheckedChange = settingsViewModel::setDashboardLocked,
                description = "Tiles ignore drag/resize until you press L to enter Edit Layout",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val accent = DwellColors.StatusAccent
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IdleTimeoutOptions.forEach { option ->
            val isSelected = selected == option.seconds
            DwellChoiceChip(
                label = option.label,
                selected = isSelected,
                color = accent,
                onClick = { onSelect(option.seconds) },
            )
        }
    }
}
