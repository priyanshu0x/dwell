package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class TodosWidgetFactory : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.todos"
    override val displayName: String = "Todos"
    override val description: String = "Quick task capture with persistent local storage"
    override val category: WidgetCategory = WidgetCategory.PRODUCTIVITY
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Bool("hideDone", "Hide completed"),
        ConfigField.Enum(
            key = "sort",
            label = "Sort by",
            options = listOf(
                ConfigField.EnumOption("newest", "Newest"),
                ConfigField.EnumOption("oldest", "Oldest"),
                ConfigField.EnumOption("alphabetical", "Alphabetical"),
            ),
            default = "newest",
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = TodosWidget(config, scope)
}

private class TodosWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
) : Widget {
    override val preferredSpan: Int = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Todo.serializer())
    private var todos by mutableStateOf<List<Todo>>(emptyList())
    private var input by mutableStateOf("")

    override fun onResume() {
        scope.coroutineScope.launch {
            todos = scope.storage.read("todos.json", String::class.java)
                ?.let { json.decodeFromString(serializer, it) }
                ?: emptyList()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val visibleTodos = sortedTodos()
        Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Task") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = ::addTodo, enabled = input.isNotBlank()) {
                    Text("Add")
                }
            }

            if (visibleTodos.isEmpty()) {
                Text(
                    text = "No tasks",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                visibleTodos.forEach { todo ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = todo.done, onCheckedChange = { toggle(todo.id) })
                        Text(
                            text = todo.text,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        IconButton(onClick = { delete(todo.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Delete task")
                        }
                    }
                }
            }
        }
    }

    private fun sortedTodos(): List<Todo> {
        val base = if (config.bool("hideDone")) todos.filterNot { it.done } else todos
        return when (config.enum("sort", "newest")) {
            "oldest" -> base.sortedBy { it.createdAt }
            "alphabetical" -> base.sortedBy { it.text.lowercase() }
            else -> base.sortedByDescending { it.createdAt }
        }
    }

    private fun addTodo() {
        val text = input.trim()
        if (text.isBlank()) return
        val now = Clock.System.now().toEpochMilliseconds()
        input = ""
        todos = todos + Todo(
            id = "todo-$now-${todos.size}",
            text = text,
            done = false,
            createdAt = now,
            updatedAt = now,
        )
        persist()
    }

    private fun toggle(id: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        todos = todos.map { if (it.id == id) it.copy(done = !it.done, updatedAt = now) else it }
        persist()
    }

    private fun delete(id: String) {
        todos = todos.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val snapshot = json.encodeToString(serializer, todos)
        scope.coroutineScope.launch {
            scope.storage.write("todos.json", snapshot)
        }
    }
}

@Serializable
private data class Todo(
    val id: String,
    val text: String,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
