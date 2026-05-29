package com.droidslife.screensaver.todos.providers

import com.droidslife.screensaver.widget.api.WidgetLogger
import com.droidslife.screensaver.widget.api.WidgetStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [TodosProvider] backed by Todoist's unified API v1.
 *
 *  - Base URL: `https://api.todoist.com/api/v1`
 *  - Auth:     `Authorization: Bearer <token>`
 *  - Endpoints used:
 *      * `GET    /tasks`                — list active (open) tasks
 *      * `POST   /tasks`                — create a task
 *      * `POST   /tasks/{id}/close`     — mark done
 *      * `POST   /tasks/{id}/reopen`    — mark undone
 *      * `DELETE /tasks/{id}`           — delete
 *
 * Tokens are per-user, generated in Todoist Settings → Integrations →
 * Developer API. Rate limit is 450 requests / 15 minutes / token, so the
 * 30-second poll cadence ([POLL_INTERVAL_MS]) lands well under the cap
 * even with concurrent mutations.
 *
 * `GET /tasks` only returns **active** tasks — once `/close` is called a
 * task disappears from the list. We surface that as `done = false`
 * snapshots (since closed tasks aren't visible) and trust the user to
 * reopen via Todoist directly for now; toggling a task from `done = true`
 * to `false` therefore won't fire here because the widget never sees the
 * task as done in the first place. The plumbing is still wired for when
 * we later swap in the full Sync API.
 *
 * Failure handling:
 *  - HTTP 401 → poller surfaces an empty list and the next mutation
 *    returns [TodosProviderUnauthorized] so the widget can prompt the
 *    user to refresh the token. Polling continues (the user may paste a
 *    new token mid-session without recreating the widget).
 *  - Other failures are logged and the previous in-memory snapshot is
 *    retained so the widget stays usable across transient blips.
 */
class TodoistProvider(
    private val http: HttpClient,
    private val apiToken: String,
    private val scope: CoroutineScope,
    private val log: WidgetLogger,
    private val storage: WidgetStorage,
) : TodosProvider {
    override val id: String = ID
    override val displayName: String = "Todoist"
    override val requiresApiKey: Boolean = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val cacheSerializer = ListSerializer(CachedTodo.serializer())
    private val state = MutableStateFlow<List<Todo>>(emptyList())
    private val sync = MutableStateFlow<TodosSyncStatus>(TodosSyncStatus.Healthy)
    private var pollJob: Job? = null

    override fun watch(): Flow<List<Todo>> {
        ensureConfigured()
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                loadCache() // show the last good snapshot immediately, then revalidate
                while (isActive) {
                    refreshSafely()
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        return state.asStateFlow()
    }

    override fun syncStatus(): Flow<TodosSyncStatus> = sync.asStateFlow()

    override suspend fun add(text: String, priority: Int, due: TodoDue?): Result<Unit> = runCatchingRequest {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@runCatchingRequest
        val response = http.post(TASKS_URL) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    NewTaskBody.serializer(),
                    NewTaskBody(
                        content = trimmed,
                        priority = priority.coerceIn(1, 4),
                        dueDate = due?.date?.toString(),
                    ),
                ),
            )
        }
        checkStatus(response, "create task")
        val created = json.decodeFromString(TodoistTask.serializer(), response.bodyAsText())
        // Optimistic update so the UI reacts before the next poll tick.
        state.value = state.value + created.toDomain()
    }

    override suspend fun update(id: String, text: String): Result<Unit> = runCatchingRequest {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@runCatchingRequest
        // v1 edits a task in place via POST /tasks/{id} (not PUT/PATCH).
        val response = http.post("$TASKS_URL/$id") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(NewTaskBody.serializer(), NewTaskBody(content = trimmed)))
        }
        checkStatus(response, "update task $id")
        // Optimistic update so the UI reacts before the next poll tick.
        state.value = state.value.map { if (it.id == id) it.copy(text = trimmed, updatedAt = Clock.System.now()) else it }
    }

    override suspend fun setPriority(id: String, priority: Int): Result<Unit> = runCatchingRequest {
        // Build a single-field body so we don't clobber other task fields with encoded defaults.
        val body = buildJsonObject { put("priority", priority) }
        val response = http.post("$TASKS_URL/$id") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        checkStatus(response, "set priority $id")
        // Optimistic update so the UI reacts before the next poll tick.
        state.value = state.value.map { if (it.id == id) it.copy(priority = priority, updatedAt = Clock.System.now()) else it }
    }

    override suspend fun setDue(id: String, due: TodoDue?): Result<Unit> = runCatchingRequest {
        val body = buildJsonObject {
            if (due == null) {
                // v1 clears a due date when the natural-language string is "no date".
                put("due_string", "no date")
            } else {
                // Intentionally date-only on write: dropping the time avoids timezone pitfalls.
                put("due_date", due.date.toString())
            }
        }
        val response = http.post("$TASKS_URL/$id") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        checkStatus(response, "set due $id")
        // Optimistic update so the UI reacts before the next poll tick.
        state.value = state.value.map { if (it.id == id) it.copy(due = due, updatedAt = Clock.System.now()) else it }
    }

    override suspend fun toggleDone(id: String, done: Boolean): Result<Unit> = runCatchingRequest {
        val suffix = if (done) "close" else "reopen"
        val response = http.post("$TASKS_URL/$id/$suffix") {
            authorize()
        }
        checkStatus(response, "$suffix task $id")
        // `close` removes the task from `GET /tasks`; mirror that locally.
        if (done) {
            state.value = state.value.filterNot { it.id == id }
        } else {
            state.value = state.value.map { if (it.id == id) it.copy(done = false, updatedAt = Clock.System.now()) else it }
        }
    }

    override suspend fun delete(id: String): Result<Unit> = runCatchingRequest {
        val response = http.delete("$TASKS_URL/$id") {
            authorize()
        }
        checkStatus(response, "delete task $id")
        state.value = state.value.filterNot { it.id == id }
    }

    private suspend fun refreshSafely() {
        try {
            val response = http.get(TASKS_URL) { authorize() }
            when (response.status) {
                HttpStatusCode.OK -> {
                    // v1 list endpoints are paginated: { "results": [...], "next_cursor": ... }.
                    // One page is plenty for a glanceable widget, so we don't follow the cursor.
                    val page = json.decodeFromString(TaskPage.serializer(), response.bodyAsText())
                    state.value = page.results.map { it.toDomain() }
                    sync.value = TodosSyncStatus.Healthy
                    persistCache(state.value)
                }
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                    log.warn("Todoist rejected the API token (HTTP ${response.status.value}); clearing snapshot")
                    state.value = emptyList()
                    sync.value = TodosSyncStatus.AuthFailed("Todoist token was rejected — update it in settings")
                }
                else -> {
                    // Keep the last snapshot; report a calm hint and let the next poll retry.
                    log.warn("Todoist /tasks returned ${response.status}; keeping previous snapshot")
                    sync.value = TodosSyncStatus.Offline("Todoist sync issue (HTTP ${response.status.value}) — retrying")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Todoist poll failed; keeping previous snapshot", e)
            sync.value = TodosSyncStatus.Offline("Can't reach Todoist — retrying")
        }
    }

    private fun checkStatus(response: HttpResponse, what: String) {
        val status = response.status
        if (status.value in 200..299) return
        if (status == HttpStatusCode.Unauthorized) {
            throw TodosProviderUnauthorized("Todoist rejected the API token while trying to $what")
        }
        throw RuntimeException("Todoist $what failed: HTTP ${status.value} ${status.description}")
    }

    // --- Stale-while-revalidate cache (mirrors the Weather widget) -------------
    // Persist the last good snapshot so a freshly (re)built widget shows tasks
    // instantly instead of an empty list until the first poll returns.

    private suspend fun loadCache() {
        if (state.value.isNotEmpty()) return
        val cached = runCatching {
            storage.read(CACHE_KEY, String::class.java)
                ?.let { json.decodeFromString(cacheSerializer, it) }
        }.getOrNull().orEmpty()
        if (cached.isNotEmpty() && state.value.isEmpty()) {
            state.value = cached.map { it.toDomain() }
        }
    }

    private fun persistCache(todos: List<Todo>) {
        scope.launch {
            runCatching {
                storage.write(CACHE_KEY, json.encodeToString(cacheSerializer, todos.map(CachedTodo::fromDomain)))
            }.onFailure { log.warn("Todoist cache write failed", it) }
        }
    }

    private fun ensureConfigured() {
        if (apiToken.isBlank()) {
            throw TodosProviderUnconfigured("Todoist API token is not configured")
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        bearerAuth(apiToken)
    }

    /** Stop polling. Currently unused — the widget cancels its scope which
     *  takes the poll job down with it — but exposed for parity with future
     *  hosts that pool provider instances. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private inline fun runCatchingRequest(block: () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    companion object {
        const val ID: String = "todoist"

        // Unified API v1. The legacy /rest/v2 endpoints were sunset and now
        // return HTTP 410 Gone, so we target /api/v1 (same bearer auth, but
        // list responses are paginated — see [TaskPage]).
        private const val TASKS_URL: String = "https://api.todoist.com/api/v1/tasks"

        /** Storage key for the persisted last-good snapshot. */
        private const val CACHE_KEY: String = "todoist-cache.json"

        /** Todoist allows 450 req / 15min / token; 30s polling gives us ample
         *  headroom for concurrent mutations. */
        internal const val POLL_INTERVAL_MS: Long = 30_000
    }
}

