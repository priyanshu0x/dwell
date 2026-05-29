package com.droidslife.screensaver.calendar.providers

import com.droidslife.screensaver.calendar.IcsParser
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
        val cached = runCatching { storage.read(cacheKey(), String::class.java) }.getOrNull() ?: return
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
            val body = response.bodyAsText()
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
            runCatching { storage.write(cacheKey(), body) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            log.warn("ICS fetch failed for $target", t)
            sync.value = CalendarSyncStatus.Offline("Calendar offline — last known events shown")
        }
    }

    private fun cacheKey(): String {
        // Multiple ICS URLs shouldn't collide if a future user reuses storage
        // across widget instances of the same type.
        val safe = url.hashCode().toString().replace('-', '_')
        return "ics-cache-$safe.txt"
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

    companion object {
        const val ID: String = "ics"
        private const val PAST_WINDOW_DAYS = 30
        private const val FUTURE_WINDOW_DAYS = 365
        private const val MIN_REFRESH_MS = 60_000L
    }
}

private fun LocalDate.minusDays(n: Int): LocalDate {
    return this.plus(-n, DateTimeUnit.DAY)
}
