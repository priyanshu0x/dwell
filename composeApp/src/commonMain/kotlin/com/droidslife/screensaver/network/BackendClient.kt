package com.droidslife.screensaver.network

import com.droidslife.screensaver.settings.SettingsModel
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

class BackendClient(
    private val httpClient: HttpClient,
    private val settingsProvider: () -> SettingsModel,
    private val tokenProvider: suspend () -> String?,
) : BackendGateway {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun pull(collection: String): BackendResult<List<JsonObject>> {
        val baseUrl = baseUrlOrNull() ?: return BackendResult.Disabled
        return call {
            val response = httpClient.get {
                url {
                    takeFrom(baseUrl)
                    appendPathSegments("api", collection)
                }
                authorize()
            }
            parseItems(response.bodyAsText())
        }
    }

    override suspend fun upsert(collection: String, id: String, payload: JsonObject): BackendResult<Unit> {
        val baseUrl = baseUrlOrNull() ?: return BackendResult.Disabled
        return call {
            httpClient.put {
                url {
                    takeFrom(baseUrl)
                    appendPathSegments("api", collection, id)
                }
                authorize()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(payload))
            }
            Unit
        }
    }

    override suspend fun delete(collection: String, id: String): BackendResult<Unit> {
        val baseUrl = baseUrlOrNull() ?: return BackendResult.Disabled
        return call {
            httpClient.delete {
                url {
                    takeFrom(baseUrl)
                    appendPathSegments("api", collection, id)
                }
                authorize()
            }
            Unit
        }
    }

    private fun baseUrlOrNull(): String? {
        return settingsProvider().backendBaseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() }
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        val token = tokenProvider()?.trim().orEmpty()
        if (token.isNotBlank()) {
            bearerAuth(token)
        }
    }

    private fun parseItems(body: String): List<JsonObject> {
        val root = json.parseToJsonElement(body)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["items"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return items.mapNotNull { it as? JsonObject }
    }

    private suspend inline fun <T> call(crossinline block: suspend () -> T): BackendResult<T> {
        return try {
            BackendResult.Success(block())
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            BackendResult.Failure(error.message ?: "Backend request failed", error)
        }
    }
}
