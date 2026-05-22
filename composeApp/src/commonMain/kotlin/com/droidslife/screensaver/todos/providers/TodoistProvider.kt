package com.droidslife.screensaver.todos.providers

import com.droidslife.screensaver.widget.api.WidgetLogger
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [TodosProvider] backed by Todoist's REST API v2.
 *
 *  - Base URL: `https://api.todoist.com/rest/v2`
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
) : TodosProvider {
    override val id: String = ID
    override val displayName: String = "Todoist"
    override val requiresApiKey: Boolean = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val state = MutableStateFlow<List<Todo>>(emptyList())
    private var pollJob: Job? = null

    override fun watch(): Flow<List<Todo>> {
        ensureConfigured()
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                while (isActive) {
                    refreshSafely()
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        return state.asStateFlow()
    }

    override suspend fun add(text: String): Result<Unit> = runCatchingRequest {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@runCatchingRequest
        val response = http.post(TASKS_URL) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(NewTaskBody.serializer(), NewTaskBody(content = trimmed)))
        }
        checkStatus(response, "create task")
        val created = json.decodeFromString(TodoistTask.serializer(), response.bodyAsText())
        // Optimistic update so the UI reacts before the next poll tick.
        state.value = state.value + created.toDomain()
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
                    val tasks = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(TodoistTask.serializer()),
                        response.bodyAsText(),
                    )
                    state.value = tasks.map { it.toDomain() }
                }
                HttpStatusCode.Unauthorized -> {
                    log.warn("Todoist rejected the API token (HTTP 401); clearing snapshot")
                    state.value = emptyList()
                }
                else -> {
                    log.warn("Todoist /tasks returned ${response.status}; keeping previous snapshot")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Todoist poll failed; keeping previous snapshot", e)
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
        private const val TASKS_URL: String = "https://api.todoist.com/rest/v2/tasks"

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

@Serializable
internal data class TodoistTask(
    val id: String,
    val content: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain(): Todo {
        val created = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Clock.System.now()
        return Todo(
            id = id,
            text = content,
            done = isCompleted,
            createdAt = created,
            updatedAt = created,
        )
    }
}

@Serializable
internal data class NewTaskBody(
    val content: String,
)
