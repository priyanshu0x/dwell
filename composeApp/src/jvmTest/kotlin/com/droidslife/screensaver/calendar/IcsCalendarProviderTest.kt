package com.droidslife.screensaver.calendar

import com.droidslife.screensaver.calendar.providers.IcsCalendarProvider
import com.droidslife.screensaver.calendar.providers.isAllowedIcsUrl
import com.droidslife.screensaver.calendar.providers.normalizeIcsUrl
import com.droidslife.screensaver.widget.api.WidgetLogger
import com.droidslife.screensaver.widget.api.WidgetStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsCalendarProviderTest {

    // ---- #17: URL normalization + scheme allow-list -----------------------

    @Test
    fun normalizesWebcalToHttps() {
        assertEquals("https://example.com/c.ics", normalizeIcsUrl("webcal://example.com/c.ics"))
        assertEquals("https://example.com/c.ics", normalizeIcsUrl("webcals://example.com/c.ics"))
        assertEquals("https://example.com/c.ics", normalizeIcsUrl("WEBCAL://example.com/c.ics"))
        // A plain https URL is passed through untouched.
        assertEquals("https://example.com/c.ics", normalizeIcsUrl("https://example.com/c.ics"))
    }

    @Test
    fun allowsOnlyWebFeedSchemes() {
        assertTrue(isAllowedIcsUrl("https://example.com/c.ics"))
        assertTrue(isAllowedIcsUrl("http://example.com/c.ics"))
        assertTrue(isAllowedIcsUrl("webcal://example.com/c.ics"))
        assertTrue(isAllowedIcsUrl("  HTTPS://example.com/c.ics  "))
        assertEquals(false, isAllowedIcsUrl("file:///etc/passwd"))
        assertEquals(false, isAllowedIcsUrl("jar:file:///x"))
        assertEquals(false, isAllowedIcsUrl("javascript:alert(1)"))
        assertEquals(false, isAllowedIcsUrl("/local/path.ics"))
    }

    // ---- #18: cache round-trip --------------------------------------------

    @Test
    fun coldStartServesCachedBodyBeforeFirstFetch() = runTest {
        val url = "https://example.com/cal.ics"
        // Pre-seed storage as if a prior run had cached this exact URL's body.
        val storage = FakeStorage().apply {
            map["ics-cache-url.txt"] = url
            map["ics-cache-body.txt"] = SAMPLE_ICS
        }
        // The HTTP layer hangs forever, so the only events that can appear come
        // from the cache — isolating the cold-start cache path from the fetch.
        val hangingHttp = HttpClient(MockEngine { awaitCancellation() })

        val provider = IcsCalendarProvider(
            http = hangingHttp,
            url = url,
            refreshIntervalMs = 60_000,
            scope = backgroundScope,
            log = NoopLogger,
            storage = storage,
            now = { LocalDate(2026, 6, 1) },
        )

        // First non-empty emission must be the cached parse.
        val events = provider.watch().first { it.isNotEmpty() }
        assertTrue(events.any { it.title == "Cached event" })
    }

    @Test
    fun coldStartIgnoresCacheFromADifferentUrl() = runTest {
        val storage = FakeStorage()
        // Cache was written for a *different* URL than we're now configured for.
        storage.map["ics-cache-url.txt"] = "https://old.example.com/other.ics"
        storage.map["ics-cache-body.txt"] = SAMPLE_ICS

        // Network hangs, so if any event appears it could only have come from
        // the (mismatched) cache — which must be rejected, leaving it empty.
        val provider = IcsCalendarProvider(
            http = HttpClient(MockEngine { awaitCancellation() }),
            url = "https://new.example.com/cal.ics",
            refreshIntervalMs = 60_000,
            scope = backgroundScope,
            log = NoopLogger,
            storage = storage,
            now = { LocalDate(2026, 6, 1) },
        )

        val first = provider.watch().first()
        assertTrue(first.isEmpty())
    }

    private companion object {
        val SAMPLE_ICS = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:cached@example.com
            DTSTART;VALUE=DATE:20260615
            SUMMARY:Cached event
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
    }
}

private object NoopLogger : WidgetLogger {
    override fun info(msg: String) {}
    override fun warn(msg: String, error: Throwable?) {}
    override fun error(msg: String, error: Throwable?) {}
}

private class FakeStorage : WidgetStorage {
    val map = mutableMapOf<String, String>()

    fun blockingPut(key: String, value: String) {
        map[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> read(key: String, type: Class<T>): T? = map[key] as T?

    override suspend fun <T : Any> write(key: String, value: T) {
        map[key] = value as String
    }

    override suspend fun delete(key: String) {
        map.remove(key)
    }
}
