package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellTextField

@Composable
fun SyncSection(settingsViewModel: SettingsViewModel) {
    val settings = settingsViewModel.settings

    // Local-only echo state for the secret field (mirrors what the legacy
    // dialog does — we don't read the actual secret from storage).
    var apiKeyInput by remember(settings.backendApiKeySecretId) { mutableStateOf("") }
    val apiKeySaved = settingsViewModel.isSecretSaved(settings.backendApiKeySecretId)

    SectionContainer {
        // Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DwellColors.DialogControlSurface)
                .border(1.dp, DwellColors.Stroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Off by default — nothing leaves your machine unless you turn this on.",
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontSize = 12.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Backend")
            // TODO(phase 11+): SettingsModel does not yet hold a `backendEnabled`
            // boolean. When it lands, wire this toggle to it. For now the toggle
            // is a visual placeholder that can't actually disable the URL/key
            // fields below.
            ToggleRow(
                label = "Enable sync",
                checked = false,
                onCheckedChange = { /* no-op placeholder */ },
                description = "Disabled — pending SettingsModel field (see TODO)",
                enabled = false,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Connection")
            SyncTextField(
                label = "Backend URL",
                value = settings.backendBaseUrl,
                onValueChange = settingsViewModel::setBackendBaseUrl,
                placeholder = "https://your.dwell.host",
            )
            SyncTextField(
                label = "API key",
                value = apiKeyInput,
                onValueChange = { input ->
                    apiKeyInput = input
                    settingsViewModel.updateBackendApiKey(input)
                },
                password = true,
                helper = when {
                    apiKeySaved && apiKeyInput.isBlank() -> "Saved. Enter a new value to replace it."
                    apiKeySaved -> "New value will replace the saved key."
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun SyncTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    helper: String? = null,
    password: Boolean = false,
) {
    DwellTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        helper = helper,
        password = password,
        accent = DwellColors.StatusAccent,
        modifier = Modifier.fillMaxWidth(),
    )
}
