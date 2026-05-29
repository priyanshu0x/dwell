package com.droidslife.screensaver.calendar

import com.droidslife.screensaver.calendar.providers.CalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Minimal RFC 5545 parser, scoped to what a dashboard tile actually needs:
 * VEVENT only, `DTSTART` / `DTEND` / `SUMMARY` / `LOCATION`, plus a small
 * RRULE expander for `FREQ=DAILY|WEEKLY|MONTHLY|YEARLY` with `INTERVAL`,
 * `UNTIL`, `COUNT`, and (for WEEKLY) `BYDAY`.
 *
 * Out of scope on purpose:
 *  - VTIMEZONE — we read TZID as a hint but render every time as wall-clock
 *    in the user's current zone. Cross-zone meetings drift by the TZ offset
 *    rather than being shifted; documented limitation, fine for "what's next?"
 *  - EXDATE / RDATE — exceptions are ignored; expanded occurrences may include
 *    a date the source marked as cancelled.
 *  - Complex BY* expansions (BYMONTHDAY etc.) — not supported.
 *
 * The parser is deliberately tolerant: malformed lines are skipped, an
 * unparseable DTSTART drops the VEVENT, and the rest of the calendar
 * continues. Returning empty on a corrupt feed is better than crashing the
 * widget.
 */
object IcsParser {

