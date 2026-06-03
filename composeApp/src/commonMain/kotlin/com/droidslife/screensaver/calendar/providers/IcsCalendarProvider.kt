package com.droidslife.screensaver.calendar.providers

import com.droidslife.screensaver.calendar.IcsParser
import com.droidslife.screensaver.network.isTransientNetworkFailure
import com.droidslife.screensaver.network.networkFailureSummary
import com.droidslife.screensaver.widget.api.WidgetLogger
import com.droidslife.screensaver.widget.api.WidgetStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Fetches an ICS feed at [url], parses it into events, and re-polls every
 * [refreshIntervalMs]. The fetched feed body is cached in [storage] so a cold
 * start renders the last-good snapshot instantly while the first refresh is
 * in flight.
 *
 * The provider trusts whoever pasted the URL: any HTTPS feed Google /
 * Outlook / PagerDuty / Opsgenie expose works, and so do raw `webcal://`
 * links once normalized to `https://`.
 *
 * Window: we expand events from 90 days ago through 365 days from now. The
 * forward year covers next-year birthdays from a yearly series; the rolling
 * past month keeps "ended an hour ago" lines from disappearing too eagerly.
 */
class IcsCalendarProvider(
    private val http: HttpClient,
    private val url: String,
    private val refreshIntervalMs: Long,
    private val scope: CoroutineScope,
    private val log: WidgetLogger,
    private val storage: WidgetStorage,
    private val now: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
) : CalendarProvider {
    override val id: String = ID
    override val displayName: String = "ICS feed"

    private val state = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val sync = MutableStateFlow<CalendarSyncStatus>(CalendarSyncStatus.Healthy)
    private var pollJob: Job? = null

    override fun watch(): Flow<List<CalendarEvent>> {
        if (url.isBlank()) {
            sync.value = CalendarSyncStatus.Unconfigured("Paste an ICS URL in widget settings")
            return state.asStateFlow()
        }
        // Reject anything that isn't an HTTP(S)/webcal feed before we hand it to
        // ktor — a pasted `file://` URL would otherwise read local files on
        // shared installs and cache the result as if it were ICS.
        if (!isAllowedUrl(url)) {
            sync.value = CalendarSyncStatus.Unconfigured("URL scheme not supported — use https:// or webcal://")
            return state.asStateFlow()
        }
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                loadCache()
                while (isActive) {
                    refreshSafely()
                    delay(refreshIntervalMs.coerceAtLeast(MIN_REFRESH_MS))
                }
            }
        }
        return state.asStateFlow()
    }

    override fun syncStatus(): Flow<CalendarSyncStatus> = sync.asStateFlow()

    private suspend fun loadCache() {
        // Only trust the cached body if it was fetched from the URL we're
        // currently configured for — otherwise on a URL change the user
        // briefly sees events from the old calendar before the first refresh
        // overwrites them.
        val cachedUrl = suspendingRunCatching { storage.read(CACHE_URL_KEY, String::class.java) }.getOrNull()
        if (cachedUrl != url) return
        val cached = suspendingRunCatching { storage.read(CACHE_BODY_KEY, String::class.java) }
            .getOrNull() ?: return
        val today = now()
        val parsed = runCatching {
            IcsParser.parse(
                text = cached,
                windowStart = today.minusDays(PAST_WINDOW_DAYS),
                windowEnd = today.plus(FUTURE_WINDOW_DAYS, DateTimeUnit.DAY),
            )
        }.getOrNull().orEmpty()
        if (parsed.isNotEmpty()) state.value = parsed
    }

    private suspend fun refreshSafely() {
        val target = normalizeUrl(url)
        try {
            val response = http.get(target)
            // Guard memory: even busy enterprise calendars rarely exceed a few
            // hundred KB. A 5MB cap rejects both runaway feeds and pathological
            // payloads aimed at exhausting the widget's process.
            val declared = response.headers["Content-Length"]?.toLongOrNull()
            if (declared != null && declared > MAX_BODY_BYTES) {
                sync.value = CalendarSyncStatus.Offline(
                    "Feed is ${declared / 1024 / 1024}MB — refusing to load (cap is ${MAX_BODY_BYTES / 1024 / 1024}MB)"
                )
                return
            }
            // NOTE: if a server omits Content-Length and streams a huge body,
            // bodyAsText() still slurps it before we can reject — we'd want a
            // streaming reader (bodyAsChannel + readRemaining) to be strict.
            // The post-read check below still keeps the parser and the cache
            // from ever touching a body that exceeds the cap.
            val body = response.bodyAsText()
            if (body.length > MAX_BODY_BYTES) {
                sync.value = CalendarSyncStatus.Offline(
                    "Feed body exceeded ${MAX_BODY_BYTES / 1024 / 1024}MB — refusing to parse"
                )
                return
            }
            if (!body.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                sync.value = CalendarSyncStatus.Offline("Feed didn't look like ICS — check the URL")
                return
            }
            val today = now()
            val parsed = IcsParser.parse(
                text = body,
                windowStart = today.minusDays(PAST_WINDOW_DAYS),
                windowEnd = today.plus(FUTURE_WINDOW_DAYS, DateTimeUnit.DAY),
            )
            state.value = parsed
            sync.value = CalendarSyncStatus.Healthy
            // Write body first, URL stamp second. A torn write between the two
            // leaves a fresh body with the previous URL stamp — loadCache
            // rejects on mismatch (fail-safe). The reverse order could leave
            // the stamp pointing at this URL while the body is still the old
            // calendar's, which would silently serve stale events.
            suspendingRunCatching { storage.write(CACHE_BODY_KEY, body) }
            suspendingRunCatching { storage.write(CACHE_URL_KEY, url) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            log.warn("ICS fetch failed; keeping cached events", t)
            sync.value = CalendarSyncStatus.Offline(
                if (t.isTransientNetworkFailure()) {
                    "${t.networkFailureSummary("Calendar")} - last known events shown"
                } else {
                    "Calendar sync issue - last known events shown"
                },
            )
        }
    }


    private fun normalizeUrl(raw: String): String {
        // Most "subscribe to calendar" links Apple/Google hand out are
        // `webcal://...` — same payload, just a different scheme used to
        // hint native calendar apps to register the feed. ktor doesn't
        // resolve webcal, so we swap it for https.
        return when {
            raw.startsWith("webcal://", ignoreCase = true) -> "https://" + raw.substring(9)
            raw.startsWith("webcals://", ignoreCase = true) -> "https://" + raw.substring(10)
            else -> raw
        }
    }

    private fun isAllowedUrl(raw: String): Boolean {
        val lower = raw.trim().lowercase()
        return lower.startsWith("https://") ||
            lower.startsWith("http://") ||
            lower.startsWith("webcal://") ||
            lower.startsWith("webcals://")
    }

    companion object {
        const val ID: String = "ics"
        private const val PAST_WINDOW_DAYS = 30
        private const val FUTURE_WINDOW_DAYS = 365
        private const val MIN_REFRESH_MS = 60_000L
        private const val MAX_BODY_BYTES = 5L * 1024 * 1024
        private const val CACHE_BODY_KEY = "ics-cache-body.txt"
        private const val CACHE_URL_KEY = "ics-cache-url.txt"
    }
}

private fun LocalDate.minusDays(n: Int): LocalDate {
    return this.plus(-n, DateTimeUnit.DAY)
}

/**
 * Cancellation-safe variant of stdlib's [runCatching]. Plain `runCatching`
 * catches [Throwable], which includes [CancellationException] — that swallows
 * coroutine cancellation and lets the loop keep running past a host-cancelled
 * scope. We rethrow cancellation so the surrounding coroutine actually stops.
 */
private inline fun <T> suspendingRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (ce: CancellationException) {
    throw ce
} catch (t: Throwable) {
    Result.failure(t)
}
