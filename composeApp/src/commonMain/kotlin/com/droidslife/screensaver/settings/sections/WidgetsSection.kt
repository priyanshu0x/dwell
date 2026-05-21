package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun WidgetsSection(
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
) {
    val descriptors by widgetRegistry.descriptors.collectAsState()
    val settings = settingsViewModel.settings
    val enabledIds = settingsViewModel.effectiveEnabledWidgetIds()

    // Track which widgets have their inline config panel expanded.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    SectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Installed widgets")
            BodyText(
                "Toggle to show in the dashboard. Per-widget gear opens its config.",
                dim = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (descriptors.isEmpty()) {
                BodyText("No widgets installed.", dim = true)
            }
            descriptors.sortedBy { it.displayName }.forEach { descriptor ->
                val id = descriptor.id
                val isEnabled = id in enabledIds
                val hasConfig = descriptor.factory.configSchema.isNotEmpty()
                val isExpanded = expanded[id] == true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DwellColors.Surface1)
                        .border(1.dp, DwellColors.Stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Drag handle. TODO(phase 13): wire up real reorder gesture
                        // (kotlinx-compose-foundation lazy reorder or similar).
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Reorder",
                            tint = DwellColors.TextFaint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = descriptor.displayName,
                                color = DwellColors.TextHigh,
                                fontFamily = DwellFonts.interTight(),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = id,
                                color = DwellColors.TextLow,
                                fontFamily = DwellFonts.jetBrainsMono(),
                                fontSize = 10.sp,
                            )
                            if (descriptor.factory.description.isNotBlank()) {
                                Text(
                                    text = descriptor.factory.description,
                                    color = DwellColors.TextMid,
                                    fontFamily = DwellFonts.interTight(),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        if (hasConfig) {
                            IconButton(
                                onClick = { expanded[id] = !isExpanded },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Configure ${descriptor.displayName}",
                                    tint = if (isExpanded) DwellColors.StatusAccent else DwellColors.TextMid,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { settingsViewModel.setWidgetEnabled(id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DwellColors.StatusAccent,
                                checkedTrackColor = DwellColors.StatusAccent.copy(alpha = 0.35f),
                            ),
                        )
                    }

                    if (isExpanded && hasConfig) {
                        Spacer(Modifier.height(8.dp))
                        WidgetConfigPanel(
                            schema = descriptor.factory.configSchema,
                            config = settings.widgetConfigs[id] ?: JsonObject(emptyMap()),
                            savedSecretIds = settingsViewModel.savedSecretIds,
                            onConfigChange = { newConfig ->
                                settingsViewModel.updateWidgetConfig(id, newConfig)
                            },
                            onSecretChange = { key, value ->
                                settingsViewModel.updateWidgetSecret(id, key, value)
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Console layout")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    label = "Edit Console layout",
                    accent = true,
                    onClick = {
                        settingsViewModel.setMode(Mode.Console)
                        settingsViewModel.updateConsoleEditMode(true)
                    },
                )
                PillButton(
                    label = "Reset Console layout",
                    onClick = { settingsViewModel.resetWidgetLayouts() },
                )
            }
        }
    }
}

/**
 * Minimal config renderer for the subset of field types used by built-ins.
 * Mirrors the behavior of `SettingsDialog.ConfigFieldRenderer` but uses
 * Dwell-styled primitives. Field types not handled fall back to a "—" notice
 * (and a TODO since they require a richer custom UI).
 */
@Composable
private fun WidgetConfigPanel(
    schema: List<ConfigField>,
    config: JsonObject,
    savedSecretIds: Set<String>,
    onConfigChange: (JsonObject) -> Unit,
    onSecretChange: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        schema.forEach { field ->
            ConfigFieldRow(
                field = field,
                config = config,
                savedSecretIds = savedSecretIds,
                onUpdate = { newValue ->
                    onConfigChange(JsonObject(config + (field.key to newValue)))
                },
                onSecretChange = onSecretChange,
            )
        }
    }
}

@Composable
private fun ConfigFieldRow(
    field: ConfigField,
    config: JsonObject,
    savedSecretIds: Set<String>,
    onUpdate: (JsonPrimitive) -> Unit,
    onSecretChange: (String, String) -> Unit,
) {
    when (field) {
        is ConfigField.Bool -> {
            val checked = config[field.key]?.jsonPrimitive?.booleanOrNull ?: field.default
            ToggleRow(
                label = field.label,
                checked = checked,
                onCheckedChange = { onUpdate(JsonPrimitive(it)) },
                description = field.help,
            )
        }
        is ConfigField.Text -> {
            val value = config[field.key]?.jsonPrimitive?.content ?: field.default
            DwellOutlinedTextField(
                label = field.label,
                value = value,
                onValueChange = { onUpdate(JsonPrimitive(it)) },
                placeholder = field.placeholder,
                helper = field.help,
            )
        }
        is ConfigField.Secret -> {
            var value by remember(field.key) { mutableStateOf("") }
            val saved = config[field.key]?.jsonPrimitive?.content in savedSecretIds
            DwellOutlinedTextField(
                label = field.label,
                value = value,
                onValueChange = {
                    value = it
                    onSecretChange(field.key, it)
                },
                password = true,
                helper = when {
                    saved && value.isBlank() -> "Saved. Enter a new value to replace it."
                    saved -> "New value will replace the saved key."
                    else -> field.help
                },
            )
        }
        is ConfigField.IntField -> {
            val current = config[field.key]?.jsonPrimitive?.intOrNull ?: field.default
            DwellOutlinedTextField(
                label = field.label,
                value = current.toString(),
                onValueChange = { input ->
                    val parsed = input.toIntOrNull() ?: field.default
                    val clamped = parsed.coerceIn(field.min ?: Int.MIN_VALUE, field.max ?: Int.MAX_VALUE)
                    onUpdate(JsonPrimitive(clamped))
                },
                helper = field.help,
                numeric = true,
            )
        }
        else -> {
            // ConfigField.Enum / Duration / DurationChoice / Currency / StringList / DesignPicker
            // are used by existing widgets but rendered with a richer UI in the legacy
            // dialog. TODO(phase 13 follow-up): port their renderers into the section
            // primitives so this panel can fully replace the legacy dialog.
            BodyText(
                "${field.label}: editor not yet available in the new sheet",
                dim = true,
            )
        }
    }
}

@Composable
private fun DwellOutlinedTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    helper: String? = null,
    password: Boolean = false,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontSize = 12.sp,
            )
        },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    color = DwellColors.TextFaint,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 13.sp,
                )
            }
        },
        supportingText = helper?.let {
            {
                Text(
                    text = it,
                    color = DwellColors.TextLow,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                )
            }
        },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DwellColors.StatusAccent,
            unfocusedBorderColor = DwellColors.Stroke,
            focusedTextColor = DwellColors.TextHigh,
            unfocusedTextColor = DwellColors.TextHigh,
            cursorColor = DwellColors.StatusAccent,
        ),
    )
}
