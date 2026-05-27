package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.todos.EisenhowerMatrix
import com.droidslife.screensaver.todos.Quadrant
import com.droidslife.screensaver.components.WidgetStatusLine
import com.droidslife.screensaver.components.WidgetStatusSeverity
import com.droidslife.screensaver.components.pausesShortcutsWhileFocused
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.todos.providers.LocalTodosProvider
import com.droidslife.screensaver.todos.providers.Todo
import com.droidslife.screensaver.todos.providers.TodoDue
import com.droidslife.screensaver.todos.providers.TodoistProvider
import com.droidslife.screensaver.todos.providers.TodosProvider
import com.droidslife.screensaver.todos.providers.TodosSyncStatus
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellRadius
import com.droidslife.screensaver.ui.WidgetPrefs
import com.droidslife.screensaver.ui.renderTaskMarkdown
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
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

private const val WIDGET_ID = "com.droidslife.screensaver.todos"

/** Storage key for the persisted List/Matrix view choice. */
private const val VIEW_KEY = "todos-view"

/** A pending date prompt for [todo], with the date the picker should pre-select. */
private data class PendingDue(val todo: Todo, val initialDate: LocalDate)

class TodosWidgetFactory(
    private val backendGateway: BackendGateway,
) : WidgetFactory {
    override val id: String = WIDGET_ID
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
                        storage = scope.storage,
                    )
                }
            }
            else -> LocalTodosProvider(scope.storage, backendGateway, scope.coroutineScope, scope.log)
        }
    }

    private var input by mutableStateOf("")
    private var inputVisible by mutableStateOf(false)

    /** Id of the task whose detail dialog is open, or null. */
    private var detailId by mutableStateOf<String?>(null)

    /**
     * List vs. Eisenhower-matrix view. Read synchronously at construction and
     * written synchronously on toggle (see [WidgetPrefs]) so the choice is on
     * disk before any disposal/exit can drop it.
     */
    private var matrixView by mutableStateOf(WidgetPrefs.read(WIDGET_ID, VIEW_KEY) == "matrix")

    /** Pending date prompt (from a drag urgency-flip or a detail-view due edit). */
    private var pendingDue by mutableStateOf<PendingDue?>(null)

    private fun applyMatrixView(enabled: Boolean) {
        matrixView = enabled
        WidgetPrefs.write(WIDGET_ID, VIEW_KEY, if (enabled) "matrix" else "list")
    }

    /** Ids with an in-flight toggle/delete, so their row can show a pending dim. */
    private val pendingIds = mutableStateMapOf<String, Unit>()

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
        val syncStatus by provider.syncStatus().collectAsState(initial = TodosSyncStatus.Healthy)
        // Keep the off-composition summary in sync with the latest poll tick.
        LaunchedEffect(todos) { summaryCache.value = todos }

        // One calm line: a live transport/auth problem from the poller wins over
        // a transient per-action warning, so the user always sees the actionable
        // hint (e.g. "update your token") rather than a stale "couldn't save".
        val (errorMessage, errorSeverity) = when (val s = syncStatus) {
            is TodosSyncStatus.AuthFailed -> s.message to WidgetStatusSeverity.Error
            is TodosSyncStatus.Offline -> s.message to WidgetStatusSeverity.Warning
            TodosSyncStatus.Healthy ->
                (status as? TodosStatus.Warn)?.message to WidgetStatusSeverity.Error
        }

        val accent = LocalConsoleAccent.current.primary
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        // Subtasks roll up under their parent: only top-level tasks are listed,
        // each with a subtask-count badge; the children live in the detail view.
        val childrenByParent = todos.filter { it.parentId != null }.groupBy { it.parentId }
        val subtaskCountOf: (Todo) -> Int = { childrenByParent[it.id]?.size ?: 0 }
        val topLevel = todos.filter { it.parentId == null }

        val sections = groupedSections(topLevel, today)
        val openCount = topLevel.count { !it.done }
        // When no open task carries a due date the dated buckets are empty, so a
        // lone "NO DATE" header would just be noise — fall back to a flat list.
        val hasDatedOpen = sections.any {
            it.section == TodoSection.Overdue ||
                it.section == TodoSection.Today ||
                it.section == TodoSection.Upcoming
        }

        Box(modifier = modifier.fillMaxSize()) {
          Column(modifier = Modifier.fillMaxSize()) {
            WidgetHeader(
                label = if (openCount > 0) "TODOS · $openCount OPEN" else "TODOS",
                settingsId = WIDGET_ID,
            ) {
                ViewToggle(
                    matrix = matrixView,
                    accent = accent,
                    onToggle = { applyMatrixView(!matrixView) },
                )
                IconButton(
                    onClick = { inputVisible = !inputVisible },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = if (inputVisible) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (inputVisible) "Hide add form" else "Add task",
                        tint = if (inputVisible) accent else DwellColors.TextLow,
                    )
                }
            }

            if (inputVisible) {
                Spacer(Modifier.height(8.dp))
                AddBar(
                    value = input,
                    onValueChange = { input = it },
                    accent = accent,
                    onSubmit = ::onAdd,
                    onClose = { inputVisible = false; input = "" },
                )
            }

            Spacer(Modifier.height(8.dp))

            when {
                sections.isEmpty() -> TodosEmptyState(
                    adding = inputVisible,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                matrixView -> TodoMatrix(
                    buckets = EisenhowerMatrix.bucket(topLevel.filterNot { it.done }, today),
                    accent = accent,
                    today = today,
                    subtaskCountOf = subtaskCountOf,
                    onDrop = { todo, target -> onDropIntoQuadrant(todo, target, today) },
                    onOpen = { detailId = it.id },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    sections.forEach { data ->
                        val showHeader = when (data.section) {
                            TodoSection.NoDate -> hasDatedOpen
                            else -> true
                        }
                        if (showHeader) {
                            item(key = "header-${data.section}") {
                                SectionHeader(label = data.section.label, count = data.todos.size)
                            }
                        }
                        items(data.todos, key = { it.id }) { todo ->
                            TodoRow(
                                todo = todo,
                                accent = accent,
                                pending = pendingIds.containsKey(todo.id),
                                today = today,
                                subtaskCount = subtaskCountOf(todo),
                                onToggle = { onToggle(todo) },
                                onDelete = { onDelete(todo) },
                                onOpen = { detailId = todo.id },
                            )
                        }
                    }
                }
            }

            WidgetStatusLine(errorMessage, severity = errorSeverity)
          }

          // Full task detail: an in-widget overlay so it stays centered on the
          // tile (not the whole window) with a softer scrim than a full dialog.
          detailId?.let { id ->
              val todo = todos.firstOrNull { it.id == id }
              if (todo == null) {
                  detailId = null // task vanished (completed/deleted elsewhere)
              } else {
                  TodoDetailOverlay(
                      todo = todo,
                      subtasks = childrenByParent[todo.id].orEmpty(),
                      accent = accent,
                      today = today,
                      onClose = { detailId = null },
                      onEditText = { text -> onEdit(todo, text) },
                      onSetPriority = { p -> setTaskPriority(todo, p) },
                      onPickDue = { pendingDue = PendingDue(todo, todo.due?.date ?: today) },
                      onToggleSubtask = { sub -> onToggle(sub) },
                      onDelete = { onDelete(todo); detailId = null },
                  )
              }
          }
        }

        // Date prompt — raised by a drag urgency-flip or the detail view's due edit.
        pendingDue?.let { p ->
            DueDateDialog(
                initialDate = p.initialDate,
                onConfirm = { date -> applyDue(p.todo, date?.let { TodoDue(it) }) },
                onDismiss = { pendingDue = null },
            )
        }
    }

    /**
     * Buckets open tasks by due-date proximity (Overdue / Today / Upcoming / No
     * date) and appends completed tasks last, so a long list reads as a plan
     * rather than a flat dump. Empty buckets are dropped. Dated buckets sort by
     * date then priority; the undated bucket uses the user's sort preference.
     */
    private fun groupedSections(todos: List<Todo>, today: LocalDate): List<TodoSectionData> {
        val open = todos.filterNot { it.done }
        val done = if (config.bool("hideDone")) emptyList() else todos.filter { it.done }

        fun sectionOf(t: Todo): TodoSection {
            val date = t.due?.date ?: return TodoSection.NoDate
            return when {
                date < today -> TodoSection.Overdue
                date == today -> TodoSection.Today
                else -> TodoSection.Upcoming
            }
        }
        val bySection = open.groupBy(::sectionOf)
        // Soonest first, then higher Todoist priority (4 = urgent) on top.
        val dueOrder = compareBy<Todo> { it.due?.date ?: today }.thenByDescending { it.priority }

        val out = mutableListOf<TodoSectionData>()
        for (section in listOf(TodoSection.Overdue, TodoSection.Today, TodoSection.Upcoming)) {
            bySection[section]?.let { out += TodoSectionData(section, it.sortedWith(dueOrder)) }
        }
        bySection[TodoSection.NoDate]?.let { out += TodoSectionData(TodoSection.NoDate, sortByConfig(it)) }
        if (done.isNotEmpty()) out += TodoSectionData(TodoSection.Done, done.sortedByDescending { it.updatedAt })
        return out
    }

    private fun sortByConfig(todos: List<Todo>): List<Todo> = when (config.enum("sort", "newest")) {
        "oldest" -> todos.sortedBy { it.createdAt }
        "alphabetical" -> todos.sortedBy { it.text.lowercase() }
        else -> todos.sortedByDescending { it.createdAt }
    }

    private fun onAdd() {
        val text = input.trim()
        if (text.isBlank()) return
        input = "" // clear immediately; the field keeps focus for rapid entry
        scope.coroutineScope.launch {
            provider.add(text)
                .onSuccess { clearTransientWarn() }
                .onFailure { err ->
                    scope.log.warn("Todos add failed", err)
                    statusFlow.value = TodosStatus.Warn("Couldn't add task: ${err.message ?: "unknown error"}")
                }
        }
    }

    private fun onEdit(todo: Todo, newText: String) {
        val text = newText.trim()
        if (text.isBlank() || text == todo.text) return
        scope.coroutineScope.launch {
            provider.update(todo.id, text)
                .onSuccess { clearTransientWarn() }
                .onFailure { err ->
                    scope.log.warn("Todos edit failed", err)
                    statusFlow.value = TodosStatus.Warn("Couldn't edit task: ${err.message ?: "unknown error"}")
                }
        }
    }

    private fun setTaskPriority(todo: Todo, priority: Int) {
        if (priority == todo.priority) return
        scope.coroutineScope.launch {
            provider.setPriority(todo.id, priority)
                .onSuccess { clearTransientWarn() }
                .onFailure { err ->
                    scope.log.warn("Todos set priority failed", err)
                    statusFlow.value = TodosStatus.Warn("Couldn't set priority: ${err.message ?: "unknown error"}")
                }
        }
    }

    /**
     * A task was dragged into [target]. The importance axis (priority) is applied
     * immediately; an urgency flip can't be guessed, so it raises a date prompt.
     */
    private fun onDropIntoQuadrant(todo: Todo, target: Quadrant, today: LocalDate) {
        val newPriority = EisenhowerMatrix.priorityFor(target, todo.priority)
        if (newPriority != todo.priority) {
            scope.coroutineScope.launch {
                provider.setPriority(todo.id, newPriority)
                    .onSuccess { clearTransientWarn() }
                    .onFailure { err ->
                        scope.log.warn("Todos set priority failed", err)
                        statusFlow.value = TodosStatus.Warn("Couldn't set priority: ${err.message ?: "unknown error"}")
                    }
            }
        }
        if (EisenhowerMatrix.changesUrgency(todo, target, today)) {
            pendingDue = PendingDue(todo, EisenhowerMatrix.suggestedDueDate(target, today))
        }
    }

    private fun applyDue(todo: Todo, due: TodoDue?) {
        pendingDue = null
        scope.coroutineScope.launch {
            provider.setDue(todo.id, due)
                .onSuccess { clearTransientWarn() }
                .onFailure { err ->
                    scope.log.warn("Todos set due failed", err)
                    statusFlow.value = TodosStatus.Warn("Couldn't set due date: ${err.message ?: "unknown error"}")
                }
        }
    }

    private fun onToggle(todo: Todo) {
        if (pendingIds.containsKey(todo.id)) return
        pendingIds[todo.id] = Unit
        scope.coroutineScope.launch {
            provider.toggleDone(todo.id, !todo.done)
                .onSuccess { clearTransientWarn() }
                .onFailure { err ->
                    scope.log.warn("Todos toggle failed", err)
                    statusFlow.value = TodosStatus.Warn("Couldn't update task: ${err.message ?: "unknown error"}")
                }
            pendingIds.remove(todo.id)
        }
    }

    private fun onDelete(todo: Todo) {
        if (pendingIds.containsKey(todo.id)) return
        pendingIds[todo.id] = Unit
        scope.coroutineScope.launch {
            provider.delete(todo.id)
                .onSuccess { clearTransientWarn() }
                .onFailure { err ->
                    scope.log.warn("Todos delete failed", err)
                    statusFlow.value = TodosStatus.Warn("Couldn't delete task: ${err.message ?: "unknown error"}")
                }
            pendingIds.remove(todo.id)
        }
    }

    /**
     * Clears a transient operation warning once something succeeds, but keeps
     * the persistent "add a Todoist token" guidance (which mentions "token") so
     * the unconfigured-source hint doesn't vanish on a local add.
     */
    private fun clearTransientWarn() {
        val current = statusFlow.value
        if (current is TodosStatus.Warn && !current.message.contains("token", ignoreCase = true)) {
            statusFlow.value = TodosStatus.Ok
        }
    }
}

