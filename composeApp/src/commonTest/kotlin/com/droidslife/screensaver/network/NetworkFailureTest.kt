package com.droidslife.screensaver.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFailureTest {
    @Test
    fun treatsCoroutineTimeoutAsTransientNetworkFailure() = runTest {
        val error = kotlin.test.assertFailsWith<TimeoutCancellationException> {
            withTimeout(1) {
                delay(10_000)
            }
        }

        assertTrue(error.isTransientNetworkFailure())
        assertEquals(NetworkFailureKind.Timeout, error.transientNetworkFailureKind())
        assertEquals("Todoist request timed out", error.networkFailureSummary("Todoist"))
    }

    @Test
    fun doesNotTreatRegularCancellationAsNetworkFailure() {
        val error = CancellationException("widget disposed")

        assertFalse(error.isTransientNetworkFailure())
    }

    @Test
    fun unwrapsNestedIoFailure() {
        val error = RuntimeException("provider failed", IOException("connection reset"))

        assertTrue(error.isTransientNetworkFailure())
        assertEquals(NetworkFailureKind.Io, error.transientNetworkFailureKind())
        assertEquals("Calendar network request failed", error.networkFailureSummary("Calendar"))
    }
}
