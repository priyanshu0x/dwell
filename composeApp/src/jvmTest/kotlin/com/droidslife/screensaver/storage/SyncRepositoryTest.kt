package com.droidslife.screensaver.storage

import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.network.BackendResult
import com.droidslife.screensaver.widget.api.WidgetStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRepositoryTest {
    @Test
    fun pushPendingUpsertsQueuedItemsAndClearsOutbox() = runTest {
        val backend = RecordingBackend()
        val repository = SyncRepository("todos", InMemoryStorage(), backend)

        repository.enqueueUpsert(
            id = "todo-1",
            payload = JsonObject(mapOf("id" to JsonPrimitive("todo-1"), "text" to JsonPrimitive("Ship it"))),
            updatedAt = 100,
        )

        val result = repository.pushPending()

        assertEquals(1, result.pushed)
        assertEquals(0, result.remaining)
        assertEquals(listOf("todos/todo-1"), backend.upserts.map { it.first })
        assertEquals(0, repository.pendingCount())
    }

    @Test
    fun failedPushStaysPendingForRetry() = runTest {
        val backend = RecordingBackend(failUpsert = true)
        val repository = SyncRepository("expenses", InMemoryStorage(), backend)

        repository.enqueueUpsert(
            id = "expense-1",
            payload = JsonObject(mapOf("id" to JsonPrimitive("expense-1"))),
            updatedAt = 100,
        )

        val result = repository.pushPending()

        assertEquals(0, result.pushed)
        assertEquals(1, result.remaining)
        assertEquals(1, repository.pendingCount())
    }
}

private class RecordingBackend(
    private val failUpsert: Boolean = false,
) : BackendGateway {
    val upserts = mutableListOf<Pair<String, JsonObject>>()

    override suspend fun pull(collection: String): BackendResult<List<JsonObject>> {
        return BackendResult.Success(emptyList())
    }

    override suspend fun upsert(collection: String, id: String, payload: JsonObject): BackendResult<Unit> {
        if (failUpsert) return BackendResult.Failure("boom")
        upserts += "$collection/$id" to payload
        return BackendResult.Success(Unit)
    }

    override suspend fun delete(collection: String, id: String): BackendResult<Unit> {
        return BackendResult.Success(Unit)
    }
}

private class InMemoryStorage : WidgetStorage {
    private val values = mutableMapOf<String, Any>()

    override suspend fun <T : Any> read(key: String, type: Class<T>): T? {
        val value = values[key] ?: return null
        return if (type.isInstance(value)) type.cast(value) else null
    }

    override suspend fun <T : Any> write(key: String, value: T) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }
}