/** Ordered buckets the open list is grouped into (plus completed, last). */
private enum class TodoSection(val label: String) {
    Overdue("OVERDUE"),
    Today("TODAY"),
    Upcoming("UPCOMING"),
    NoDate("NO DATE"),
    Done("DONE"),
}

private data class TodoSectionData(val section: TodoSection, val todos: List<Todo>)

/** Small uppercase divider above each task group, with its count. */
@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = DwellColors.TextLow,
        )
        Text(
            text = count.toString(),
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            color = DwellColors.TextFaint,
        )
    }
}

/** Leading priority bar colour, or null for normal (Todoist p4 / priority 1). */
private fun priorityColor(priority: Int): Color? = when (priority) {
    4 -> DwellColors.StatusError   // urgent (p1)
    3 -> DwellColors.StatusAccent  // high (p2)
    2 -> DwellColors.TextMid       // medium (p3)
    else -> null                   // normal (p4) — no marker
}

/**
 * Relative due label + colour. Overdue is red, today the accent, everything
 * else a calm mid/low tone. Short by design — it sits inline on a task row.
 */
private fun dueLabelAndColor(due: TodoDue, today: LocalDate, accent: Color): Pair<String, Color> {
    val delta = today.daysUntil(due.date) // <0 overdue, 0 today, >0 future
    val time = due.time?.let { " ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }.orEmpty()
    return when {
        delta < 0 -> {
            val ago = -delta
            val label = if (ago == 1) "Yesterday" else "${ago}d ago"
            label to DwellColors.StatusError
        }
        delta == 0 -> "Today$time" to accent
        delta == 1 -> "Tomorrow$time" to DwellColors.TextMid
        delta in 2..6 -> weekdayShort(due.date.dayOfWeek) to DwellColors.TextMid
        else -> "${monthShort(due.date.month)} ${due.date.dayOfMonth}" to DwellColors.TextLow
    }
}

private fun weekdayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
    else -> day.name.take(3)
}

