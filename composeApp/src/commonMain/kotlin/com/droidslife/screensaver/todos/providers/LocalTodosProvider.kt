package com.droidslife.screensaver.todos.providers

import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.network.BackendResult
import com.droidslife.screensaver.storage.SyncRepository
import com.droidslife.screensaver.widget.api.WidgetLogger
import com.droidslife.screensaver.widget.api.WidgetStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Default [TodosProvider] — stores the list on this machine via the widget's
 * [WidgetStorage] and pushes through the existing [SyncRepository] outbox if
 * the host has configured a backend.
 *
 * This adapter is a near-direct lift of the original `TodosWidgetFactory`
 * in-memory + persistence logic, repackaged behind the port so a sibling
 * provider (e.g. [TodoistProvider]) can be swapped in without the widget
 * caring.
 *
 * Lifecycle:
 *  - The factory hands us a long-lived [CoroutineScope] (the widget scope).
 *  - We hydrate from storage on first subscription via [load].
 *  - Mutations update the in-memory snapshot, persist asynchronously, and
 *    enqueue an outbox entry for the backend sync.
 */
class LocalTodosProvider(
    private val storage: WidgetStorage,
    private val backend: BackendGateway,
    private val scope: CoroutineScope,
    private val log: WidgetLogger,
) : TodosProvider {
    override val id: String = ID
    override val displayName: String = "Local (this machine)"
    override val requiresApiKey: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StoredTodo.serializer())
    private val syncRepository = SyncRepository("todos", storage, backend)

    private val state = MutableStateFlow<List<Todo>>(emptyList())
    private var loaded = false

    override fun watch(): Flow<List<Todo>> {
        if (!loaded) {
            loaded = true
            scope.launch { load() }
        }
        return state.asStateFlow()
    }

    private suspend fun load() {
        val stored = storage.read("todos.json", String::class.java)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: emptyList()
        state.value = stored.map { it.toDomain() }
        mergeRemote()
        pushPending()
    }

    override suspend fun add(text: String, priority: Int, due: TodoDue?): Result<Unit> = runCatching {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@runCatching
        val now = Clock.System.now()
        val todo = Todo(
            id = "todo-${now.toEpochMilliseconds()}-${state.value.size}",
            text = trimmed,
            done = false,
            createdAt = now,
            updatedAt = now,
            priority = priority.coerceIn(1, 4),
            due = due,
        )
        state.value = state.value + todo
        persist()
        queueUpsert(todo)
    }

    override suspend fun toggleDone(id: String, done: Boolean): Result<Unit> = runCatching {
        val now = Clock.System.now()
        var changed: Todo? = null
        state.value = state.value.map { todo ->
            if (todo.id == id) {
                todo.copy(done = done, updatedAt = now).also { changed = it }
            } else {
                todo
            }
        }
        persist()
        changed?.let { queueUpsert(it) }
    }

    override suspend fun update(id: String, text: String): Result<Unit> = runCatching {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@runCatching
        val now = Clock.System.now()
        var changed: Todo? = null
        state.value = state.value.map { todo ->
            if (todo.id == id) {
                todo.copy(text = trimmed, updatedAt = now).also { changed = it }
            } else {
                todo
            }
        }
        persist()
        changed?.let { queueUpsert(it) }
    }

    override suspend fun setPriority(id: String, priority: Int): Result<Unit> = runCatching {
        val now = Clock.System.now()
        var changed: Todo? = null
        state.value = state.value.map { todo ->
            if (todo.id == id) {
                todo.copy(priority = priority, updatedAt = now).also { changed = it }
            } else {
                todo
            }
        }
        persist()
        changed?.let { queueUpsert(it) }
    }

    override suspend fun setDue(id: String, due: TodoDue?): Result<Unit> = runCatching {
        val now = Clock.System.now()
        var changed: Todo? = null
        state.value = state.value.map { todo ->
            if (todo.id == id) {
                todo.copy(due = due, updatedAt = now).also { changed = it }
            } else {
                todo
            }
        }
        persist()
        changed?.let { queueUpsert(it) }
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        state.value = state.value.filterNot { it.id == id }
        persist()
        syncRepository.enqueueDelete(id)
        pushPending()
    }

    private fun persist() {
        val snapshot = json.encodeToString(serializer, state.value.map(StoredTodo::fromDomain))
        scope.launch {
            storage.write("todos.json", snapshot)
        }
    }

    private fun queueUpsert(todo: Todo) {
        val stored = StoredTodo.fromDomain(todo)
        scope.launch {
            syncRepository.enqueueUpsert(todo.id, stored.toJsonObject(), todo.updatedAt.toEpochMilliseconds())
            pushPending()
        }
    }

    private suspend fun mergeRemote() {
        when (val result = syncRepository.pullRemote()) {
            BackendResult.Disabled -> Unit
            is BackendResult.Failure -> {
                log.warn("Todo sync pull failed", result.cause)
            }
            is BackendResult.Success -> {
                val remote = result.value.mapNotNull {
                    runCatching { it.toStoredTodo() }.getOrNull()
                }
                if (remote.isNotEmpty()) {
                    val merged = (state.value.map(StoredTodo::fromDomain) + remote)
                        .groupBy { it.id }
                        .map { (_, values) -> values.maxBy { it.updatedAt } }
                        .map { it.toDomain() }
                    state.value = merged
                    persist()
                }
            }
        }
    }

    private suspend fun pushPending() {
        val result = syncRepository.pushPending()
        if (result.lastError != null) {
            log.warn("Todo sync push failed: ${result.lastError}")
        }
    }

    /** Outstanding outbox entries — surfaced to UI for the "unsynced" hint. */
    suspend fun pendingCount(): Int = syncRepository.pendingCount()

    private fun StoredTodo.toJsonObject(): JsonObject {
        return json.encodeToJsonElement(StoredTodo.serializer(), this) as JsonObject
    }

    private fun JsonObject.toStoredTodo(): StoredTodo {
        return json.decodeFromJsonElement(StoredTodo.serializer(), this)
    }

    companion object {
        const val ID: String = "local"
    }
}

/**
 * On-disk shape preserved for compatibility with the pre-port `todos.json`
 * format the widget used to write. Times are epoch milliseconds so the
 * serialized document is portable across `kotlin.time.Instant` revisions.
 */
@Serializable
private data class StoredTodo(
    val id: String,
    val text: String,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    // Defaults keep older `todos.json` (written before priority/due) decodable.
    val priority: Int = 1,
    // Due is stored as primitive ISO strings (not TodoDue) for forward-compat
    // across kotlinx-datetime revisions, mirroring the epoch-millis time fields.
    val dueDate: String? = null,
    val dueTime: String? = null,
) {
    fun toDomain(): Todo = Todo(
        id = id,
        text = text,
        done = done,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        priority = priority,
        // Parse defensively: a malformed stored value yields no due rather than throwing.
        due = dueDate?.let { date ->
            runCatching {
                TodoDue(LocalDate.parse(date), dueTime?.let(LocalTime::parse))
            }.getOrNull()
        },
    )

    companion object {
        fun fromDomain(todo: Todo): StoredTodo = StoredTodo(
            id = todo.id,
            text = todo.text,
            done = todo.done,
            createdAt = todo.createdAt.toEpochMilliseconds(),
            updatedAt = todo.updatedAt.toEpochMilliseconds(),
            priority = todo.priority,
            dueDate = todo.due?.date?.toString(),
            dueTime = todo.due?.time?.toString(),
        )
    }
}
