package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.ProfileKind
import com.droidslife.screensaver.settings.SettingsViewModel

@Composable
fun ProfilesSection(settingsViewModel: SettingsViewModel) {
    val settings = settingsViewModel.settings
    val grouped = settingsViewModel.profiles.groupBy { it.kind }

    SectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Active profile")
            grouped[ProfileKind.Custom].orEmpty().forEach { profile ->
                RadioRow(
                    label = profile.name,
                    selected = settings.activeProfileId == profile.id,
                    onClick = { settingsViewModel.setActiveProfile(profile.id) },
                    description = profile.description,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Built-in profiles")
            grouped[ProfileKind.BuiltIn].orEmpty().forEach { profile ->
                RadioRow(
                    label = profile.name,
                    selected = settings.activeProfileId == profile.id,
                    onClick = { settingsViewModel.setActiveProfile(profile.id) },
                    description = profile.description,
                )
            }
        }
    }
}