private fun monthShort(month: Month): String = when (month) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
    else -> month.name.take(3)
}

/** Inline add bar: a Dwell-styled text field + ADD chip. Enter submits, Esc closes. */
@Composable
private fun AddBar(
    value: String,
    onValueChange: (String) -> Unit,
    accent: Color,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DwellRadius.xs))
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(DwellRadius.xs))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Add a task…",
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 12.sp,
                    color = DwellColors.TextLow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(accent),
                textStyle = TextStyle(
                    color = DwellColors.TextHigh,
                    fontSize = 12.sp,
                    fontFamily = DwellFonts.interTight(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .pausesShortcutsWhileFocused()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> { onSubmit(); true }
                            Key.Escape -> { onClose(); true }
                            else -> false
                        }
                    },
            )
        }
        val enabled = value.isNotBlank()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) accent.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(enabled = enabled, onClick = onSubmit)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text(
                text = "ADD",
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = if (enabled) accent else DwellColors.TextFaint,
            )
        }
    }
}

/**
 * One interactive task line: a priority bar, round check toggle, the task text
 * (click opens the detail view), a subtask-count badge, a relative due chip,
 * and a hover-revealed delete.
 */
@Composable
private fun TodoRow(
    todo: Todo,
    accent: Color,
    pending: Boolean,
    today: LocalDate,
    subtaskCount: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .background(if (hovered) Color.White.copy(alpha = 0.03f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .alpha(if (pending) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Priority marker: a thin bar, transparent at normal priority so every
        // row keeps the same left alignment.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(priorityColor(todo.priority) ?: Color.Transparent),
        )

        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .then(
                    if (todo.done) {
                        Modifier.background(DwellColors.StatusOk)
                    } else {
                        Modifier.border(1.5.dp, if (hovered) accent else DwellColors.TextLow, CircleShape)
                    },
                )
                .clickable(enabled = !pending, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (todo.done) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = DwellColors.Surface0,
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        // Clicking the text (or the row body) opens the full detail view.
        Text(
            text = renderTaskMarkdown(todo.text, accent),
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !pending, onClick = onOpen),
            fontFamily = DwellFonts.interTight(),
            fontSize = 12.sp,
            color = if (todo.done) DwellColors.TextFaint else DwellColors.TextHigh,
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (subtaskCount > 0) {
            SubtaskBadge(count = subtaskCount)
        }

        todo.due?.let { due ->
            val (label, color) = dueLabelAndColor(due, today, accent)
            Text(
                text = label,
                fontFamily = DwellFonts.interTight(),
                fontSize = 10.sp,
                color = if (todo.done) DwellColors.TextFaint else color,
                maxLines = 1,
            )
        }

        // Delete reveals on hover (or while a delete is in flight) to keep the
        // resting row uncluttered.
        if (hovered || pending) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !pending, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete task",
                    tint = DwellColors.TextLow,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** Small pill showing how many subtasks a task rolls up. */
@Composable
private fun SubtaskBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = if (count == 1) "1 subtask" else "$count subtasks",
            fontFamily = DwellFonts.interTight(),
            fontSize = 9.sp,
            color = DwellColors.TextLow,
            maxLines = 1,
        )
    }
}

/** Inline editor for a task's text. Enter or blur commits, Esc cancels. */
@Composable
private fun TodoEditField(
    initial: String,
    accent: Color,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    textStyle: TextStyle = TextStyle(
        color = DwellColors.TextHigh,
        fontSize = 12.sp,
        fontFamily = DwellFonts.interTight(),
    ),
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    // Guard so the Enter/Esc path and the commit-on-blur path don't both fire.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoFocus) focusRequester.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        cursorBrush = SolidColor(accent),
        textStyle = textStyle,
        modifier = modifier
            .focusRequester(focusRequester)
            .pausesShortcutsWhileFocused()
            .onFocusChanged { if (!it.isFocused && !settled) { settled = true; onCommit(text) } }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter -> { if (!settled) { settled = true; onCommit(text) }; true }
                    Key.Escape -> { settled = true; onCancel(); true }
                    else -> false
                }
            },
    )
}

