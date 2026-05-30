package com.droidslife.screensaver.network

import com.droidslife.screensaver.serialization.DwellJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/**
 * Thin client for the real Fastifly ledger API, authenticated with a Fastifly
 * API key (`ffk_...`) sent as a bearer token. The server skips CSRF for API-key
 * auth, so no cookie/token dance is needed here.
 *
 * Endpoints follow `/api/v1/workspaces/{workspaceId}/ledgers/{ledgerId}/...`;
 * callers first resolve their workspace + ledger via [meContext].
 */
class FastiflyClient(
    private val httpClient: HttpClient,
    private val baseUrlProvider: () -> String?,
    private val tokenProvider: suspend () -> String?,
) {
    private val json = DwellJson.Persisted

    fun isConfigured(): Boolean = baseUrlOrNull() != null

    suspend fun meContext(): FastiflyResult<MeContext> = call {
        val body = getJson("api", "v1", "me", "context")
        val dto = json.decodeFromString(MeContextEnvelope.serializer(), body).data
        MeContext(
            workspaceId = dto.activeWorkspace.id,
            ledgerId = dto.activeLedger.id,
            currencyCode = dto.activeLedger.baseCurrencyCode,
            workspaceName = dto.activeWorkspace.name,
            ledgerName = dto.activeLedger.name,
        )
    }

    suspend fun listAccounts(ctx: MeContext): FastiflyResult<List<FastiflyAccount>> = call {
        val body = getJson(
            "api", "v1", "workspaces", ctx.workspaceId, "ledgers", ctx.ledgerId, "accounts",
            query = mapOf("limit" to "100"),
        )
        json.decodeFromString(AccountListEnvelope.serializer(), body).data
    }

    suspend fun listCategories(ctx: MeContext): FastiflyResult<List<FastiflyCategory>> = call {
        val body = getJson(
            "api", "v1", "workspaces", ctx.workspaceId, "ledgers", ctx.ledgerId, "categories",
            query = mapOf("limit" to "100"),
        )
        json.decodeFromString(CategoryListEnvelope.serializer(), body).data
    }

    /** Most-recent transaction groups, newest first. [fromOccurredAt] is an ISO-8601 lower bound. */
    suspend fun listTransactions(
        ctx: MeContext,
        limit: Int,
        fromOccurredAt: String? = null,
    ): FastiflyResult<List<FastiflyTransactionGroup>> = call {
        val query = buildMap {
            put("limit", limit.toString())
            if (fromOccurredAt != null) put("fromOccurredAt", fromOccurredAt)
        }
        val body = getJson(
            "api", "v1", "workspaces", ctx.workspaceId, "ledgers", ctx.ledgerId, "transactions",
            query = query,
        )
        json.decodeFromString(TransactionListEnvelope.serializer(), body).data
    }

    suspend fun createTransaction(
        ctx: MeContext,
        request: CreateTransactionRequest,
        idempotencyKey: String,
    ): FastiflyResult<Unit> = call {
        val baseUrl = baseUrlOrNull() ?: error("Backend URL is not configured")
        val response = httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegments(
                    "api", "v1", "workspaces", ctx.workspaceId, "ledgers", ctx.ledgerId,
                    "transactions",
                )
            }
            authorize()
            header("idempotency-key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateTransactionRequest.serializer(), request))
        }
        response.ensureSuccess()
        Unit
    }

    private suspend fun getJson(vararg segments: String, query: Map<String, String> = emptyMap()): String {
        val baseUrl = baseUrlOrNull() ?: error("Backend URL is not configured")
        val response = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegments(*segments)
                if (query.isNotEmpty()) {
                    parameters { query.forEach { (k, v) -> append(k, v) } }
                }
            }
            authorize()
        }
        return response.ensureSuccess().bodyAsText()
    }

    private fun baseUrlOrNull(): String? =
        baseUrlProvider()?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        val token = tokenProvider()?.trim().orEmpty()
        if (token.isNotBlank()) bearerAuth(token)
    }

    private fun HttpResponse.ensureSuccess(): HttpResponse {
        if (!status.isSuccess()) {
            throw FastiflyHttpException(status.value, status.description)
        }
        return this
    }

    private class FastiflyHttpException(val code: Int, description: String) :
        IllegalStateException("Fastifly responded $code${if (description.isBlank()) "" else " $description"}")

    private suspend inline fun <T> call(crossinline block: suspend () -> T): FastiflyResult<T> {
        if (baseUrlOrNull() == null) return FastiflyResult.Disabled
        return try {
            // Bound the full multi-request sequence so expensive sync calls
            // cannot keep the widget stuck on a loading state.
            FastiflyResult.Success(withTimeout(REQUEST_TIMEOUT_MS) { block() })
        } catch (timeout: TimeoutCancellationException) {
            FastiflyResult.Failure(timeout.networkFailureSummary("Fastifly"), null)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val code = (error as? FastiflyHttpException)?.code
            FastiflyResult.Failure(
                if (error.isTransientNetworkFailure()) error.networkFailureSummary("Fastifly") else error.message ?: "Fastifly request failed",
                code,
            )
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}

sealed interface FastiflyResult<out T> {
    data class Success<T>(val value: T) : FastiflyResult<T>
    data object Disabled : FastiflyResult<Nothing>
    data class Failure(val message: String, val httpCode: Int? = null) : FastiflyResult<Nothing>
}

// --- Domain models exposed to the widget -----------------------------------

@Serializable
data class MeContext(
    val workspaceId: String,
    val ledgerId: String,
    val currencyCode: String,
    val workspaceName: String,
    val ledgerName: String,
)

// --- Wire DTOs (parsed with ignoreUnknownKeys) -----------------------------

@Serializable
private data class MeContextEnvelope(val data: MeContextData)

@Serializable
private data class MeContextData(val activeLedger: ActiveLedgerDto, val activeWorkspace: ActiveWorkspaceDto)

@Serializable
private data class ActiveLedgerDto(val id: String, val name: String = "", val baseCurrencyCode: String = "")

@Serializable
private data class ActiveWorkspaceDto(val id: String, val name: String = "")

@Serializable
data class FastiflyAccount(
    val id: String,
    val name: String,
    val kind: String,
    val currencyCode: String = "",
    val isActive: Boolean = true,
)

@Serializable
private data class AccountListEnvelope(val data: List<FastiflyAccount> = emptyList())

@Serializable
data class FastiflyCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val counterpartyAccountId: String? = null,
)

