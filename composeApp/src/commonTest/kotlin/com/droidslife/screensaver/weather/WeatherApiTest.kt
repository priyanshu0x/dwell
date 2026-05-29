package com.droidslife.screensaver.weather

import com.droidslife.screensaver.settings.SecretStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeatherApiTest {
    @Test
    fun nonSuccessResponseMapsStatusAndWeatherApiPayload() = runTest {
        val api = WeatherApi(
            client = mockClient(
                status = HttpStatusCode.Unauthorized,
                body = """{"error":{"code":2006,"message":"API key provided is invalid"}}""",
            ),
            secretStorage = fixedSecretStorage("bad-key"),
        )

        val error = assertFailsWith<WeatherApiException> {
            api.getWeatherDataByCity("London")
        }

        assertEquals(WeatherApiFailure.Unauthorized, error.failure)
        assertEquals(401, error.httpStatusCode)
        assertEquals(2006, error.upstreamCode)
        assertEquals("API key provided is invalid", error.upstreamMessage)
        assertNotNull(error.message).also {
            assertTrue(error.message!!.contains("Failed to load weather for London"))
        }
    }

    @Test
    fun rateLimitedResponseMapsStatusAndWeatherApiPayload() = runTest {
        val api = WeatherApi(
            client = mockClient(
                status = HttpStatusCode.TooManyRequests,
                body = """{"error":{"code":2007,"message":"API key has exceeded calls per month"}}""",
            ),
            secretStorage = fixedSecretStorage("key"),
        )

        val error = assertFailsWith<WeatherApiException> {
            api.getWeatherDataByCity("London")
        }

        assertEquals(WeatherApiFailure.RateLimited, error.failure)
        assertEquals(429, error.httpStatusCode)
        assertEquals(2007, error.upstreamCode)
        assertEquals("API key has exceeded calls per month", error.upstreamMessage)
    }

    @Test
    fun invalidSuccessPayloadMapsToSchemaFailure() = runTest {
        val api = WeatherApi(
            client = mockClient(status = HttpStatusCode.OK, body = """{"unexpected":true}"""),
            secretStorage = fixedSecretStorage("key"),
        )

        val error = assertFailsWith<WeatherApiException> {
            api.getWeatherDataByCity("London")
        }

        assertTrue(error.failure is WeatherApiFailure.Schema)
    }

    @Test
    fun serverErrorResponseMapsToTransientFailure() = runTest {
        val api = WeatherApi(
            client = mockClient(
                status = HttpStatusCode.ServiceUnavailable,
                body = """{"error":{"message":"upstream down"}}""",
            ),
            secretStorage = fixedSecretStorage("key"),
        )

        val error = assertFailsWith<WeatherApiException> {
            api.getWeatherDataByCity("London")
        }

        assertTrue(error.failure is WeatherApiFailure.Transient)
        assertEquals(503, error.httpStatusCode)
    }

    @Test
    fun ktorIoFailureMapsToTransientFailure() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { throw IOException("socket closed") }
            }
        }
        val api = WeatherApi(client = client, secretStorage = fixedSecretStorage("key"))

        val error = assertFailsWith<WeatherApiException> {
            api.getWeatherDataByCity("London")
        }

        assertTrue(error.failure is WeatherApiFailure.Transient)
    }

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
            }
            engine {
                addHandler {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

    private fun fixedSecretStorage(value: String): SecretStorage =
        object : SecretStorage {
            override suspend fun read(id: String): String? = value
            override suspend fun write(id: String, value: String) {}
            override suspend fun delete(id: String) {}
        }
}