// -----------------------------------------------------------------------------
// JSON shapes
// -----------------------------------------------------------------------------
//
// Only the fields the widget actually renders are deserialized; everything
// else falls under `ignoreUnknownKeys`.

/** Paginated envelope the v1 list endpoints wrap their rows in. */
@Serializable
internal data class TaskPage(
    val results: List<TodoistTask> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
internal data class TodoistTask(
    val id: String,
    val content: String,
    val description: String = "",
    // Present on subtasks; the widget rolls subtasks up under their parent.
    @SerialName("parent_id") val parentId: String? = null,
    // Todoist scale: 1 = normal … 4 = urgent. Absent on minimal payloads → default 1.
    val priority: Int = 1,
    val due: TodoistDue? = null,
    // v1 marks completion by the presence of a timestamp rather than a boolean.
    @SerialName("completed_at") val completedAt: String? = null,
    // v1 sends "added_at"; older payloads used "created_at" — accept either.
    @SerialName("added_at") val addedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain(): Todo {
        val createdRaw = addedAt ?: createdAt
        val created = createdRaw?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Clock.System.now()
        return Todo(
            id = id,
            text = content,
            done = completedAt != null,
            createdAt = created,
            updatedAt = created,
            priority = priority,
            due = due?.toDomain(),
            parentId = parentId,
            note = description,
        )
    }
}

/**
 * Todoist's `due` object. `date` is always "YYYY-MM-DD"; `datetime` is present
 * only for time-specific tasks and may arrive as a local datetime (no zone) or
 * with a 'Z'/offset. Other fields (string, is_recurring, timezone) are ignored.
 */
@Serializable
internal data class TodoistDue(
    val date: String,
    val datetime: String? = null,
) {
    fun toDomain(): TodoDue? {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        // Parse defensively: a malformed datetime degrades to date-only rather than
        // dropping the whole due. Zoned datetimes ('Z'/offset) parse as Instant and
        // get localized; bare local datetimes parse directly as LocalDateTime.
        val time: LocalTime? = datetime?.let { raw ->
            runCatching {
                Instant.parse(raw).toLocalDateTime(TimeZone.currentSystemDefault()).time
            }.recoverCatching {
                LocalDateTime.parse(raw).time
            }.getOrNull()
        }
        return TodoDue(date = day, time = time)
    }
}

@Serializable
internal data class NewTaskBody(
    val content: String,
    val priority: Int? = null,
    @SerialName("due_date") val dueDate: String? = null,
)

/**
 * Disk-cache shape for the last good snapshot. Stores times as epoch millis and
 * the due as ISO strings so the document is stable across `Instant`/datetime
 * revisions (same approach as the local provider's StoredTodo).
 */
@Serializable
internal data class CachedTodo(
    val id: String,
    val text: String,
    val done: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val priority: Int = 1,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val parentId: String? = null,
    val note: String = "",
) {
    fun toDomain(): Todo = Todo(
        id = id,
        text = text,
        done = done,
        createdAt = Instant.fromEpochMilliseconds(createdAtMs),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtMs),
        priority = priority,
        due = dueDate?.let {
            runCatching { TodoDue(LocalDate.parse(it), dueTime?.let(LocalTime::parse)) }.getOrNull()
        },
        parentId = parentId,
        note = note,
    )

    companion object {
        fun fromDomain(todo: Todo): CachedTodo = CachedTodo(
            id = todo.id,
            text = todo.text,
            done = todo.done,
            createdAtMs = todo.createdAt.toEpochMilliseconds(),
            updatedAtMs = todo.updatedAt.toEpochMilliseconds(),
            priority = todo.priority,
            dueDate = todo.due?.date?.toString(),
            dueTime = todo.due?.time?.toString(),
            parentId = todo.parentId,
            note = todo.note,
        )
    }
}