@Serializable
private data class CategoryListEnvelope(val data: List<FastiflyCategory> = emptyList())

@Serializable
data class FastiflyPosting(val accountId: String, val amountMinor: String = "0", val currencyCode: String = "")

@Serializable
data class FastiflyJournal(
    val type: String = "",
    val description: String? = null,
    val occurredAt: String = "",
    val status: String = "",
    val postings: List<FastiflyPosting> = emptyList(),
)

@Serializable
data class FastiflyTransactionGroup(
    val id: String,
    val type: String = "",
    val title: String? = null,
    val journals: List<FastiflyJournal> = emptyList(),
)

@Serializable
private data class TransactionListEnvelope(val data: List<FastiflyTransactionGroup> = emptyList())

@Serializable
data class CreateTransactionLine(
    val amountMinor: String,
    val destinationAccountId: String,
    val categoryId: String? = null,
)

@Serializable
data class CreateTransactionOptions(val recalculateBalances: Boolean = true)

@Serializable
data class CreateTransactionRequest(
    val type: String,
    val currencyCode: String,
    val description: String,
    val occurredAt: String,
    val sourceAccountId: String,
    val source: String = "api",
    val status: String = "cleared",
    val options: CreateTransactionOptions = CreateTransactionOptions(),
    val transactions: List<CreateTransactionLine>,
)
