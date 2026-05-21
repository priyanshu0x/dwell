package com.droidslife.screensaver.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.droidslife.screensaver.DigitalClock
import com.droidslife.screensaver.DigitalClock10
import com.droidslife.screensaver.DigitalClock11
import com.droidslife.screensaver.DigitalClock3
import com.droidslife.screensaver.DigitalClock4
import com.droidslife.screensaver.DigitalClock5
import com.droidslife.screensaver.DigitalClock6
import com.droidslife.screensaver.DigitalClock7
import com.droidslife.screensaver.DigitalClock8
import com.droidslife.screensaver.DigitalClock9
import com.droidslife.screensaver.clockdigits.DigitalClockDigit2
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.host.WidgetDescriptor
import com.droidslife.screensaver.widget.host.WidgetSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dialog for application settings.
 *
 * @param onDismiss Callback when the dialog is dismissed.
 * @param settings Current settings.
 * @param onThemeToggle Callback when the theme is toggled.
 * @param onClockFormatToggle Callback when the clock format is toggled.
 * @param onAutoPlayToggle Callback when the auto play is toggled.
 * @param onShuffleToggle Callback when the shuffle is toggled.
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    settings: SettingsModel,
    widgetDescriptors: List<WidgetDescriptor> = emptyList(),
    onThemeToggle: () -> Unit,
    onClockFormatToggle: () -> Unit,
    onAutoPlayToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onWidgetEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
    onWidgetConfigChange: (String, JsonObject) -> Unit = { _, _ -> },
    onWidgetSecretChange: (String, String, String) -> Unit = { _, _, _ -> },
    onWidgetReload: () -> Unit = {},
    onIdleTimeoutChange: (Int) -> Unit = {},
    onTrayIconToggle: (Boolean) -> Unit = {},
    onStartWithSystemToggle: (Boolean) -> Unit = {},
    onBackendBaseUrlChange: (String) -> Unit = {},
    onBackendApiKeyChange: (String) -> Unit = {},
    onWeatherApiKeyChange: (String) -> Unit = {},
    backendApiKeySaved: Boolean = false,
    weatherApiKeySaved: Boolean = false,
    savedSecretIds: Set<String> = emptySet(),
) {
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Display") }

    if (showShortcutsDialog) {
        ShortcutsHelpDialog(onDismiss = { showShortcutsDialog = false })
    }
    // Real Dialog with proper dismiss handling (Esc + click-outside both
    // delegate to onDismissRequest via DialogProperties).
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 480.dp, max = 720.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Sticky header: title + close affordance.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close settings",
                        )
                    }
                }

                // Sticky tab row.
                SettingsTabRow(
                    tabs = listOf("Display", "Widgets", "Activation", "Backend", "About"),
                    selected = selectedTab,
                    onSelected = { selectedTab = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                HorizontalDivider()

                // Scrolling content with visible scrollbar.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 20.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        when (selectedTab) {
                            "Display" -> DisplaySettings(
                                settings = settings,
                                onThemeToggle = onThemeToggle,
                                onClockFormatToggle = onClockFormatToggle,
                                onAutoPlayToggle = onAutoPlayToggle,
                                onShuffleToggle = onShuffleToggle,
                                onWeatherApiKeyChange = onWeatherApiKeyChange,
                                weatherApiKeySaved = weatherApiKeySaved,
                            )
                            "Widgets" -> WidgetSettings(
                                settings = settings,
                                widgetDescriptors = widgetDescriptors,
                                onWidgetEnabledChange = onWidgetEnabledChange,
                                onWidgetConfigChange = onWidgetConfigChange,
                                onWidgetSecretChange = onWidgetSecretChange,
                                onWidgetReload = onWidgetReload,
                                savedSecretIds = savedSecretIds,
                            )
                            "Activation" -> ActivationSettings(
                                settings = settings,
                                onIdleTimeoutChange = onIdleTimeoutChange,
                                onTrayIconToggle = onTrayIconToggle,
                                onStartWithSystemToggle = onStartWithSystemToggle,
                            )
                            "Backend" -> BackendSettings(
                                settings = settings,
                                onBackendBaseUrlChange = onBackendBaseUrlChange,
                                onBackendApiKeyChange = onBackendApiKeyChange,
                                backendApiKeySaved = backendApiKeySaved,
                            )
                            "About" -> AboutSettings()
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                    )
                }

                HorizontalDivider()

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Pill-tab row that distributes tabs evenly and prevents label wrapping by
 * using `maxLines = 1` and generous horizontal padding. Selected tab uses the
 * primary color; unselected tabs use surfaceVariant.
 */
