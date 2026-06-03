package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.calendar.CalendarMath
import com.droidslife.screensaver.calendar.providers.CalendarEvent
import com.droidslife.screensaver.calendar.providers.CalendarProvider
import com.droidslife.screensaver.calendar.providers.CalendarSyncStatus
import com.droidslife.screensaver.calendar.providers.IcsCalendarProvider
import com.droidslife.screensaver.components.WidgetStatusLine
import com.droidslife.screensaver.components.WidgetStatusSeverity
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.modes.console.consoleNestedSurfaceColor
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.openLink
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val WIDGET_ID = "com.droidslife.screensaver.calendar"
private const val PROVIDER_NONE = "none"

class CalendarWidgetFactory : WidgetFactory {
    override val id: String = WIDGET_ID
    override val displayName: String = "Calendar"
    override val description: String = "Month grid with events from an ICS feed (Google Cal, Outlook, PagerDuty, …)"
    override val category: WidgetCategory = WidgetCategory.PRODUCTIVITY
    override val preferredSize: WidgetSize = WidgetSize(
        // Allow the strip (7×1) and the timeline (2×5) layouts in addition to the
        // default month grid; Content() chooses by measured size at runtime.
        minCols = 2, minRows = 1,
        defaultCols = 4, defaultRows = 3,
        maxCols = 8, maxRows = 6,
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Enum(
            key = "provider",
            label = "Event source",
            options = listOf(
                ConfigField.EnumOption(PROVIDER_NONE, "None (month grid only)"),
                ConfigField.EnumOption(IcsCalendarProvider.ID, "ICS URL"),
            ),
            default = PROVIDER_NONE,
            help = "Paste any iCalendar (.ics) URL — Google Calendar's secret address, Outlook's published URL, PagerDuty schedules, etc.",
        ),
        ConfigField.Text(
            key = "icsUrl",
            label = "ICS feed URL",
            placeholder = "https://calendar.google.com/calendar/ical/.../basic.ics",
            help = "Used only when the source above is set to ICS URL.",
        ),
        ConfigField.DurationChoice(
            key = "refreshInterval",
            label = "Refresh every",
            options = listOf(
                ConfigField.DurationOption("5m", "5 minutes"),
                ConfigField.DurationOption("15m", "15 minutes"),
                ConfigField.DurationOption("1h", "1 hour"),
            ),
            default = "15m",
        ),
        ConfigField.Bool(
            key = "heatmap",
            label = "Free/busy heatmap",
            default = true,
            help = "Tint each day by how packed it is with meetings.",
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget =
        CalendarWidget(config, scope)
}

private class CalendarWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
) : Widget {

    private val statusFlow = MutableStateFlow<CalendarSyncStatus>(CalendarSyncStatus.Healthy)
    private val provider: CalendarProvider? = run {
        val pick = config.string("provider").ifBlank { PROVIDER_NONE }
        when (pick) {
            IcsCalendarProvider.ID -> {
                val url = config.string("icsUrl").trim()
                if (url.isBlank()) {
                    statusFlow.value = CalendarSyncStatus.Unconfigured("Paste an ICS URL in widget settings")
                    null
                } else {
                    IcsCalendarProvider(
                        http = scope.httpClient,
                        url = url,
                        refreshIntervalMs = config.durationMillis("refreshInterval", default = 15.minutes.inWholeMilliseconds),
                        scope = scope.coroutineScope,
                        log = scope.log,
                        storage = scope.storage,
                    )
                }
            }
            else -> null
        }
    }

    private val heatmapEnabled: Boolean = config.bool("heatmap", default = true)

    /**
     * Cache of the latest snapshot, **sorted by start**, so [summary] (called
     * outside composition by chip / minimal renderers) can grab the next
     * event with a single `firstOrNull` without re-sorting on every call.
     * Sort is applied once at the LaunchedEffect boundary because
     * [CalendarProvider.watch] explicitly disclaims ordering.
     */
    private val summaryCache = MutableStateFlow<List<CalendarEvent>>(emptyList())

    override fun summary(): WidgetSummary {
        val today = todayLocal()
        val nowDt = nowLocalDateTime()
        val next = summaryCache.value.firstOrNull { it.isUpcomingFrom(nowDt) && it.startDate <= today.plus(1, DateTimeUnit.DAY) }
        return if (next != null) {
            WidgetSummary(
                primaryValue = next.shortCountdownFrom(nowDt),
                primaryLabel = "until",
                subtitle = next.title.take(40).ifBlank { "Next event" },
            )
        } else {
            WidgetSummary(
                primaryValue = today.day.toString(),
                primaryLabel = monthShortName(today.month),
                subtitle = today.year.toString(),
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val today = todayLocal()
        val nowDt = nowLocalDateTime()
        val rawEvents by (provider?.watch() ?: emptyEvents()).collectAsState(initial = remember { summaryCache.value })
        val sync by (provider?.syncStatus() ?: emptyStatus()).collectAsState(initial = statusFlow.value)

        // Single sort boundary — providers don't promise ordering per the
        // contract, so we normalize once here and feed sorted events to every
        // downstream composable AND the off-composition summary cache.
        val events = remember(rawEvents) {
            rawEvents.sortedBy { it.start ?: it.startDate.atTime(0, 0) }
        }

        LaunchedEffect(events) { summaryCache.value = events }

        val (statusMessage, statusSeverity) = when (val s = sync) {
            is CalendarSyncStatus.Offline -> s.message to WidgetStatusSeverity.Warning
            is CalendarSyncStatus.Unconfigured -> s.message to WidgetStatusSeverity.Info
            CalendarSyncStatus.Healthy -> null to WidgetStatusSeverity.Info
        }

        val accent = LocalConsoleAccent.current.primary
        // Use ordinal + 1 to derive the 1-based month — Month.number landed
        // later than this project's kotlinx-datetime version.
        val monthNumber = today.month.ordinal + 1

        val countsByDay: Map<LocalDate, Int> = remember(events, monthNumber, today.year) {
            CalendarMath.countsByDay(events, today.year, monthNumber)
        }
        val heatByDay: Map<LocalDate, Int> = remember(events, monthNumber, today.year, heatmapEnabled) {
            if (heatmapEnabled) CalendarMath.busyMinutesByDay(events, today.year, monthNumber) else emptyMap()
        }
        // First-event URL per day so a click on a day cell opens that day's
        // earliest event in the source calendar. Days with no event aren't
        // clickable — we don't know what generic "open the calendar" link to
        // use without knowing the source provider.
        val firstUrlByDay: Map<LocalDate, String> = remember(events) {
            events
                .filter { it.url.isNotBlank() }
                .groupBy { it.startDate }
                .mapValues { (_, list) -> list.first().url }
        }
        val upcoming = events.filter { it.isUpcomingFrom(nowDt) }
            .sortedBy { it.start ?: it.startDate.atTime(0, 0) }

        // Only offer manual refresh when there's a remote provider to re-fetch.
        val onRefresh: (() -> Unit)? = provider?.let { p -> { p.refresh() } }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            // The tile system gives us actual pixels; pick a layout by aspect.
            // These breakpoints leave the month grid as the comfortable default
            // and only switch when the tile genuinely isn't grid-shaped.
            val variant = when {
                h < 110.dp -> CalendarLayout.WEEK_STRIP
                w < 200.dp && h >= 280.dp -> CalendarLayout.TODAY_TIMELINE
                else -> CalendarLayout.MONTH_GRID
            }
            when (variant) {
                CalendarLayout.WEEK_STRIP -> WeekStripContent(
                    today = today,
                    nowDt = nowDt,
                    events = events,
                    countsByDay = countsByDay,
                    accent = accent,
                    onRefresh = onRefresh,
                    statusMessage = statusMessage,
                    statusSeverity = statusSeverity,
                )
                CalendarLayout.TODAY_TIMELINE -> TodayTimelineContent(
                    today = today,
                    nowDt = nowDt,
                    // Pass the full event list so past-today events still show
                    // up in the timeline (dimmed) — otherwise a 9 a.m. standup
                    // disappears at 9:16 and the day reads emptier than it was.
                    events = events,
                    accent = accent,
                    onRefresh = onRefresh,
                    statusMessage = statusMessage,
                    statusSeverity = statusSeverity,
                )
                CalendarLayout.MONTH_GRID -> MonthGridContent(
                    today = today,
                    nowDt = nowDt,
                    upcoming = upcoming,
                    countsByDay = countsByDay,
                    heatByDay = heatByDay,
                    firstUrlByDay = firstUrlByDay,
                    accent = accent,
                    onRefresh = onRefresh,
                    statusMessage = statusMessage,
                    statusSeverity = statusSeverity,
                )
            }
        }
    }

    private fun emptyEvents() = kotlinx.coroutines.flow.flowOf<List<CalendarEvent>>(emptyList())
    private fun emptyStatus() = statusFlow
}

private enum class CalendarLayout { MONTH_GRID, WEEK_STRIP, TODAY_TIMELINE }

/**
 * Shared widget header for every calendar layout. Renders [label] and, when
 * [onRefresh] is non-null (a remote provider is configured), a small refresh
 * button so the user doesn't have to wait out the poll interval after fixing
 * something upstream.
 */
@Composable
private fun CalendarHeader(label: String, onRefresh: (() -> Unit)?) {
    WidgetHeader(label = label, settingsId = WIDGET_ID) {
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(18.dp)) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh calendar",
                    tint = DwellColors.TextLow,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

// region — Month grid (default)

@Composable
private fun MonthGridContent(
    today: LocalDate,
    nowDt: LocalDateTime,
    upcoming: List<CalendarEvent>,
    countsByDay: Map<LocalDate, Int>,
    heatByDay: Map<LocalDate, Int>,
    firstUrlByDay: Map<LocalDate, String>,
    accent: Color,
    onRefresh: (() -> Unit)?,
    statusMessage: String?,
    statusSeverity: WidgetStatusSeverity,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarHeader(label = "${monthShortName(today.month).uppercase()} ${today.year}", onRefresh = onRefresh)
        Spacer(Modifier.height(4.dp))
        MonthGrid(today = today, countsByDay = countsByDay, heatByDay = heatByDay, firstUrlByDay = firstUrlByDay, accent = accent)
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            UpcomingList(events = upcoming, today = today, nowDt = nowDt, accent = accent, max = 3)
        }
        WidgetStatusLine(statusMessage, severity = statusSeverity)
    }
}

@Composable
private fun MonthGrid(
    today: LocalDate,
    countsByDay: Map<LocalDate, Int>,
    heatByDay: Map<LocalDate, Int>,
    firstUrlByDay: Map<LocalDate, String>,
    accent: Color,
) {
    val firstOfMonth = LocalDate(today.year, today.month, 1)
    val leadingBlanks = sundayFirstLeadingBlanks(firstOfMonth.dayOfWeek)
    val daysInMonth = daysInMonth(today.year, today.month)
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7
    val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekdayLabels.forEach { label ->
                Box(modifier = Modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = DwellColors.TextLow,
                        fontFamily = DwellFonts.interTight(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = LocalDate(today.year, today.month, dayNumber)
                        DayCell(
                            day = dayNumber,
                            isToday = dayNumber == today.day,
                            eventCount = countsByDay[date] ?: 0,
                            heatAlpha = CalendarMath.heatAlpha(heatByDay[date] ?: 0),
                            openUrl = firstUrlByDay[date].orEmpty(),
                            accent = accent,
                        )
                    } else {
                        Box(modifier = Modifier.padding(horizontal = 2.dp).padding(vertical = 2.dp)) {
                            Text(text = " ", fontSize = 11.sp, color = DwellColors.TextFaint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    eventCount: Int,
    heatAlpha: Float,
    openUrl: String,
    accent: Color,
) {
    // Today wins the visual emphasis: a stronger tint than the heatmap would
    // otherwise apply to that single cell, so it stays anchored.
    val bg = when {
        isToday -> accent.copy(alpha = 0.18f)
        heatAlpha > 0f -> accent.copy(alpha = heatAlpha)
        else -> Color.Transparent
    }
    val textColor = if (isToday) accent else DwellColors.TextFaint
    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .openLinkOnClick(openUrl)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = day.toString(),
            fontSize = 11.sp,
            color = textColor,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
        )
        EventDots(count = eventCount, accent = accent)
    }
}

@Composable
private fun EventDots(count: Int, accent: Color) {
    Row(
        modifier = Modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count <= 0) {
            Spacer(Modifier.width(0.dp))
        } else {
            val dots = count.coerceAtMost(3)
            val alpha = if (count > 3) 1f else 0.85f
            repeat(dots) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = alpha)),
                )
            }
        }
    }
}