    /**
     * Parse an ICS document into expanded events covering the half-open window
     * `[windowStart, windowEnd)`. Non-recurring events outside the window are
     * dropped; recurring series are expanded into individual occurrences and
     * any occurrence whose start falls in the window is kept.
     */
    fun parse(
        text: String,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<CalendarEvent> {
        val lines = unfold(text)
        val events = mutableListOf<CalendarEvent>()

        var inEvent = false
        var props = mutableMapOf<String, Pair<Map<String, String>, String>>()

        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VEVENT" -> {
                    inEvent = true
                    props = mutableMapOf()
                }
                upper == "END:VEVENT" -> {
                    if (inEvent) {
                        val expanded = runCatching { buildEvents(props, windowStart, windowEnd) }
                            .getOrDefault(emptyList())
                        events.addAll(expanded)
                    }
                    inEvent = false
                }
                inEvent -> {
                    val (name, params, value) = splitContentLine(line) ?: continue
                    // Last write wins for repeated keys; sufficient for our scope.
                    props[name] = params to value
                }
            }
        }

        return events.sortedBy { it.start ?: it.startDate.atTime(0, 0) }
    }

    /**
     * RFC 5545 §3.1 line unfolding: a continuation line begins with a single
     * space or tab; that prefix is dropped and the line is concatenated to the
     * preceding one. Both CRLF and LF are accepted.
     */
    internal fun unfold(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<String>()
        for (line in raw) {
            if (line.isEmpty()) continue
            val first = line[0]
            if ((first == ' ' || first == '\t') && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + line.substring(1)
            } else {
                out.add(line)
            }
        }
        return out
    }

    /**
     * Splits `NAME;PARAM=VAL;PARAM2=VAL2:value` into name, param map, value.
     * Returns null when the line has no colon (and so isn't a content line).
     */
    private fun splitContentLine(line: String): Triple<String, Map<String, String>, String>? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(';')
        val name = parts[0].uppercase()
        val params = parts.drop(1).associate { p ->
            val eq = p.indexOf('=')
            if (eq < 0) p.uppercase() to "" else p.substring(0, eq).uppercase() to p.substring(eq + 1)
        }
        return Triple(name, params, value)
    }

    private fun buildEvents(
        props: Map<String, Pair<Map<String, String>, String>>,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<CalendarEvent> {
        val (dtStartParams, dtStartValue) = props["DTSTART"] ?: return emptyList()
        val dtEndPair = props["DTEND"]
        val summary = props["SUMMARY"]?.second?.let(::unescape).orEmpty()
        val location = props["LOCATION"]?.second?.let(::unescape).orEmpty()
        val uid = props["UID"]?.second ?: "ics-${summary.hashCode()}-$dtStartValue"

        val start = parseDateOrDateTime(dtStartValue, dtStartParams) ?: return emptyList()
        val end = dtEndPair?.let { parseDateOrDateTime(it.second, it.first) }
        val rrule = props["RRULE"]?.second

        // Duration is preserved when expanding recurrences so a 30-minute slot
        // stays 30 minutes on every occurrence — not a property of the rule.
        val durationDays = if (start.allDay && end != null && end.allDay) {
            // ICS all-day DTEND is exclusive — subtract 1 to get inclusive last day.
            (end.date.toEpochDays() - start.date.toEpochDays()).toInt().coerceAtLeast(0) - 1
        } else 0

        val starts: List<DateOrDateTime> = if (rrule != null) {
            expandRrule(start, rrule, windowStart, windowEnd)
        } else if (inWindow(start.date, windowStart, windowEnd)) {
            listOf(start)
        } else {
            emptyList()
        }

        return starts.map { occStart ->
            val occEnd = end?.let {
                if (rrule != null) shiftEndToOccurrence(start, occStart, it) else it
            }
            occurrenceToEvent(
                seriesId = uid,
                start = occStart,
                end = occEnd,
                durationDays = durationDays,
                summary = summary,
                location = location,
            )
        }
    }

    private fun occurrenceToEvent(
        seriesId: String,
        start: DateOrDateTime,
        end: DateOrDateTime?,
        durationDays: Int,
        summary: String,
        location: String,
    ): CalendarEvent {
        val id = "$seriesId::${start.date}"
        val endDate = end?.date?.let {
            if (start.allDay) it.minus(1, DateTimeUnit.DAY) else it
        } ?: start.date.plus(durationDays.coerceAtLeast(0), DateTimeUnit.DAY)
        return CalendarEvent(
            id = id,
            title = summary,
            location = location,
            startDate = start.date,
            endDate = if (endDate < start.date) start.date else endDate,
            start = if (start.allDay) null else start.dateTime,
            end = if (start.allDay) null else end?.dateTime,
            allDay = start.allDay,
        )
    }

    /**
     * Carry the original timed duration onto each occurrence's end. For
     * all-day series the parent end is already aligned by the durationDays
     * path, so this just returns it.
     */
    private fun shiftEndToOccurrence(
        start: DateOrDateTime,
        occStart: DateOrDateTime,
        end: DateOrDateTime,
    ): DateOrDateTime {
        if (start.allDay || end.allDay) return end
        val startMs = start.dateTime!!.toMillis()
        val endMs = end.dateTime!!.toMillis()
        val deltaMs = (endMs - startMs).coerceAtLeast(0)
        val newEnd = LocalDateTime.fromEpochMillis(occStart.dateTime!!.toMillis() + deltaMs)
        return DateOrDateTime(date = newEnd.date, dateTime = newEnd, allDay = false)
    }

    /**
     * Expand a recurring series into concrete starts. We bound the expansion
     * with both [windowEnd] and a hard cap of 366 occurrences as a safety net
     * for unbounded rules.
     */
    internal fun expandRrule(
        seed: DateOrDateTime,
        rrule: String,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<DateOrDateTime> {
        val parts = rrule.split(';').associate {
            val eq = it.indexOf('=')
            if (eq < 0) it.uppercase() to "" else it.substring(0, eq).uppercase() to it.substring(eq + 1)
        }
        val freq = parts["FREQ"] ?: return listOf(seed).filter { inWindow(it.date, windowStart, windowEnd) }
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { parseUntil(it) }
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull(::weekdayFromIcs)?.toSet() ?: emptySet()

        val out = mutableListOf<DateOrDateTime>()
        var cursor = seed
        var emitted = 0
        val hardCap = 366

        fun shouldStop(occ: DateOrDateTime): Boolean {
            if (occ.date >= windowEnd) return true
            if (until != null) {
                // Compare the occurrence's wall-clock against UNTIL. For all-day
                // occurrences treat them as midnight at the start of the day.
                val occMs = (occ.dateTime ?: LocalDateTime(occ.date, LocalTime(0, 0))).toMillis()
                if (occMs > until.toMillis()) return true
            }
            if (count != null && emitted >= count) return true
            return false
        }

        fun emit(occ: DateOrDateTime) {
            if (inWindow(occ.date, windowStart, windowEnd)) {
                out += occ
            }
            emitted++
        }

        var safety = 0
        while (!shouldStop(cursor) && safety < hardCap) {
            safety++
            when (freq.uppercase()) {
                "DAILY" -> {
                    emit(cursor)
                    cursor = cursor.shiftDays(interval)
                }
                "WEEKLY" -> {
                    if (byDay.isEmpty()) {
                        emit(cursor)
                        cursor = cursor.shiftDays(7 * interval)
                    } else {
                        // For BYDAY rules we walk forward one week at a time
                        // from the seed and emit each listed weekday in order.
                        // Apply the seed's own weekday first time around.
                        val weekStart = startOfIsoWeek(cursor.date)
                        for (offset in 0..6) {
                            val d = weekStart.plus(offset, DateTimeUnit.DAY)
                            if (d.dayOfWeek in byDay && d >= seed.date) {
                                val occ = cursor.withDate(d)
                                if (shouldStop(occ)) return out
                                emit(occ)
                            }
                        }
                        cursor = cursor.shiftDays(7 * interval)
                    }
                }
                "MONTHLY" -> {
                    emit(cursor)
                    cursor = cursor.shiftMonths(interval)
                }
                "YEARLY" -> {
                    emit(cursor)
                    cursor = cursor.shiftYears(interval)
                }
                else -> return out
            }
        }
        return out
    }

    private fun inWindow(date: LocalDate, start: LocalDate, end: LocalDate): Boolean {
        return date in start..<end
    }

    /**
     * UNTIL may be `YYYYMMDD` (date) or `YYYYMMDDTHHMMSS[Z]` (date-time). We
     * project both onto a wall-clock LocalDateTime so the expander can
     * compare against the occurrence's full datetime — a date-only UNTIL
     * lands at end-of-day so the day itself is included.
     */
    private fun parseUntil(value: String): LocalDateTime? {
        val datePart = value.take(8)
        if (datePart.length != 8) return null
        val date = runCatching {
            LocalDate(
                year = datePart.substring(0, 4).toInt(),
                monthNumber = datePart.substring(4, 6).toInt(),
                dayOfMonth = datePart.substring(6, 8).toInt(),
            )
        }.getOrNull() ?: return null
        val rest = value.removeSuffix("Z").drop(8) // either "" or "THHMMSS"
        if (rest.isEmpty()) {
            return LocalDateTime(date, LocalTime(23, 59, 59))
        }
        val timePart = rest.removePrefix("T")
        return runCatching {
            val h = timePart.substring(0, 2).toInt()
            val m = timePart.substring(2, 4).toInt()
            val s = if (timePart.length >= 6) timePart.substring(4, 6).toInt() else 0
            LocalDateTime(date, LocalTime(h, m, s))
        }.getOrNull() ?: LocalDateTime(date, LocalTime(23, 59, 59))
    }

    private fun weekdayFromIcs(token: String): DayOfWeek? {
        // BYDAY tokens may be prefixed with a position (e.g. "1MO"); we ignore
        // the position and only honor the weekday code.
        val code = token.takeLast(2).uppercase()
        return when (code) {
            "SU" -> DayOfWeek.SUNDAY
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            else -> null
        }
    }

    private fun startOfIsoWeek(date: LocalDate): LocalDate {
        val daysFromMonday = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
            else -> 0
        }
        return date.minus(daysFromMonday, DateTimeUnit.DAY)
    }

    private fun parseDateOrDateTime(
        value: String,
        params: Map<String, String>,
    ): DateOrDateTime? {
        // VALUE=DATE → 8-char YYYYMMDD all-day anchor.
        if (params["VALUE"]?.equals("DATE", ignoreCase = true) == true || value.length == 8) {
            return runCatching {
                val d = LocalDate(
                    year = value.substring(0, 4).toInt(),
                    monthNumber = value.substring(4, 6).toInt(),
                    dayOfMonth = value.substring(6, 8).toInt(),
                )
                DateOrDateTime(date = d, dateTime = null, allDay = true)
            }.getOrNull()
        }
        // DATE-TIME forms: YYYYMMDDTHHMMSS[Z]. Trailing Z (UTC) is treated as
        // wall-clock for our purposes — see class-level note about timezones.
        return runCatching {
            val clean = value.removeSuffix("Z")
            val date = LocalDate(
                year = clean.substring(0, 4).toInt(),
                monthNumber = clean.substring(4, 6).toInt(),
                dayOfMonth = clean.substring(6, 8).toInt(),
            )
            val time = LocalTime(
                hour = clean.substring(9, 11).toInt(),
                minute = clean.substring(11, 13).toInt(),
                second = if (clean.length >= 15) clean.substring(13, 15).toInt() else 0,
            )
            DateOrDateTime(date = date, dateTime = LocalDateTime(date, time), allDay = false)
        }.getOrNull()
    }

    /**
     * RFC 5545 §3.3.11 text escaping. The set of escapes we honor is small
     * because that's the set Google and Outlook actually emit.
     */
    private fun unescape(text: String): String = text
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    /**
     * Compact carrier for "this is either a calendar date or a wall-clock
     * date-time" used inside the parser pipeline. Externally we project this
     * onto [CalendarEvent].
     */
    data class DateOrDateTime(
        val date: LocalDate,
        val dateTime: LocalDateTime?,
        val allDay: Boolean,
    ) {
        fun shiftDays(n: Int): DateOrDateTime {
            val newDate = date.plus(n, DateTimeUnit.DAY)
            val newDt = dateTime?.let { LocalDateTime(newDate, it.time) }
            return copy(date = newDate, dateTime = newDt)
        }

        fun shiftMonths(n: Int): DateOrDateTime {
            val newDate = date.plus(n, DateTimeUnit.MONTH)
            val newDt = dateTime?.let { LocalDateTime(newDate, it.time) }
            return copy(date = newDate, dateTime = newDt)
        }

        fun shiftYears(n: Int): DateOrDateTime {
            val newDate = date.plus(n, DateTimeUnit.YEAR)
            val newDt = dateTime?.let { LocalDateTime(newDate, it.time) }
            return copy(date = newDate, dateTime = newDt)
        }

        fun withDate(d: LocalDate): DateOrDateTime {
            val newDt = dateTime?.let { LocalDateTime(d, it.time) }
            return DateOrDateTime(date = d, dateTime = newDt, allDay = allDay)
        }
    }
}

private fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(this, LocalTime(hour, minute))

/**
 * Convert a LocalDateTime to UTC epoch ms by treating its fields as UTC. We
 * only use this for computing duration arithmetic during RRULE expansion;
 * crossing a DST boundary will momentarily skew a duration by an hour, which
 * is acceptable for "what's next" rendering and avoids depending on the
 * tz database from common code.
 */
private fun LocalDateTime.toMillis(): Long {
    val days = this.date.toEpochDays()
    val secondsOfDay = this.time.toSecondOfDay().toLong()
    return days * 86_400_000L + secondsOfDay * 1000L
}

private fun LocalDateTime.Companion.fromEpochMillis(millis: Long): LocalDateTime {
    val days = (millis / 86_400_000L).toInt()
    val secOfDay = ((millis % 86_400_000L) / 1000L).toInt()
    val date = LocalDate.fromEpochDays(days)
    val time = LocalTime.fromSecondOfDay(secOfDay)
    return LocalDateTime(date, time)
}
