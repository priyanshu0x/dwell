package com.droidslife.screensaver.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkFailureJvmTest {
    @Test
    fun recognizesKtorRequestTimeout() {
        val error = HttpRequestTimeoutException("https://api.todoist.com/api/v1/tasks", 15_000, null)

        assertTrue(error.isTransientNetworkFailure())
        assertEquals(NetworkFailureKind.Timeout, error.transientNetworkFailureKind())
        assertEquals("Todoist request timed out", error.networkFailureSummary("Todoist"))
    }

    @Test
    fun recognizesDnsFailure() {
        val error = UnknownHostException("api.todoist.com")

        assertTrue(error.isTransientNetworkFailure())
        assertEquals(NetworkFailureKind.Dns, error.transientNetworkFailureKind())
        assertEquals("Todoist host could not be resolved", error.networkFailureSummary("Todoist"))
    }
}