// endregion

// region — Week strip (short + wide)

@Composable
private fun WeekStripContent(
    today: LocalDate,
    nowDt: LocalDateTime,
    events: List<CalendarEvent>,
    countsByDay: Map<LocalDate, Int>,
    accent: Color,
    onRefresh: (() -> Unit)?,
    statusMessage: String?,
    statusSeverity: WidgetStatusSeverity,
) {
    // Show today plus the next 6 days. Anchoring at today (instead of the
    // start of the week) keeps the most actionable cells leftmost.
    val days = (0 until 7).map { today.plus(it, DateTimeUnit.DAY) }
    // For each day: the earliest upcoming event (used for the title + click-
    // through), and the total count of upcoming events so the cell can show a
    // "+N" overflow hint when the day has more than one.
    val byDay = events
        .filter { it.isUpcomingFrom(nowDt) && it.startDate in days.first()..days.last() }
        .groupBy { it.startDate }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CalendarHeader(label = "NEXT 7 DAYS", onRefresh = onRefresh)
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { d ->
                // events here are already widget-sorted by start, so first() is
                // the earliest of the day for the click-through target.
                val dayEvents = byDay[d].orEmpty()
                val first = dayEvents.firstOrNull()
                StripCell(
                    date = d,
                    isToday = d == today,
                    eventCount = countsByDay[d] ?: 0,
                    firstEvent = first,
                    firstEventTitle = first?.title,
                    firstEventUrl = first?.url.orEmpty(),
                    extraUpcoming = (dayEvents.size - 1).coerceAtLeast(0),
                    accent = accent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        WidgetStatusLine(statusMessage, severity = statusSeverity)
    }
}

@Composable
private fun StripCell(
    date: LocalDate,
    isToday: Boolean,
    eventCount: Int,
    firstEvent: CalendarEvent?,
    firstEventTitle: String?,
    firstEventUrl: String,
    extraUpcoming: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val bg = if (isToday) {
        consoleNestedSurfaceColor(accent.copy(alpha = 0.16f))
    } else {
        consoleNestedSurfaceColor(DwellColors.Surface1)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .openLinkOnClick(firstEventUrl)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = weekdayShort(date.dayOfWeek).uppercase(),
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 8.sp,
            letterSpacing = 1.sp,
            color = if (isToday) accent else DwellColors.TextLow,
        )
        Text(
            text = date.day.toString(),
            fontFamily = DwellFonts.jetBrainsMono(),
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
            color = if (isToday) accent else DwellColors.TextHigh,
        )
        EventDots(count = eventCount, accent = accent)
        if (!firstEventTitle.isNullOrBlank()) {
            val titleText: @Composable () -> Unit = {
                Text(
                    text = firstEventTitle,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 9.sp,
                    color = DwellColors.TextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (firstEvent != null) EventTooltip(event = firstEvent) { titleText() } else titleText()
        }
        // Lets a 5-meetings-Monday read differently from a 1-meeting-Monday
        // even after the dots cap kicks in at 3.
        if (extraUpcoming > 0) {
            Text(
                text = "+$extraUpcoming more",
                fontFamily = DwellFonts.interTight(),
                fontSize = 8.sp,
                color = DwellColors.TextFaint,
                maxLines = 1,
            )
        }
    }
}

// endregion

// region — Today timeline (tall + narrow)

@Composable
private fun TodayTimelineContent(
    today: LocalDate,
    nowDt: LocalDateTime,
    events: List<CalendarEvent>,
    accent: Color,
    onRefresh: (() -> Unit)?,
    statusMessage: String?,
    statusSeverity: WidgetStatusSeverity,
) {
    // Anchoring on startDate == today (not on isUpcomingFrom) keeps already-
    // ended meetings in the timeline so the user can scroll up and see the
    // full shape of the day. They render dimmed so attention stays on what's
    // current/next.
    val todays = events
        .filter { it.startDate == today }
        .sortedBy { it.start ?: it.startDate.atTime(0, 0) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarHeader(
            label = "TODAY · ${monthShortName(today.month).uppercase()} ${today.day}",
            onRefresh = onRefresh,
        )
        if (todays.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No events today",
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                    color = DwellColors.TextLow,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                todays.forEach { e ->
                    TimelineRow(event = e, nowDt = nowDt, accent = accent)
                }
            }
        }
        WidgetStatusLine(statusMessage, severity = statusSeverity)
    }
}

@Composable
private fun TimelineRow(event: CalendarEvent, nowDt: LocalDateTime, accent: Color) {
    val timeLabel = when {
        event.allDay -> "ALL DAY"
        event.start != null -> "${event.start.time.hour.toString().padStart(2, '0')}:${event.start.time.minute.toString().padStart(2, '0')}"
        else -> "—"
    }
    val isLive = event.start != null && event.end != null &&
        nowDt in event.start..event.end
    // "Past" = an event whose end (or start, if no end) is before now today.
    // All-day events never go "past" while the day is still today.
    val isPast = !event.allDay && !isLive && run {
        val end = event.end ?: event.start
        end != null && end < nowDt
    }
    val barColor = when {
        isLive -> accent
        isPast -> DwellColors.TextFaint
        else -> accent.copy(alpha = 0.5f)
    }
    val timeColor = when {
        isLive -> accent
        isPast -> DwellColors.TextFaint
        else -> DwellColors.TextMid
    }
    val titleColor = if (isPast) DwellColors.TextLow else DwellColors.TextHigh
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isLive) accent.copy(alpha = 0.10f) else Color.Transparent)
            .openLinkOnClick(event.url)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(barColor),
        )
        Text(
            text = timeLabel,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontSize = 10.sp,
            color = timeColor,
            modifier = Modifier.width(46.dp),
        )
        EventTooltip(event = event, modifier = Modifier.weight(1f)) {
            Text(
                text = event.title.ifBlank { "(no title)" },
                fontFamily = DwellFonts.interTight(),
                fontSize = 12.sp,
                color = titleColor,
                textDecoration = if (isPast) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// endregion

// region — Shared upcoming list (under month grid)

@Composable
private fun UpcomingList(
    events: List<CalendarEvent>,
    today: LocalDate,
    nowDt: LocalDateTime,
    accent: Color,
    max: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        events.take(max).forEach { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .openLinkOnClick(e.url),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.85f)),
                )
                Text(
                    text = e.shortWhenLabel(today, nowDt),
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontSize = 9.sp,
                    color = DwellColors.TextLow,
                    modifier = Modifier.width(58.dp),
                    maxLines = 1,
                )
                EventTooltip(event = e, modifier = Modifier.weight(1f)) {
                    Text(
                        text = e.title.ifBlank { "(no title)" },
                        fontFamily = DwellFonts.interTight(),
                        fontSize = 11.sp,
                        color = DwellColors.TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// endregion

// region — Pure helpers

private fun sundayFirstLeadingBlanks(weekday: DayOfWeek): Int = when (weekday) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
}

private fun daysInMonth(year: Int, month: Month): Int {
    val first = LocalDate(year, month, 1)
    val firstNext = first.plus(1, DateTimeUnit.MONTH)
    return (firstNext.toEpochDays() - first.toEpochDays()).toInt()
}

private fun monthShortName(month: Month): String = when (month) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
}

private fun weekdayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun todayLocal(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun nowLocalDateTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(this, LocalTime(hour, minute))

/** True if the event has not yet finished as of [nowDt]. */
private fun CalendarEvent.isUpcomingFrom(nowDt: LocalDateTime): Boolean {
    val end = this.end ?: this.start ?: this.endDate.atTime(23, 59)
    return end >= nowDt
}

private fun CalendarEvent.shortCountdownFrom(nowDt: LocalDateTime): String {
    val start = this.start ?: this.startDate.atTime(0, 0)
    val minutes = minutesBetween(nowDt, start)
    return when {
        minutes <= 0 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

private fun CalendarEvent.shortWhenLabel(today: LocalDate, nowDt: LocalDateTime): String {
    val s = this.start
    if (this.allDay || s == null) {
        return when {
            this.startDate == today -> "today"
            this.startDate == today.plus(1, DateTimeUnit.DAY) -> "tomorrow"
            else -> "${monthShortName(this.startDate.month)} ${this.startDate.day}"
        }.uppercase()
    }
    val deltaMin = minutesBetween(nowDt, s)
    val hh = s.time.hour.toString().padStart(2, '0')
    val mm = s.time.minute.toString().padStart(2, '0')
    return when {
        deltaMin <= 0 -> "NOW"
        deltaMin < 60 -> "in ${deltaMin}m"
        s.date == today -> "$hh:$mm"
        s.date == today.plus(1, DateTimeUnit.DAY) -> "tmrw $hh:$mm"
        else -> "${monthShortName(s.date.month)} ${s.date.day}"
    }
}

private fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Int {
    val days = (b.date.toEpochDays() - a.date.toEpochDays()).toInt()
    val secA = a.time.toSecondOfDay()
    val secB = b.time.toSecondOfDay()
    return (days * 24 * 60) + (secB - secA) / 60
}

/**
 * Adds a click handler that opens [url] and switches the hover cursor to a
 * hand on platforms that support pointer icons, so a calendar row reads as
 * actionable instead of decorative. No-op when [url] is blank.
 */
private fun Modifier.openLinkOnClick(url: String): Modifier {
    if (url.isBlank()) return this
    return this
        .pointerHoverIcon(PointerIcon.Hand)
        .clickable { openLink(url) }
}

/**
 * Wraps [content] in a hover tooltip showing the event's full detail —
 * needed because every list/strip/timeline title is single-line ellipsized,
 * so a long "Quarterly business review with the leadership team" otherwise
 * reads as "Quarterly busine…". The card surfaces the full title plus a
 * time/location line on hover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventTooltip(
    event: CalendarEvent,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = { EventHoverCard(event) },
        delayMillis = 450,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.TopStart,
            alignment = Alignment.BottomStart,
            offset = DpOffset(0.dp, (-6).dp),
        ),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
private fun EventHoverCard(event: CalendarEvent) {
    val timeLine = buildString {
        if (event.allDay) {
            append("All day")
        } else if (event.start != null) {
            append("${event.start.time.hour.toString().padStart(2, '0')}:${event.start.time.minute.toString().padStart(2, '0')}")
            event.end?.let {
                append("–${it.time.hour.toString().padStart(2, '0')}:${it.time.minute.toString().padStart(2, '0')}")
            }
        }
        if (event.location.isNotBlank()) {
            if (isNotEmpty()) append("  ·  ")
            append(event.location)
        }
    }
    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DwellColors.Surface0.copy(alpha = 0.97f))
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = event.title.ifBlank { "(no title)" },
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
        if (timeLine.isNotBlank()) {
            Text(
                text = timeLine,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 10.sp,
            )
        }
    }
}

// endregion
