package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.PROFILE_CURRENT_ID
import com.droidslife.screensaver.settings.ProfileKind
import com.droidslife.screensaver.settings.ProfileModel
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellTextField

@Composable
fun ProfilesSection(settingsViewModel: SettingsViewModel) {
    val settings = settingsViewModel.settings
    val profiles = settingsViewModel.profiles
    val currentProfile = profiles.firstOrNull { it.id == PROFILE_CURRENT_ID }
    val activeProfile = profiles.firstOrNull { it.id == settings.activeProfileId } ?: currentProfile
    val builtInProfiles = profiles.filter { it.kind == ProfileKind.BuiltIn }
    val customProfiles = profiles.filter { it.kind == ProfileKind.Custom && it.id != PROFILE_CURRENT_ID }

    var createName by remember { mutableStateOf("Custom Profile") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember(activeProfile?.id, activeProfile?.name) {
        mutableStateOf(activeProfile?.name.orEmpty())
    }

    fun requireName(value: String, action: (String) -> Unit) {
        val name = value.trim()
        if (name.isBlank()) {
            feedback = "Enter a profile name."
        } else {
            action(name)
            feedback = null
        }
    }

    SectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Active profile")
            activeProfile?.let { profile ->
                BodyText(profile.name)
                BodyText(profile.description, dim = true)
            }
            feedback?.let { BodyText(it, dim = true) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Create")
            DwellTextField(
                label = "New profile name",
                value = createName,
                onValueChange = { createName = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    label = "Create from current",
                    accent = true,
                    onClick = {
                        requireName(createName) { name ->
                            settingsViewModel.createProfileFromCurrent(name)
                            createName = "Custom Profile"
                        }
                    },
                )
                PillButton(
                    label = "Duplicate active",
                    onClick = {
                        activeProfile?.let {
                            settingsViewModel.duplicateProfile(it.id)
                            feedback = null
                        }
                    },
                )
            }
        }

        activeProfile?.let { profile ->
            ActiveProfileManagement(
                profile = profile,
                renameDraft = renameDraft,
                onRenameDraftChange = { renameDraft = it },
                onRename = {
                    requireName(renameDraft) { name ->
                        settingsViewModel.renameCustomProfile(profile.id, name)
                    }
                },
                onDelete = {
                    settingsViewModel.deleteCustomProfile(profile.id)
                    feedback = null
                },
                onReset = {
                    settingsViewModel.resetBuiltInProfile(profile.id)
                    feedback = null
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Current setup")
            currentProfile?.let { profile ->
                ProfileRadioRow(
                    profile = profile,
                    selected = settings.activeProfileId == profile.id,
                    onClick = { settingsViewModel.setActiveProfile(profile.id) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Built-in profiles")
            builtInProfiles.forEach { profile ->
                ProfileRadioRow(
                    profile = profile,
                    selected = settings.activeProfileId == profile.id,
                    onClick = { settingsViewModel.setActiveProfile(profile.id) },
                )
            }
        }

        if (customProfiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Custom profiles")
                customProfiles.forEach { profile ->
                    ProfileRadioRow(
                        profile = profile,
                        selected = settings.activeProfileId == profile.id,
                        onClick = { settingsViewModel.setActiveProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveProfileManagement(
    profile: ProfileModel,
    renameDraft: String,
    onRenameDraftChange: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    when {
        profile.id == PROFILE_CURRENT_ID -> Unit
        profile.kind == ProfileKind.Custom -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Custom profile")
                DwellTextField(
                    label = "Profile name",
                    value = renameDraft,
                    onValueChange = onRenameDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(label = "Rename", accent = true, onClick = onRename)
                    PillButton(label = "Delete", onClick = onDelete)
                }
            }
        }
        profile.kind == ProfileKind.BuiltIn -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Built-in profile")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(label = "Reset defaults", onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun ProfileRadioRow(
    profile: ProfileModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RadioRow(
        label = profile.name,
        selected = selected,
        onClick = onClick,
        description = profile.description,
    )
}
