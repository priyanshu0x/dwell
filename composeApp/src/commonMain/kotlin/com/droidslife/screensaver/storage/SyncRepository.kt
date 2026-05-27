package com.droidslife.screensaver.storage

import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.network.BackendResult
import com.droidslife.screensaver.widget.api.WidgetStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

class SyncRepository(
    private val collection: String,
    private val storage: WidgetStorage,
    private val backend: BackendGateway,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SyncOutboxItem.serializer())
    private val outboxKey = "sync-$collection-outbox.json"

    suspend fun enqueueUpsert(id: String, payload: JsonObject, updatedAt: Long) {
        val existing = readOutbox().filterNot { it.id == id }
        writeOutbox(
            existing + SyncOutboxItem(
                id = id,
                payload = payload,
                updatedAt = updatedAt,
                deleted = false,
            ),
        )
    }

    suspend fun enqueueDelete(id: String, updatedAt: Long = Clock.System.now().toEpochMilliseconds()) {
        val existing = readOutbox().filterNot { it.id == id }
        writeOutbox(
            existing + SyncOutboxItem(
                id = id,
                payload = JsonObject(emptyMap()),
                updatedAt = updatedAt,
                deleted = true,
            ),
        )
    }

    suspend fun pendingCount(): Int = readOutbox().size

    suspend fun pushPending(): SyncPushResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val pending = readOutbox()
        if (pending.isEmpty()) return SyncPushResult(0, 0, null)

        val remaining = mutableListOf<SyncOutboxItem>()
        var pushed = 0
        var lastError: String? = null

        pending.forEach { item ->
            if (item.nextRetryAt > now) {
                remaining += item
                return@forEach
            }

            val result = if (item.deleted) {
                backend.delete(collection, item.id)
            } else {
                backend.upsert(collection, item.id, item.payload)
            }

            when (result) {
                BackendResult.Disabled -> remaining += item
                is BackendResult.Failure -> {
                    lastError = result.message
                    remaining += item.copy(
                        attempts = item.attempts + 1,
                        nextRetryAt = now + retryDelayMillis(item.attempts + 1),
                        lastError = result.message,
                    )
                }
                is BackendResult.Success -> pushed += 1
            }
        }

        writeOutbox(remaining)
        return SyncPushResult(pushed = pushed, remaining = remaining.size, lastError = lastError)
    }

    suspend fun pullRemote(): BackendResult<List<JsonObject>> = backend.pull(collection)

    private suspend fun readOutbox(): List<SyncOutboxItem> {
        return storage.read(outboxKey, String::class.java)
            ?.let { json.decodeFromString(serializer, it) }
            ?: emptyList()
    }

    private suspend fun writeOutbox(items: List<SyncOutboxItem>) {
        if (items.isEmpty()) {
            storage.delete(outboxKey)
        } else {
            storage.write(outboxKey, json.encodeToString(serializer, items))
        }
    }

    // Fixed retry cadence instead of exponential backoff: the outbox is drained
    // opportunistically, so a steady short interval keeps a recovered backend in
    // sync quickly without the delay creeping toward minutes. [attempts] is kept
    // for the signature / future capping but no longer grows the wait.
    private fun retryDelayMillis(@Suppress("UNUSED_PARAMETER") attempts: Int): Long =
        RETRY_INTERVAL_SECONDS * 1_000L

    private companion object {
        const val RETRY_INTERVAL_SECONDS = 10L
    }
}

data class SyncPushResult(
    val pushed: Int,
    val remaining: Int,
    val lastError: String?,
)

@Serializable
private data class SyncOutboxItem(
    val id: String,
    val payload: JsonObject,
    val updatedAt: Long,
    val deleted: Boolean,
    val attempts: Int = 0,
    val nextRetryAt: Long = 0,
    val lastError: String? = null,
)
