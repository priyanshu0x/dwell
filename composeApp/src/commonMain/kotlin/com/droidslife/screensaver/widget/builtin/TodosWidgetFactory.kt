package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.network.BackendResult
import com.droidslife.screensaver.storage.SyncRepository
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock

class TodosWidgetFactory(
    private val backendGateway: BackendGateway,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.todos"
    override val displayName: String = "Todos"
    override val description: String = "Quick task capture with persistent local storage"
    override val category: WidgetCategory = WidgetCategory.PRODUCTIVITY
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 5, defaultRows = 2,
        maxCols = 8, maxRows = 4,
    )
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

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = TodosWidget(config, scope, backendGateway)
}

private class TodosWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
    backendGateway: BackendGateway,
) : Widget {
    override val preferredSpan: Int = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Todo.serializer())
    private val syncRepository = SyncRepository("todos", scope.storage, backendGateway)
    private var todos by mutableStateOf<List<Todo>>(emptyList())
    private var input by mutableStateOf("")
    private var unsyncedCount by mutableStateOf(0)
    private var inputVisible by mutableStateOf(false)

    override fun summary(): WidgetSummary {
        val open = todos.count { !it.done }
        return WidgetSummary(
            primaryValue = open.toString(),
            primaryLabel = "Todos",
            subtitle = if (open == 0) "Nothing to do" else "$open open",
        )
    }

    override fun onResume() {
        scope.coroutineScope.launch {
            todos = scope.storage.read("todos.json", String::class.java)
                ?.let { json.decodeFromString(serializer, it) }
                ?: emptyList()
            mergeRemoteTodos()
            pushPending()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val visibleTodos = sortedTodos()
        val openTodos = visibleTodos.filterNot { it.done }
        val displayed = openTodos.take(5)
        Column(modifier = modifier) {
            // Header row: title + add toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Todos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { inputVisible = !inputVisible }) {
                    Icon(
                        imageVector = if (inputVisible) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (inputVisible) "Hide add form" else "Add task",
                    )
                }
            }

            if (inputVisible) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            }

            if (displayed.isEmpty()) {
                Text(
                    text = "Nothing to do",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                displayed.forEach { todo ->
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
                        if (inputVisible) {
                            IconButton(onClick = { delete(todo.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Delete task")
                            }
                        }
                    }
                }
                if (openTodos.size > displayed.size) {
                    Text(
                        text = "+${openTodos.size - displayed.size} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (unsyncedCount > 0) {
                Text(
                    text = "$unsyncedCount unsynced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
        queueUpsert(todos.last())
    }

    private fun toggle(id: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        var changed: Todo? = null
        todos = todos.map {
            if (it.id == id) {
                it.copy(done = !it.done, updatedAt = now).also { updated -> changed = updated }
            } else {
                it
            }
        }
        persist()
        changed?.let(::queueUpsert)
    }

    private fun delete(id: String) {
        todos = todos.filterNot { it.id == id }
        persist()
        scope.coroutineScope.launch {
            syncRepository.enqueueDelete(id)
            pushPending()
        }
    }

    private fun persist() {
        val snapshot = json.encodeToString(serializer, todos)
        scope.coroutineScope.launch {
            scope.storage.write("todos.json", snapshot)
        }
    }

    private fun queueUpsert(todo: Todo) {
        scope.coroutineScope.launch {
            syncRepository.enqueueUpsert(todo.id, todo.toJsonObject(), todo.updatedAt)
            pushPending()
        }
    }

    private suspend fun mergeRemoteTodos() {
        when (val result = syncRepository.pullRemote()) {
            BackendResult.Disabled -> Unit
            is BackendResult.Failure -> {
                scope.log.warn("Todo sync pull failed", result.cause)
                unsyncedCount = syncRepository.pendingCount()
            }
            is BackendResult.Success -> {
                val remote = result.value.mapNotNull { runCatching { it.toTodo() }.getOrNull() }
                if (remote.isNotEmpty()) {
                    todos = (todos + remote)
                        .groupBy { it.id }
                        .map { (_, values) -> values.maxBy { it.updatedAt } }
                    persist()
                }
            }
        }
    }

    private suspend fun pushPending() {
        val result = syncRepository.pushPending()
        if (result.lastError != null) {
            scope.log.warn("Todo sync push failed: ${result.lastError}")
        }
        unsyncedCount = result.remaining
    }

    private fun Todo.toJsonObject(): JsonObject {
        return json.encodeToJsonElement(Todo.serializer(), this) as JsonObject
    }

    private fun JsonObject.toTodo(): Todo {
        return json.decodeFromJsonElement(Todo.serializer(), this)
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
