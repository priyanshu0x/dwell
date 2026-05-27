package com.droidslife.screensaver.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BackendClientTest {
    @Test
    fun pullDoesNotConvertCancellationToFailure() = runTest {
        val client = BackendClient(
            httpClient = HttpClient(),
            baseUrlProvider = { "https://example.test" },
            tokenProvider = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            client.pull("todos")
        }
    }
}
