package com.droidslife.screensaver.network

import kotlinx.serialization.json.JsonObject

interface BackendGateway {
    suspend fun pull(collection: String): BackendResult<List<JsonObject>>
    suspend fun upsert(collection: String, id: String, payload: JsonObject): BackendResult<Unit>
    suspend fun delete(collection: String, id: String): BackendResult<Unit>
}

sealed interface BackendResult<out T> {
    data class Success<T>(val value: T) : BackendResult<T>
    data object Disabled : BackendResult<Nothing>
    data class Failure(val message: String, val cause: Throwable? = null) : BackendResult<Nothing>
}
