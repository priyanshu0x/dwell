package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.AmbientVariant
import com.droidslife.screensaver.settings.CinematicVariant
import com.droidslife.screensaver.settings.ConsoleVariant
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel

@Composable
fun DisplaySection(settingsViewModel: SettingsViewModel) {
    val settings = settingsViewModel.settings

    SectionContainer {
        // Mode picker
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Mode")
            RadioRow(
                label = "Cinematic",
                selected = settings.mode == Mode.Cinematic,
                onClick = { settingsViewModel.setMode(Mode.Cinematic) },
                description = "Large clock on a drifting mesh-gradient backdrop",
            )
            RadioRow(
                label = "Ambient",
                selected = settings.mode == Mode.Ambient,
                onClick = { settingsViewModel.setMode(Mode.Ambient) },
                description = "Calm presence — Lumen HUD or Borealis aurora",
            )
            RadioRow(
                label = "Console",
                selected = settings.mode == Mode.Console,
                onClick = { settingsViewModel.setMode(Mode.Console) },
                description = "Modular tile grid — every enabled widget visible at once",
            )
        }

        // Variant picker — shown for the current mode
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Variant")
            when (settings.mode) {
                Mode.Cinematic -> {
                    RadioRow(
                        label = "Dusk",
                        selected = settings.cinematicVariant == CinematicVariant.Dusk,
                        onClick = { settingsViewModel.setCinematicVariant(CinematicVariant.Dusk) },
                        description = "Warm peach / violet / midnight mesh",
                    )
                    RadioRow(
                        label = "Noir",
                        selected = settings.cinematicVariant == CinematicVariant.Noir,
                        onClick = { settingsViewModel.setCinematicVariant(CinematicVariant.Noir) },
                        description = "Near-monochrome — single warm drifting glow",
                    )
                }
                Mode.Ambient -> {
                    RadioRow(
                        label = "Lumen",
                        selected = settings.ambientVariant == AmbientVariant.Lumen,
                        onClick = { settingsViewModel.setAmbientVariant(AmbientVariant.Lumen) },
                        description = "Sci-fi HUD — cyan brackets, orbital dial, telemetry",
                    )
                    RadioRow(
                        label = "Borealis",
                        selected = settings.ambientVariant == AmbientVariant.Borealis,
                        onClick = { settingsViewModel.setAmbientVariant(AmbientVariant.Borealis) },
                        description = "Soft aurora ribbons over a deep night sky",
                    )
                    if (settings.ambientVariant == AmbientVariant.Lumen) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            ToggleRow(
                                label = "Quieter Lumen",
                                checked = settings.quieterLumen,
                                onCheckedChange = settingsViewModel::setQuieterLumen,
                                description = "Hides corner brackets and side telemetry",
                            )
                        }
                    }
                }
                Mode.Console -> {
                    RadioRow(
                        label = "Standard",
                        selected = settings.consoleVariant == ConsoleVariant.Standard,
                        onClick = { settingsViewModel.setConsoleVariant(ConsoleVariant.Standard) },
                        description = "Terminal green accent",
                    )
                    RadioRow(
                        label = "Amber",
                        selected = settings.consoleVariant == ConsoleVariant.Amber,
                        onClick = { settingsViewModel.setConsoleVariant(ConsoleVariant.Amber) },
                        description = "Vintage CRT amber accent",
                    )
                }
            }
        }

        // Clock format
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Clock format")
            RadioRow(
                label = "12-hour (AM / PM)",
                selected = !settings.is24HourFormat,
                onClick = { settingsViewModel.setClockFormat(false) },
            )
            RadioRow(
                label = "24-hour",
                selected = settings.is24HourFormat,
                onClick = { settingsViewModel.setClockFormat(true) },
            )
        }

        // Display options
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Display")
            ToggleRow(
                label = "Show seconds",
                checked = settings.showSeconds,
                onCheckedChange = settingsViewModel::setShowSeconds,
                description = "Append :ss to the clock readout",
            )
            ToggleRow(
                label = "Show date",
                checked = settings.showDate,
                onCheckedChange = settingsViewModel::setShowDate,
            )
        }
    }
}
