package com.droidslife.screensaver.widget.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.nio.file.Path

class DeclarativeWidgetFactory(
    private val manifest: WidgetManifest,
    private val folder: Path,
) : WidgetFactory {
    override val id: String = manifest.id
    override val displayName: String = manifest.title
    override val description: String = manifest.description
    override val category: WidgetCategory = WidgetCategory.entries
        .firstOrNull { it.name.equals(manifest.category, ignoreCase = true) }
        ?: WidgetCategory.OTHER
    override val apiVersion: Int = manifest.apiVersion
    override val configSchema: List<ConfigField> = manifest.config.mapNotNull { it.toConfigField() }

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return DeclarativeWidget(manifest, folder, config, scope)
    }
}

private class DeclarativeWidget(
    private val manifest: WidgetManifest,
    private val folder: Path,
    private val config: WidgetConfig,
    private val scope: WidgetScope,
) : Widget {
    override val preferredSpan: Int = manifest.preferredSpan.coerceIn(1, 3)

    private val json = Json { ignoreUnknownKeys = true }
    private val binder = JsonPathBinder()
    private var pollingJob: Job? = null
    private var state by mutableStateOf<DeclarativeState>(DeclarativeState.Loading)

    override fun onResume() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.coroutineScope.launch {
            val intervalMs = config.durationMillis("refreshOverride", durationMillis(manifest.refresh, 60_000L))
            val dataSource = manifest.source.toDataSource(folder, config, scope)
            while (isActive) {
                load(dataSource)
                delay(intervalMs)
            }
        }
    }

    override fun onSuspend() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onDispose() {
        onSuspend()
    }

    private suspend fun load(dataSource: DataSource) {
        state = runCatching {
            val root = json.parseToJsonElement(dataSource.fetch())
            DeclarativeState.Ready(bind(root))
        }.getOrElse { error ->
            DeclarativeState.Error(error.message ?: "Failed to load widget data")
        }
    }

    private fun bind(root: JsonElement): BoundWidgetData {
        return when (manifest.template.lowercase()) {
            "text" -> BoundWidgetData.Text(value("value", root))
            "kv" -> BoundWidgetData.Rows(rows(root, "key", "value"))
            "list" -> BoundWidgetData.Rows(rows(root, "label", "value", "trend"))
            "grid" -> BoundWidgetData.Rows(rows(root, "label", "value"))
            "chart" -> BoundWidgetData.Chart(series(root))
            "image" -> BoundWidgetData.Text(value("url", root))
            else -> BoundWidgetData.Text("Unsupported template: ${manifest.template}")
        }
    }

    private fun value(bindingKey: String, root: JsonElement): String {
        val path = manifest.bindings[bindingKey] ?: return ""
        return binder.selectOne(root, path).asText()
    }

    private fun rows(root: JsonElement, labelKey: String, valueKey: String, trendKey: String? = null): List<BoundRow> {
        val itemsPath = manifest.bindings["items"]
        val roots = if (itemsPath != null) binder.select(root, itemsPath) else listOf(root)
        return roots.map { item ->
            BoundRow(
                label = boundItemValue(item, labelKey),
                value = boundItemValue(item, valueKey),
                trend = trendKey?.let { boundItemValue(item, it) }.orEmpty(),
            )
        }
    }

    private fun boundItemValue(item: JsonElement, key: String): String {
        val path = manifest.bindings["item.$key"] ?: manifest.bindings[key] ?: return ""
        return binder.selectOne(item, path).asText()
    }

    private fun series(root: JsonElement): List<Double> {
        val seriesPath = manifest.bindings["series"] ?: return emptyList()
        val yPath = manifest.bindings["series.y"] ?: "$"
        return binder.select(root, seriesPath)
            .mapNotNull { binder.selectOne(it, yPath)?.asDouble() }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        when (val current = state) {
            DeclarativeState.Loading -> Text("Loading...", modifier = modifier)
            is DeclarativeState.Error -> Text(
                text = current.message,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier,
            )
            is DeclarativeState.Ready -> DeclarativeTemplate(current.data, modifier)
        }
    }
}

@Composable
private fun DeclarativeTemplate(data: BoundWidgetData, modifier: Modifier) {
    when (data) {
        is BoundWidgetData.Text -> Text(
            text = data.value,
            style = MaterialTheme.typography.headlineSmall,
            modifier = modifier,
        )
        is BoundWidgetData.Rows -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    Text(listOf(row.value, row.trend).filter { it.isNotBlank() }.joinToString(" "))
                }
            }
        }
        is BoundWidgetData.Chart -> Sparkline(data.values, modifier)
    }
}

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.height(96.dp).padding(vertical = 8.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it != 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        values.zipWithNext().forEachIndexed { index, (a, b) ->
            val start = Offset(index * stepX, size.height - (((a - min) / range).toFloat() * size.height))
            val end = Offset((index + 1) * stepX, size.height - (((b - min) / range).toFloat() * size.height))
            drawLine(color, start, end, strokeWidth = 4f)
        }
    }
}

private sealed interface DeclarativeState {
    data object Loading : DeclarativeState
    data class Ready(val data: BoundWidgetData) : DeclarativeState
    data class Error(val message: String) : DeclarativeState
}

private sealed interface BoundWidgetData {
    data class Text(val value: String) : BoundWidgetData
    data class Rows(val rows: List<BoundRow>) : BoundWidgetData
    data class Chart(val values: List<Double>) : BoundWidgetData
}

private data class BoundRow(
    val label: String,
    val value: String,
    val trend: String = "",
)

private fun JsonElement?.asText(): String {
    val primitive = this as? JsonPrimitive ?: return ""
    return primitive.content
}

private fun JsonElement.asDouble(): Double? {
    return (this as? JsonPrimitive)?.doubleOrNull
}

private fun ConfigFieldManifest.toConfigField(): ConfigField? {
    return when (type.lowercase()) {
        "text" -> ConfigField.Text(key, label, default = default, required = required, help = help)
        "secret" -> ConfigField.Secret(key, label, required = required, help = help)
        "int" -> ConfigField.IntField(key, label, default = default.toIntOrNull() ?: 0, required = required, help = help)
        "bool" -> ConfigField.Bool(key, label, default = default.toBooleanStrictOrNull() ?: false, required = required, help = help)
        "duration" -> ConfigField.Duration(key, label, default = default.ifBlank { "30s" }, required = required, help = help)
        "enum" -> ConfigField.Enum(
            key = key,
            label = label,
            options = options.map { ConfigField.EnumOption(it.value, it.label) },
            default = default,
            required = required,
            help = help,
        )
        else -> null
    }
}
