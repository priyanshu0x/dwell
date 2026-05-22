package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.todos.providers.LocalTodosProvider
import com.droidslife.screensaver.todos.providers.Todo
import com.droidslife.screensaver.todos.providers.TodoistProvider
import com.droidslife.screensaver.todos.providers.TodosProvider
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class TodosWidgetFactory(
    private val backendGateway: BackendGateway,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.todos"
    override val displayName: String = "Todos"
    override val description: String = "Quick task capture with pluggable providers (local / Todoist)"
    override val category: WidgetCategory = WidgetCategory.PRODUCTIVITY
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 5, defaultRows = 2,
        maxCols = 8, maxRows = 4,
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Enum(
            key = "provider",
            label = "Source",
            options = listOf(
                ConfigField.EnumOption(LocalTodosProvider.ID, "Local (this machine)"),
                ConfigField.EnumOption(TodoistProvider.ID, "Todoist"),
            ),
            default = LocalTodosProvider.ID,
            help = "Local stores todos on this machine. Todoist syncs with your Todoist account.",
        ),
        ConfigField.Secret(
            key = "apiToken",
            label = "Todoist API token",
            help = "Get yours from Todoist → Settings → Integrations → Developer. Only needed for the Todoist source.",
        ),
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

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget =
        TodosWidget(config, scope, backendGateway)
}

/**
 * Status surfaced under the body row. Mirrors the "subtle hint" pattern other
 * Console tiles use; we never want to take the whole tile away on an auth
 * failure — the user can still type a new task into local.
 */
private sealed interface TodosStatus {
    data object Ok : TodosStatus
    data class Warn(val message: String) : TodosStatus
}

private class TodosWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
    private val backendGateway: BackendGateway,
) : Widget {
    override val preferredSpan: Int = 1

    /**
     * The active provider is chosen once per widget instance based on the
     * persisted config. WidgetRegistry.updateConfig disables + recreates the
     * widget when the config JSON changes, so a user flipping "Source" in
     * Settings yields a fresh widget with the new provider — we never need
     * to swap providers mid-lifecycle here.
     *
     * If the user picks Todoist but hasn't pasted a token yet, we silently
     * fall back to the local provider and surface a warning in the subtitle
     * so the tile keeps working instead of going dark.
     */
    private val statusFlow = MutableStateFlow<TodosStatus>(TodosStatus.Ok)
    private val provider: TodosProvider = run {
        val pick = config.string("provider").ifBlank { LocalTodosProvider.ID }
        when (pick) {
            TodoistProvider.ID -> {
                val token = config.secret("apiToken")?.trim().orEmpty()
                if (token.isBlank()) {
                    statusFlow.value = TodosStatus.Warn("Add a Todoist API token to enable sync")
                    LocalTodosProvider(scope.storage, backendGateway, scope.coroutineScope, scope.log)
                } else {
                    TodoistProvider(
                        http = scope.httpClient,
                        apiToken = token,
                        scope = scope.coroutineScope,
                        log = scope.log,
                    )
                }
            }
            else -> LocalTodosProvider(scope.storage, backendGateway, scope.coroutineScope, scope.log)
        }
    }

    private var input by mutableStateOf("")
    private var inputVisible by mutableStateOf(false)

    /**
     * The summary is computed off the last snapshot the Content composable
     * collected. Keeping a separate cache means [summary] (called by the
     * host outside composition for chip/minimal renders) stays cheap.
     */
    private val summaryCache = MutableStateFlow<List<Todo>>(emptyList())

    override fun summary(): WidgetSummary {
        val snapshot = summaryCache.value
        val open = snapshot.count { !it.done }
        return WidgetSummary(
            primaryValue = open.toString(),
            primaryLabel = "Todos",
            subtitle = when {
                open == 0 && snapshot.isEmpty() -> "Nothing yet"
                open == 0 -> "All clear"
                else -> "$open open"
            },
        )
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val todos by provider.watch().collectAsState(initial = remember { summaryCache.value })
        val status by statusFlow.collectAsState()
        // Keep the off-composition summary in sync with the latest poll tick.
        LaunchedEffect(todos) { summaryCache.value = todos }

        val sorted = sortedTodos(todos)
        val openTodos = sorted.filterNot { it.done }
        val displayed = openTodos.take(3)
        val openCount = openTodos.size

        // Tile layout: label top-start, big summary centered, subtitle / list
        // bottom-start. Mirrors the Console mockup at
        // `.superpowers/brainstorm/1398003-1779392791/content/mode-mockups.html`.
        Box(modifier = modifier.fillMaxSize()) {
            // Top-start: label + tiny add toggle on the right.
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (openCount > 0) "TODOS · $openCount OPEN" else "TODOS",
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 2.25.sp,
                    color = DwellColors.TextLow,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { inputVisible = !inputVisible },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = if (inputVisible) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (inputVisible) "Hide add form" else "Add task",
                        tint = DwellColors.TextLow,
                    )
                }
            }

            // Center: big numeric summary OR "ALL CLEAR" callout. When the add
            // form is open we hide the centerpiece so the input has room.
            if (!inputVisible) {
                val centerLine = if (openCount == 0) "ALL CLEAR" else "$openCount open"
                Text(
                    text = centerLine,
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = if (openCount == 0) 22.sp else 32.sp,
                    color = DwellColors.TextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
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
                    Button(onClick = ::onAdd, enabled = input.isNotBlank()) {
                        Text("Add")
                    }
                }
            }

            // Bottom-start: next couple of open task names + status line.
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (displayed.isNotEmpty()) {
                    displayed.forEach { todo ->
                        Text(
                            text = todo.text,
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 11.sp,
                            color = DwellColors.TextMid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        )
                    }
                }
                val statusLine = when (val s = status) {
                    is TodosStatus.Warn -> s.message
                    TodosStatus.Ok -> when {
                        openCount > displayed.size ->
                            "+ ${openCount - displayed.size} more"
                        else -> ""
                    }
                }
                if (statusLine.isNotBlank()) {
                    Text(
                        text = statusLine,
                        fontFamily = DwellFonts.interTight(),
                        fontSize = 10.sp,
                        color = if (status is TodosStatus.Warn) DwellColors.StatusError else DwellColors.TextLow,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }

    private fun sortedTodos(todos: List<Todo>): List<Todo> {
        val base = if (config.bool("hideDone")) todos.filterNot { it.done } else todos
        return when (config.enum("sort", "newest")) {
            "oldest" -> base.sortedBy { it.createdAt }
            "alphabetical" -> base.sortedBy { it.text.lowercase() }
            else -> base.sortedByDescending { it.createdAt }
        }
    }

    private fun onAdd() {
        val text = input.trim()
        if (text.isBlank()) return
        input = ""
        scope.coroutineScope.launch {
            val result = provider.add(text)
            result.onFailure { err ->
                scope.log.warn("Todos add failed", err)
                statusFlow.value = TodosStatus.Warn("Couldn't add task: ${err.message ?: "unknown error"}")
            }
        }
    }
}