/** Shown when there are no tasks (respecting the hide-completed filter). */
@Composable
private fun TodosEmptyState(adding: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ALL CLEAR",
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = DwellColors.TextHigh,
            )
            if (!adding) {
                Text(
                    text = "press + to add a task",
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                    color = DwellColors.TextLow,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Per-quadrant display cap in the matrix; the rest collapse to "+N more". */
private const val MATRIX_MAX_PER_QUADRANT = 6

/** Header toggle between the list and the Eisenhower matrix (2×2 grid glyph). */
@Composable
private fun ViewToggle(matrix: Boolean, accent: Color, onToggle: () -> Unit) {
    val tint = if (matrix) accent else DwellColors.TextLow
    Box(
        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(2) {
                        Box(Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(tint))
                    }
                }
            }
        }
    }
}

private fun quadrantColor(quadrant: Quadrant, accent: Color): Color = when (quadrant) {
    Quadrant.Do -> DwellColors.StatusError
    Quadrant.Schedule -> accent
    Quadrant.Delegate -> DwellColors.StatusAccent
    Quadrant.Drop -> DwellColors.TextLow
}

/**
 * The Eisenhower 2×2 (urgent left, important top). Tasks are draggable chips;
 * dropping into another quadrant calls [onDrop], which writes back priority and,
 * on an urgency flip, prompts for a due date. Drop target is hit-tested at drag
 * end against each cell's live bounds, so layout-ordering races don't matter.
 */
