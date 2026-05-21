package com.droidslife.screensaver.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.host.WidgetDescriptor
import com.droidslife.screensaver.widget.host.WidgetSource
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
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Display", "Widgets", "Activation", "Backend", "About").forEach { tab ->
                        Button(
                            onClick = { selectedTab = tab },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(tab)
                        }
                    }
                }

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
    fun update(value: JsonPrimitive) {
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
                SettingRadio(option.label, selected == option.value) { update(JsonPrimitive(option.value)) }
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
        is ConfigField.Duration -> TextConfigField(field.label, config[field.key]?.jsonPrimitive?.content ?: field.default, ::update)
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
        is ConfigField.Text -> TextConfigField(field.label, config[field.key]?.jsonPrimitive?.content ?: field.default, ::update)
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
