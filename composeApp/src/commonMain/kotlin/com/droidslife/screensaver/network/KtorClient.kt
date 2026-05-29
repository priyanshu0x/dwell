package com.droidslife.screensaver.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Provides a configured Ktor HttpClient for making API requests.
 */
object KtorClient {
    /** Fixed gap between retry attempts (see [create]). */
    private const val RETRY_DELAY_MS: Long = 3_000
    private const val REQUEST_TIMEOUT_MS: Long = 15_000
    private const val CONNECT_TIMEOUT_MS: Long = 5_000
    private const val SOCKET_TIMEOUT_MS: Long = 10_000

    /**
     * Creates and returns a configured HttpClient instance.
     *
     * @return A configured HttpClient instance.
     */
    fun create(maxRetries: Int = 0): HttpClient {
        return HttpClient {
            // Install ContentNegotiation plugin with kotlinx.serialization
            install(ContentNegotiation) {
                json(Json {
                    // Configure JSON serializer to ignore unknown keys
                    ignoreUnknownKeys = true
                    // Allow serialization of special floating point values (NaN, Infinity)
                    isLenient = true
                    // Allow serialization of objects with missing fields
                    coerceInputValues = true
                })
            }

            // Install Logging plugin
            install(Logging) {
                level = LogLevel.INFO
            }

            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }

            // Configure default request parameters
            defaultRequest {
                // Default request configuration can be added here if needed
            }

            if (maxRetries > 0) {
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries)
                    retryOnException(maxRetries, retryOnTimeout = true)
                    // Fixed cadence rather than exponential backoff: a flaky
                    // network recovers in seconds, and a steadily climbing delay
                    // just makes the UI feel stuck. Honor Retry-After if present.
                    constantDelay(
                        millis = RETRY_DELAY_MS,
                        randomizationMs = 1_000,
                        respectRetryAfterHeader = true,
                    )
                }
            }
        }
    }

    /**
     * Creates and returns a configured HttpClient instance with retry functionality.
     * 
     * @param maxRetries The maximum number of retry attempts.
     * @return A configured HttpClient instance with retry functionality.
     */
    fun createWithRetry(maxRetries: Int = 3): HttpClient {
        return create(maxRetries)
    }
}