@Composable
private fun TodoMatrix(
    buckets: Map<Quadrant, List<Todo>>,
    accent: Color,
    today: LocalDate,
    subtaskCountOf: (Todo) -> Int,
    onDrop: (Todo, Quadrant) -> Unit,
    onOpen: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cellCoords = remember { mutableStateMapOf<Quadrant, LayoutCoordinates>() }
    var dragging by remember { mutableStateOf<Todo?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    fun quadrantAt(point: Offset): Quadrant? {
        val c = container ?: return null
        return cellCoords.entries.firstOrNull { (_, coords) ->
            coords.isAttached && c.localBoundingBoxOf(coords).contains(point)
        }?.key
    }

    val hovered = dragging?.let { quadrantAt(dragPos) }

    Box(modifier = modifier.onGloballyPositioned { container = it }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Quadrant.Do to Quadrant.Schedule,
                Quadrant.Delegate to Quadrant.Drop,
            ).forEach { (left, right) ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(left, right).forEach { quadrant ->
                        QuadrantCell(
                            quadrant = quadrant,
                            todos = buckets[quadrant].orEmpty(),
                            accent = accent,
                            highlighted = hovered == quadrant && hovered != dragging?.let { EisenhowerMatrix.quadrantOf(it, today) },
                            draggingId = dragging?.id,
                            container = container,
                            subtaskCountOf = subtaskCountOf,
                            onCoords = { cellCoords[quadrant] = it },
                            onDragStart = { dragging = it },
                            onDragMove = { dragPos = it },
                            onDragEnd = {
                                val t = dragging
                                val target = quadrantAt(dragPos)
                                if (t != null && target != null && target != EisenhowerMatrix.quadrantOf(t, today)) {
                                    onDrop(t, target)
                                }
                                dragging = null
                            },
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        // Drag preview trailing the cursor (offset so it doesn't sit under it).
        dragging?.let { todo ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(dragPos.x.toInt() + 10, dragPos.y.toInt() + 10) }
                    .clip(RoundedCornerShape(6.dp))
                    .background(DwellColors.Surface1)
                    .border(1.dp, accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .alpha(0.95f),
            ) {
                Text(
                    text = renderTaskMarkdown(todo.text, accent),
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                    color = DwellColors.TextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    quadrant: Quadrant,
    todos: List<Todo>,
    accent: Color,
    highlighted: Boolean,
    draggingId: String?,
    container: LayoutCoordinates?,
    subtaskCountOf: (Todo) -> Int,
    onCoords: (LayoutCoordinates) -> Unit,
    onDragStart: (Todo) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onOpen: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = quadrantColor(quadrant, accent)
    Column(
        modifier = modifier
            .onGloballyPositioned(onCoords)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) Color.White.copy(alpha = 0.05f) else DwellColors.Surface1)
            .border(1.dp, if (highlighted) accent else DwellColors.Stroke, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = quadrant.label,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = label,
            )
            Text(
                text = todos.size.toString(),
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                color = DwellColors.TextFaint,
            )
        }
        if (todos.isEmpty()) {
            Text(
                text = "—",
                fontFamily = DwellFonts.interTight(),
                fontSize = 11.sp,
                color = DwellColors.TextFaint,
            )
        } else {
            todos.take(MATRIX_MAX_PER_QUADRANT).forEach { todo ->
                MatrixChip(
                    todo = todo,
                    beingDragged = draggingId == todo.id,
                    container = container,
                    subtaskCount = subtaskCountOf(todo),
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                    onOpen = { onOpen(todo) },
                )
            }
            if (todos.size > MATRIX_MAX_PER_QUADRANT) {
                Text(
                    text = "+${todos.size - MATRIX_MAX_PER_QUADRANT} more",
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 9.sp,
                    color = DwellColors.TextLow,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** A draggable task chip inside a quadrant. Reports pointer position in the
 *  matrix container's coordinate space so the parent can hit-test the drop. */
@Composable
private fun MatrixChip(
    todo: Todo,
    beingDragged: Boolean,
    container: LayoutCoordinates?,
    subtaskCount: Int,
    onDragStart: (Todo) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onOpen: () -> Unit,
) {
    var chipCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { chipCoords = it }
            .clip(RoundedCornerShape(5.dp))
            .background(DwellColors.Surface0)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .alpha(if (beingDragged) 0.4f else 1f)
            // Tap opens the detail view; a drag (past touch slop) reclassifies.
            .clickable(onClick = onOpen)
            .pointerInput(todo.id) {
                detectDragGestures(
                    onDragStart = { onDragStart(todo) },
                    onDrag = { change, _ ->
                        change.consume()
                        val c = container
                        val ch = chipCoords
                        if (c != null && ch != null && ch.isAttached) {
                            onDragMove(c.localPositionOf(ch, change.position))
                        }
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        priorityColor(todo.priority)?.let {
            Box(Modifier.size(5.dp).clip(CircleShape).background(it))
        }
        Text(
            text = renderTaskMarkdown(todo.text, DwellColors.TextMid),
            modifier = Modifier.weight(1f),
            fontFamily = DwellFonts.interTight(),
            fontSize = 10.sp,
            color = DwellColors.TextHigh,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtaskCount > 0) {
            Text(
                text = "$subtaskCount",
                fontFamily = DwellFonts.interTight(),
                fontSize = 9.sp,
                color = DwellColors.TextFaint,
            )
        }
    }
}

/** Date prompt for setting / removing a task's due date. [initialDate] is the
 *  pre-selection (a quadrant-derived suggestion, or the current due). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDays().toLong() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis
                    ?.let { LocalDate.fromEpochDays((it / MILLIS_PER_DAY).toInt()) }
                onConfirm(picked)
            }) { Text("Set date") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onConfirm(null) }) { Text("Remove date") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** DatePicker speaks in UTC-midnight millis; this converts to/from epoch days. */
private const val MILLIS_PER_DAY: Long = 86_400_000L

/**
 * Full task detail: editable title, priority + due controls, notes, and the
 * roll-up of subtasks (each toggleable). Opened by clicking a task in either
 * view. Edits flow straight back through the provider callbacks.
 */
@Composable
private fun BoxScope.TodoDetailOverlay(
    todo: Todo,
    subtasks: List<Todo>,
    accent: Color,
    today: LocalDate,
    onClose: () -> Unit,
    onEditText: (String) -> Unit,
    onSetPriority: (Int) -> Unit,
    onPickDue: () -> Unit,
    onToggleSubtask: (Todo) -> Unit,
    onDelete: () -> Unit,
) {
    // Softer scrim than a full-window dialog; tapping outside the card dismisses.
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
    )
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.94f)
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DwellColors.Surface0)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {} // swallow taps so clicking the card doesn't dismiss
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            // Title (editable) + close.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TodoEditField(
                    initial = todo.text,
                    accent = accent,
                    onCommit = onEditText,
                    onCancel = {},
                    autoFocus = false,
                    textStyle = TextStyle(
                        color = DwellColors.TextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = DwellFonts.interTight(),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = DwellColors.TextLow, modifier = Modifier.size(15.dp))
                }
            }

            // Priority selector (UI P1..P4 map to Todoist 4..1).
            DetailSectionLabel("PRIORITY")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4 to "P1", 3 to "P2", 2 to "P3", 1 to "P4").forEach { (level, label) ->
                    val selected = todo.priority == level
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) accent.copy(alpha = 0.16f) else DwellColors.Surface1)
                            .border(1.dp, if (selected) accent else DwellColors.Stroke, RoundedCornerShape(6.dp))
                            .clickable { onSetPriority(level) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = label,
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 11.sp,
                            color = if (selected) accent else DwellColors.TextMid,
                        )
                    }
                }
            }

            // Due date.
            DetailSectionLabel("DUE")
            val (dueLabel, dueColor) = todo.due
                ?.let { dueLabelAndColor(it, today, accent) }
                ?: ("Set a date" to DwellColors.TextLow)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DwellColors.Surface1)
                    .border(1.dp, DwellColors.Stroke, RoundedCornerShape(6.dp))
                    .clickable(onClick = onPickDue)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = dueLabel,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 12.sp,
                    color = dueColor,
                )
            }

            if (todo.note.isNotBlank()) {
                DetailSectionLabel("NOTES")
                Text(
                    text = renderTaskMarkdown(todo.note, accent, interactive = true),
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 12.sp,
                    color = DwellColors.TextMid,
                )
            }

            if (subtasks.isNotEmpty()) {
                DetailSectionLabel("SUBTASKS · ${subtasks.size}")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    subtasks.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { onToggleSubtask(sub) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (sub.done) Modifier.background(DwellColors.StatusOk)
                                        else Modifier.border(1.5.dp, DwellColors.TextLow, CircleShape),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sub.done) {
                                    Icon(Icons.Filled.Check, null, tint = DwellColors.Surface0, modifier = Modifier.size(10.dp))
                                }
                            }
                            Text(
                                text = renderTaskMarkdown(sub.text, accent),
                                modifier = Modifier.weight(1f),
                                fontFamily = DwellFonts.interTight(),
                                fontSize = 12.sp,
                                color = if (sub.done) DwellColors.TextFaint else DwellColors.TextHigh,
                                textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = DwellColors.StatusError, fontFamily = DwellFonts.interTight())
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text("Done", color = accent, fontFamily = DwellFonts.interTight())
                }
            }
        }
    }

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = DwellFonts.interTight(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 1.5.sp,
        color = DwellColors.TextLow,
    )
}
