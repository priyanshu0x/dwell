package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellChoiceGroup
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellTextField
import com.droidslife.screensaver.todos.providers.TodoistProvider
import com.droidslife.screensaver.weather.providers.WeatherApiProvider
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()

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
                        .background(DwellColors.DialogControlSurface)
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
                            if (descriptor.factory.description.isNotBlank()) {
                                Text(
                                    text = descriptor.factory.description,
                                    color = DwellColors.TextLow,
                                    fontFamily = DwellFonts.interTight(),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        if (hasConfig) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isExpanded) DwellColors.StatusAccent.copy(alpha = 0.12f) else DwellColors.DialogControlSurface)
                                    .border(1.dp, DwellColors.Stroke, RoundedCornerShape(8.dp))
                                    .clickable { expanded[id] = !isExpanded },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Configure ${descriptor.displayName}",
                                    tint = if (isExpanded) DwellColors.StatusAccent else DwellColors.TextMid,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        DwellSwitch(
                            checked = isEnabled,
                            onCheckedChange = { settingsViewModel.setWidgetEnabled(id, it) },
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
            SectionHeader("Widget sources")
            BodyText(
                "Drop JARs or YAML folders into ~/.screensaver/widgets/, then reload.",
                dim = true,
            )
            PillButton(
                label = "Reload widgets from disk",
                onClick = {
                    widgetRegistry.reload()
                    coroutineScope.launch {
                        widgetRegistry.syncWithSettings(settingsViewModel.settings)
                    }
                },
            )
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
 *
 * `internal` so the per-widget config dialog (opened from a widget's gear
 * icon) renders the same fields as the Settings → Widgets row.
 */
@Composable
internal fun WidgetConfigPanel(
    schema: List<ConfigField>,
    config: JsonObject,
    savedSecretIds: Set<String>,
    onConfigChange: (JsonObject) -> Unit,
    onSecretChange: (String, String) -> Unit,
    // Indents the fields under the widget row in the Settings → Widgets list.
    // The standalone gear dialog already pads its body, so it passes 0.dp.
    startIndent: Dp = 28.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        schema.forEach { field ->
            if (shouldHideField(field, config)) return@forEach
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

// Conditional fields: hide credential inputs that don't apply to the active
// source so the panel doesn't show dead chrome (e.g. a Todoist token while the
// Todos widget is on Local).
private fun shouldHideField(field: ConfigField, config: JsonObject): Boolean {
    val provider = config["provider"]?.jsonPrimitive?.content
    return when (field.key) {
        // WeatherAPI keys only matter on the WeatherAPI.com source. Built-in
        // weather-capable widgets default to wttr.in, so an absent provider
        // should not expose a dead credential field.
        "apiKey" -> provider != WeatherApiProvider.ID
        // Todoist token only matters on the Todoist source. The default source
        // is Local, so hide it whenever the source isn't explicitly Todoist.
        "apiToken" -> provider != TodoistProvider.ID
        else -> false
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
            DwellTextField(
                label = field.label,
                value = value,
                onValueChange = { onUpdate(JsonPrimitive(it)) },
                placeholder = field.placeholder,
                helper = field.help,
                accent = DwellColors.StatusAccent,
            )
        }
        is ConfigField.Secret -> {
            var value by remember(field.key) { mutableStateOf("") }
            val saved = config[field.key]?.jsonPrimitive?.content in savedSecretIds
            DwellTextField(
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
                accent = DwellColors.StatusAccent,
            )
        }
        is ConfigField.IntField -> {
            val current = config[field.key]?.jsonPrimitive?.intOrNull ?: field.default
            DwellTextField(
                label = field.label,
                value = current.toString(),
                onValueChange = { input ->
                    val parsed = input.toIntOrNull() ?: field.default
                    val clamped = parsed.coerceIn(field.min ?: Int.MIN_VALUE, field.max ?: Int.MAX_VALUE)
                    onUpdate(JsonPrimitive(clamped))
                },
                helper = field.help,
                numeric = true,
                accent = DwellColors.StatusAccent,
            )
        }
        is ConfigField.Enum -> {
            val current = config[field.key]?.jsonPrimitive?.content ?: field.default
            DwellChoiceGroup(
                label = field.label,
                options = field.options.map { it.value to it.label },
                selectedValue = current,
                onSelect = { onUpdate(JsonPrimitive(it)) },
                helper = field.help,
                color = DwellColors.StatusAccent,
            )
        }
        is ConfigField.Duration -> {
            val value = config[field.key]?.jsonPrimitive?.content ?: field.default
            DwellTextField(
                label = field.label,
                value = value,
                onValueChange = { onUpdate(JsonPrimitive(it)) },
                helper = field.help ?: "Format: 30s · 2m · 1h",
                accent = DwellColors.StatusAccent,
            )
        }
        is ConfigField.DurationChoice -> {
            val current = config[field.key]?.jsonPrimitive?.content ?: field.default
            DwellChoiceGroup(
                label = field.label,
                options = field.options.map { it.value to it.label },
                selectedValue = current,
                onSelect = { onUpdate(JsonPrimitive(it)) },
                helper = field.help,
                color = DwellColors.StatusAccent,
            )
        }
        is ConfigField.Currency -> {
            val current = config[field.key]?.jsonPrimitive?.content ?: field.default
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DwellChoiceGroup(
                    label = field.label,
                    options = field.popular.map { it to it },
                    selectedValue = current,
                    onSelect = { onUpdate(JsonPrimitive(it)) },
                    color = DwellColors.StatusAccent,
                    helper = field.help,
                )
                DwellTextField(
                    label = "ISO 4217 code",
                    value = current,
                    onValueChange = { onUpdate(JsonPrimitive(it.uppercase().take(3))) },
                    helper = "e.g. USD, EUR, JPY",
                    accent = DwellColors.StatusAccent,
                )
            }
        }
        is ConfigField.StringList -> {
            // Comma-separated string editor — chip editor is deferred to widget-api
            // (needs FlowRow). On read, the legacy comma string is split into a list.
            val raw = config[field.key]?.jsonPrimitive?.content
                ?: field.default.joinToString(",")
            DwellTextField(
                label = field.label,
                value = raw,
                onValueChange = { onUpdate(JsonPrimitive(it)) },
                helper = field.help ?: "Comma-separated list",
                accent = DwellColors.StatusAccent,
            )
        }
        else -> {
            // ConfigField.DesignPicker is unused after the 11-design retirement.
            BodyText(
                "${field.label}: editor not yet available in the new sheet",
                dim = true,
            )
        }
    }
}