@Composable
private fun SettingsTabRow(
    tabs: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val bg = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable { onSelected(tab) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DisplaySettings(
    settings: SettingsModel,
    onThemeToggle: () -> Unit,
    onClockFormatToggle: () -> Unit,
    onAutoPlayToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onWeatherApiKeyChange: (String) -> Unit,
    weatherApiKeySaved: Boolean,
) {
    var weatherApiKey by remember(settings.weatherApiKeySecretId) { mutableStateOf("") }

    SectionTitle("Theme")
    SettingSwitch("Dark Theme", settings.isDarkTheme, onThemeToggle)
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionTitle("Clock Format")
    SettingRadio("12-hour (AM/PM)", !settings.is24HourFormat) {
        if (settings.is24HourFormat) onClockFormatToggle()
    }
    SettingRadio("24-hour", settings.is24HourFormat) {
        if (!settings.is24HourFormat) onClockFormatToggle()
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionTitle("Weather")
    SettingValue("Current City", settings.currentCity ?: "Auto (Timezone)")
    OutlinedTextField(
        value = weatherApiKey,
        onValueChange = {
            weatherApiKey = it
            onWeatherApiKeyChange(it)
        },
        label = { Text("WeatherAPI Key") },
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            SecretSavedText(weatherApiKeySaved, weatherApiKey.isBlank())
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionTitle("Playback")
    SettingSwitch("Auto Play", settings.autoPlayEnabled, onAutoPlayToggle)
    SettingSwitch("Shuffle", settings.shuffleEnabled, onShuffleToggle)
}

@Composable
private fun WidgetSettings(
    settings: SettingsModel,
    widgetDescriptors: List<WidgetDescriptor>,
    onWidgetEnabledChange: (String, Boolean) -> Unit,
    onWidgetConfigChange: (String, JsonObject) -> Unit,
    onWidgetSecretChange: (String, String, String) -> Unit,
    onWidgetReload: () -> Unit,
    savedSecretIds: Set<String>,
) {
    val defaultEnabled = widgetDescriptors
        .filter { it.source is WidgetSource.BuiltIn }
        .map { it.id }
        .toSet()
    val enabledIds = settings.enabledWidgetIds.ifEmpty { defaultEnabled }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Widget Registry", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Button(onClick = onWidgetReload) {
            Text("Reload")
        }
    }

    if (widgetDescriptors.isEmpty()) {
        Text(
            "No widgets found",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }

    widgetDescriptors.sortedBy { it.displayName }.forEach { descriptor ->
        val enabled = descriptor.id in enabledIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(descriptor.displayName, fontWeight = FontWeight.Bold)
                if (descriptor.factory.description.isNotBlank()) {
                    Text(
                        descriptor.factory.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = { onWidgetEnabledChange(descriptor.id, it) })
        }

        if (enabled && descriptor.factory.configSchema.isNotEmpty()) {
            val currentConfig = settings.widgetConfigs[descriptor.id] ?: JsonObject(emptyMap())
            Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                descriptor.factory.configSchema.forEach { field ->
                    ConfigFieldRenderer(
                        field = field,
                        config = currentConfig,
                        savedSecretIds = savedSecretIds,
                        onConfigChange = { onWidgetConfigChange(descriptor.id, it) },
                        onSecretChange = { key, value -> onWidgetSecretChange(descriptor.id, key, value) },
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun ConfigFieldRenderer(
    field: ConfigField,
    config: JsonObject,
    savedSecretIds: Set<String>,
    onConfigChange: (JsonObject) -> Unit,
    onSecretChange: (String, String) -> Unit,
) {
    fun update(value: JsonElement) {
        onConfigChange(JsonObject(config + (field.key to value)))
    }

    when (field) {
        is ConfigField.Bool -> SettingSwitch(
            label = field.label,
            checked = config[field.key]?.jsonPrimitive?.booleanOrNull ?: field.default,
            onToggle = { update(JsonPrimitive(!(config[field.key]?.jsonPrimitive?.booleanOrNull ?: field.default))) },
        )
        is ConfigField.Enum -> {
            SectionTitle(field.label)
            val selected = config[field.key]?.jsonPrimitive?.content ?: field.default
            field.options.forEach { option ->
                SettingRadio(option.label, selected == option.value) {
                    update(JsonPrimitive(option.value))
                }
            }
        }
        is ConfigField.IntField -> {
            val value = config[field.key]?.jsonPrimitive?.intOrNull?.toString() ?: field.default.toString()
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    update(JsonPrimitive((input.toIntOrNull() ?: field.default).coerceToField(field)))
                },
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        is ConfigField.Duration -> TextConfigField(
            label = field.label,
            value = config[field.key]?.jsonPrimitive?.content ?: field.default,
            onValueChange = { update(it) },
        )
        is ConfigField.DurationChoice -> DurationChoiceField(field, config) { update(it) }
        is ConfigField.Currency -> CurrencyField(field, config) { update(it) }
        is ConfigField.StringList -> StringListField(field, config) { update(it) }
        is ConfigField.DesignPicker -> DesignPickerField(field, config) { update(it) }
        is ConfigField.Secret -> {
            var value by remember(field.key, config[field.key]) { mutableStateOf("") }
            val saved = config[field.key]?.jsonPrimitive?.content in savedSecretIds
            TextConfigField(
                label = field.label,
                value = value,
                onValueChange = {
                    value = it.content
                    onSecretChange(field.key, it.content)
                },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = secretSavedMessage(saved, value.isBlank()),
            )
        }
        is ConfigField.Text -> TextConfigField(
            label = field.label,
            value = config[field.key]?.jsonPrimitive?.content ?: field.default,
            onValueChange = { update(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationChoiceField(
    field: ConfigField.DurationChoice,
    config: JsonObject,
    onUpdate: (JsonPrimitive) -> Unit,
) {
    val selectedValue = config[field.key]?.jsonPrimitive?.content ?: field.default
    val selectedLabel = field.options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue
    var expanded by remember { mutableStateOf(false) }

    SectionTitle(field.label)
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                TextButton(onClick = { expanded = true }) { Text("Change") }
            },
            singleLine = true,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onUpdate(JsonPrimitive(option.value))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CurrencyField(
    field: ConfigField.Currency,
    config: JsonObject,
    onUpdate: (JsonPrimitive) -> Unit,
) {
    val selectedCode = config[field.key]?.jsonPrimitive?.content ?: field.default
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val allCurrencies = remember { availableCurrencies() }
    val popularCodes = remember(field.popular) { field.popular.toSet() }
    val popular = remember(allCurrencies, popularCodes) {
        field.popular.mapNotNull { code -> allCurrencies.firstOrNull { it.code == code } }
    }
    val rest = remember(allCurrencies, popularCodes) {
        allCurrencies.filter { it.code !in popularCodes }
    }
    val filteredPopular = popular.filter { it.matches(query) }
    val filteredRest = rest.filter { it.matches(query) }

    SectionTitle(field.label)
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        OutlinedTextField(
            value = selectedCode,
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                TextButton(onClick = { expanded = true }) { Text("Change") }
            },
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = ""
            },
            modifier = Modifier.heightIn(max = 360.dp).widthIn(min = 280.dp),
        ) {
            DropdownMenuItem(
                enabled = false,
                onClick = {},
                text = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
            if (filteredPopular.isNotEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    onClick = {},
                    text = {
                        Text(
                            "Popular",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                filteredPopular.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.displayLabel()) },
                        onClick = {
                            onUpdate(JsonPrimitive(entry.code))
                            expanded = false
                            query = ""
                        },
                    )
                }
                HorizontalDivider()
            }
            if (filteredRest.isEmpty() && filteredPopular.isEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    onClick = {},
                    text = { Text("No matches") },
                )
            }
            filteredRest.take(200).forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.displayLabel()) },
                    onClick = {
                        onUpdate(JsonPrimitive(entry.code))
                        expanded = false
                        query = ""
                    },
                )
            }
        }
    }
}

private fun CurrencyEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return code.lowercase().contains(q) || displayName.lowercase().contains(q)
}

private fun CurrencyEntry.displayLabel(): String {
    return if (displayName.isBlank() || displayName == code) code else "$code  -  $displayName"
}

@Composable
private fun StringListField(
    field: ConfigField.StringList,
    config: JsonObject,
    onUpdate: (JsonElement) -> Unit,
) {
    val stored = config[field.key]
    val current: List<String> = remember(stored) {
        when (stored) {
            is JsonArray -> stored.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                .filter { it.isNotBlank() }
            is JsonPrimitive -> stored.content
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> field.default
        }
    }
    var input by remember { mutableStateOf("") }

    fun commit(newList: List<String>) {
        onUpdate(JsonArray(newList.map { JsonPrimitive(it) }))
    }

    SectionTitle(field.label)
    if (current.isNotEmpty()) {
        // Flow-like row using wrap. We approximate Flow with a simple wrapping
        // Row by chunking when measuring isn't worth it for short label lists.
        ChipFlowRow(items = current, onRemove = { idx ->
            commit(current.toMutableList().also { it.removeAt(idx) })
        })
    }

    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("Add ${field.label.lowercase().trimEnd('s')}") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            val trimmed = input.trim()
            if (trimmed.isNotBlank() && trimmed !in current) {
                commit(current + trimmed)
            }
            input = ""
        }),
        trailingIcon = {
            TextButton(onClick = {
                val trimmed = input.trim()
                if (trimmed.isNotBlank() && trimmed !in current) {
                    commit(current + trimmed)
                }
                input = ""
            }) { Text("Add") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipFlowRow(items: List<String>, onRemove: (Int) -> Unit) {
    // Compose Multiplatform does not yet ship androidx.compose.foundation.layout.FlowRow
    // in all versions we depend on, so we render chips in chunks of 4 per row
    // as a robust fallback.
    items.chunked(4).forEach { chunk ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chunk.forEachIndexed { localIndex, item ->
                val absoluteIndex = items.indexOfFirst { it === item }.let { idx ->
                    if (idx >= 0) idx else items.indexOf(item)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(item) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemove(absoluteIndex) },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove $item",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

@Composable
private fun DesignPickerField(
    field: ConfigField.DesignPicker,
    config: JsonObject,
    onUpdate: (JsonPrimitive) -> Unit,
) {
    val storedString = config[field.key]?.jsonPrimitive?.content
    val storedInt = config[field.key]?.jsonPrimitive?.intOrNull
    val selected = storedInt ?: storedString?.toIntOrNull() ?: field.default

    SectionTitle(field.label)
    field.designIds.forEach { designId ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(JsonPrimitive(designId.toString())) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected == designId,
                onClick = { onUpdate(JsonPrimitive(designId.toString())) },
            )
            Text(
                text = "Design $designId",
                modifier = Modifier.padding(start = 8.dp).width(96.dp),
            )
            ClockDesignPreview(designId = designId)
        }
    }
}

/**
 * Renders a compact 2-digit preview of the given clock design at ~40dp tall.
 *
 * The underlying digit composables are fixed-size (~110dp wide x 130dp tall) so
 * we render them in a `requiredSize` overlay scaled down by a single factor,
 * and clip the result to the visible preview slot.
 */
@Composable
private fun ClockDesignPreview(designId: Int) {
    val targetHeight = 44.dp
    val sourceHeight = 130f
    val targetHeightPx = 44f
    val scale = targetHeightPx / sourceHeight
    // Two digits ~ 220dp wide + spacing -> visible ~ (220 * scale) dp
    val visibleWidth = 96.dp
    val sourceWidth = 240.dp

    Box(
        modifier = Modifier
            .size(visibleWidth, targetHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Transparent),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(sourceWidth, 140.dp)
                .scale(scale),
            contentAlignment = Alignment.CenterStart,
        ) {
            when (designId) {
                1 -> DigitalClock(1, 2)
                2 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DigitalClockDigit2(1)
                    DigitalClockDigit2(2)
                }
                3 -> DigitalClock3(1, 2)
                4 -> DigitalClock4(1, 2)
                5 -> DigitalClock5(1, 2)
                6 -> DigitalClock6(1, 2)
                7 -> DigitalClock7(1, 2)
                8 -> DigitalClock8(1, 2)
                9 -> DigitalClock9(1, 2)
                10 -> DigitalClock10(1, 2)
                11 -> DigitalClock11(1, 2)
                else -> Text(
                    "Design $designId",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivationSettings(
    settings: SettingsModel,
    onIdleTimeoutChange: (Int) -> Unit,
    onTrayIconToggle: (Boolean) -> Unit,
    onStartWithSystemToggle: (Boolean) -> Unit,
) {
    SectionTitle("Idle")
    OutlinedTextField(
        value = settings.idleTimeoutMinutes.toString(),
        onValueChange = { onIdleTimeoutChange(it.toIntOrNull() ?: settings.idleTimeoutMinutes) },
        label = { Text("Idle timeout minutes") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionTitle("Daemon")
    SettingSwitch("Tray Icon", settings.trayIconEnabled) { onTrayIconToggle(!settings.trayIconEnabled) }
    SettingSwitch("Start With System", settings.startWithSystem) { onStartWithSystemToggle(!settings.startWithSystem) }
}

@Composable
private fun BackendSettings(
    settings: SettingsModel,
    onBackendBaseUrlChange: (String) -> Unit,
    onBackendApiKeyChange: (String) -> Unit,
    backendApiKeySaved: Boolean,
) {
    var apiKey by remember(settings.backendApiKeySecretId) { mutableStateOf("") }

    SectionTitle("Sync")
    OutlinedTextField(
        value = settings.backendBaseUrl,
        onValueChange = onBackendBaseUrlChange,
        label = { Text("Base URL") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = {
            apiKey = it
            onBackendApiKeyChange(it)
        },
        label = { Text("API Key") },
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            SecretSavedText(backendApiKeySaved, apiKey.isBlank())
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
}

@Composable
private fun TextConfigField(
    label: String,
    value: String,
    onValueChange: (JsonPrimitive) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(JsonPrimitive(it)) },
        label = { Text(label) },
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun SecretSavedText(saved: Boolean, inputBlank: Boolean) {
    val message = secretSavedMessage(saved, inputBlank)
    if (message != null) {
        Text(message)
    }
}

private fun secretSavedMessage(saved: Boolean, inputBlank: Boolean): String? {
    return when {
        saved && inputBlank -> "Saved. Enter a new value to replace it."
        saved -> "New value will replace the saved key."
        else -> null
    }
}

private fun Int.coerceToField(field: ConfigField.IntField): Int {
    val min = field.min ?: Int.MIN_VALUE
    val max = field.max ?: Int.MAX_VALUE
    return coerceIn(min, max)
}

@Composable
private fun AboutSettings() {
    SectionTitle("Screen Saver App")
    Text("Desktop dashboard")
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SettingRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Dialog that displays all available keyboard shortcuts in the app.
 *
 * @param onDismiss Callback when the dialog is dismissed.
 * @param onExitApplication Callback to exit the application.
 */
@Composable
fun ShortcutsHelpDialog(
    onDismiss: () -> Unit,
    onExitApplication: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            // Exit button in top right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            ) {
                Button(
                    onClick = onExitApplication,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Exit Application",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "Exit App",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Keyboard Shortcuts",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // List of shortcuts
                Text(
                    text = "Available Shortcuts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Alt shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Alt",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Hide Screen Saver")
                }

                // Escape shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Esc",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Hide Screen Saver")
                }

                // Ctrl + C shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + C",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Open Settings")
                }

                // Ctrl + H shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + H",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Show Help")
                }

                // Ctrl + N shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + N",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Cycle Clock Design")
                }

                // Ctrl + P shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + P",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Toggle Auto-Change")
                }

                // Ctrl + R shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + R",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Toggle Shuffle Mode")
                }

                // Ctrl + T shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + T",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Toggle Theme (Light/Dark)")
                }

                // Ctrl + S shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Ctrl + S",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(text = "Show City Selection")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Theme settings
                Text(
                    text = "Theme Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Text(
                    text = "Toggle between light and dark theme using Ctrl + T or in the Settings dialog (Ctrl + C)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
