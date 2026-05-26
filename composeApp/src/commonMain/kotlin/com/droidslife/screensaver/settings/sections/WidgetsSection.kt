package com.droidslife.screensaver.settings.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.components.pausesShortcutsWhileFocused
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
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
            SectionHeader("Installed widgets")
            BodyText(
                "Drop JARs or YAML folders into ~/.screensaver/widgets/, then reload.",
                dim = true,
            )
            PillButton(
                label = "Reload widgets from disk",
                onClick = {
                    widgetRegistry.reload()
                    widgetRegistry.syncWithSettings(settingsViewModel.settings)
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp),
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

// Conditional fields: the WeatherAPI key only matters when WeatherAPI is the
// active source. Hiding it on wttr.in removes a row of dead chrome.
private fun shouldHideField(field: ConfigField, config: JsonObject): Boolean {
    if (field.key != "apiKey") return false
    val provider = config["provider"]?.jsonPrimitive?.content
    return provider != null && provider != "weatherapi"
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
        is ConfigField.Enum -> {
            val current = config[field.key]?.jsonPrimitive?.content ?: field.default
            ChoiceRow(
                label = field.label,
                options = field.options.map { it.value to it.label },
                selectedValue = current,
                onSelect = { onUpdate(JsonPrimitive(it)) },
                helper = field.help,
            )
        }
        is ConfigField.Duration -> {
            val value = config[field.key]?.jsonPrimitive?.content ?: field.default
            DwellOutlinedTextField(
                label = field.label,
                value = value,
                onValueChange = { onUpdate(JsonPrimitive(it)) },
                helper = field.help ?: "Format: 30s · 2m · 1h",
            )
        }
        is ConfigField.DurationChoice -> {
            val current = config[field.key]?.jsonPrimitive?.content ?: field.default
            ChoiceRow(
                label = field.label,
                options = field.options.map { it.value to it.label },
                selectedValue = current,
                onSelect = { onUpdate(JsonPrimitive(it)) },
                helper = field.help,
            )
        }
        is ConfigField.Currency -> {
            val current = config[field.key]?.jsonPrimitive?.content ?: field.default
            // Show "popular" pinned currencies inline as chips. Anything else can
            // still be typed in as a raw 3-letter code (the dropdown of all 150
            // ISO 4217 currencies is platform-specific and deferred).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = field.label,
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
                val currencyAccent = LocalConsoleAccent.current.primary
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    field.popular.forEach { code ->
                        val isSelected = code == current
                        val bg = if (isSelected) currencyAccent.copy(alpha = 0.14f) else DwellColors.Surface1
                        val fg = if (isSelected) DwellColors.TextHigh else DwellColors.TextMid
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(1.dp, DwellColors.Stroke, RoundedCornerShape(8.dp))
                                .clickable { onUpdate(JsonPrimitive(code)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = code,
                                color = fg,
                                fontFamily = DwellFonts.jetBrainsMono(),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                DwellOutlinedTextField(
                    label = "ISO 4217 code",
                    value = current,
                    onValueChange = { onUpdate(JsonPrimitive(it.uppercase().take(3))) },
                    helper = field.help ?: "e.g. USD, EUR, JPY",
                )
            }
        }
        is ConfigField.StringList -> {
            // Comma-separated string editor — chip editor is deferred to widget-api
            // (needs FlowRow). On read, the legacy comma string is split into a list.
            val raw = config[field.key]?.jsonPrimitive?.content
                ?: field.default.joinToString(",")
            DwellOutlinedTextField(
                label = field.label,
                value = raw,
                onValueChange = { onUpdate(JsonPrimitive(it)) },
                helper = field.help ?: "Comma-separated list",
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

@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    helper: String? = null,
) {
    val accent = LocalConsoleAccent.current.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        // iOS-style segmented control: track is a single Surface1 pill; the
        // active segment is a smaller rounded pill that "floats" inside it.
        // No internal dividers — the contrast between the floating pill and
        // the track does the visual work.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DwellColors.Surface1)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (value, displayLabel) ->
                val isSelected = value == selectedValue
                val bg by animateColorAsState(
                    targetValue = if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent,
                    animationSpec = tween(120),
                    label = "seg-bg",
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) DwellColors.TextHigh else DwellColors.TextMid,
                    animationSpec = tween(120),
                    label = "seg-fg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayLabel,
                        color = fg,
                        fontFamily = DwellFonts.interTight(),
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (!helper.isNullOrBlank()) HelperText(helper)
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
    val accent = LocalConsoleAccent.current.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Border lifts to the accent on focus and recedes to a hair-thin stroke at
    // rest — the field shouldn't compete with the label visually.
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) accent else DwellColors.Stroke.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 140),
        label = "field-border",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DwellColors.Surface1)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 14.sp,
                    color = DwellColors.TextHigh,
                ),
                cursorBrush = SolidColor(accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (numeric) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp)
                    .pausesShortcutsWhileFocused(),
                decorationBox = { inner ->
                    if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            color = DwellColors.TextFaint,
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                },
            )
        }
        if (!helper.isNullOrBlank()) {
            HelperText(helper)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = DwellColors.TextMid,
        fontFamily = DwellFonts.interTight(),
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    )
}

@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        color = DwellColors.TextLow,
        fontFamily = DwellFonts.interTight(),
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}
