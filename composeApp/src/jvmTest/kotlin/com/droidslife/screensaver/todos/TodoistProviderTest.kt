package com.droidslife.screensaver.todos

import com.droidslife.screensaver.todos.providers.TaskPage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the Todoist unified API v1 `GET /tasks` wire shape TodoistProvider
 * parses against. Two things changed from the retired REST v2 API and this
 * test guards both:
 *
 *  1. List responses are paginated — rows live under `results`, not at the
 *     top level (a bare array now fails to deserialize).
 *  2. Completion is the presence of a `completed_at` timestamp, not an
 *     `is_completed` boolean; creation arrives as `added_at`.
 *
 * The HTTP layer is exercised separately; here we stay at the JSON contract
 * so the test doesn't need a Ktor mock engine.
 */
class TodoistProviderTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun parsesHeadlineFieldsOutOfPaginatedResponse() {
        val page = json.decodeFromString(TaskPage.serializer(), TASKS_SAMPLE)
        assertEquals(2, page.results.size)

        val first = page.results.first()
        assertEquals("2995104339", first.id)
        assertEquals("Buy milk", first.content)
        assertEquals(null, first.completedAt) // active task → no completion stamp

        val domain = first.toDomain()
        assertEquals("Buy milk", domain.text)
        assertEquals(false, domain.done)
        // The wire string is ISO-8601 with microseconds; Instant.parse handles it.
        assertEquals(domain.createdAt, domain.updatedAt)
        // No `due` object on this task → no schedule; absent `priority` defaults to 1.
        assertEquals(1, domain.priority)
        assertNull(domain.due)
    }

    @Test
    fun parsesPriorityAndDueIntoDomain() {
        val page = json.decodeFromString(TaskPage.serializer(), TASKS_SAMPLE)
        val landlord = page.results.last()

        val domain = landlord.toDomain()
        assertEquals(4, domain.priority)
        val due = assertNotNull(domain.due)
        assertEquals(LocalDate(2026, 4, 14), due.date)
        // `datetime` carried a wall-clock local time → surfaced as a time-of-day.
        assertNotNull(due.time)
    }

    @Test
    fun parsesParentIdAndDescription() {
        val page = json.decodeFromString(TaskPage.serializer(), TASKS_SAMPLE)
        val top = page.results.first().toDomain()
        val sub = page.results.last().toDomain()
        // Top-level task has no parent; the widget keeps it in the list.
        assertNull(top.parentId)
        // Subtask carries its parent id (so the widget rolls it up + badges it)
        // and its description flows into the detail view's notes.
        assertEquals("2995104339", sub.parentId)
        assertEquals("ask about the lease", sub.note)
    }

    @Test
    fun completedStampFlowsThroughToDomain() {
        val page = json.decodeFromString(TaskPage.serializer(), TASKS_SAMPLE)
        val closed = page.results.last()
        assertNotNull(closed.completedAt)
        assertTrue(closed.toDomain().done)
    }

    @Test
    fun toleratesMissingCreatedTimestamp() {
        // Imported tasks occasionally omit the creation stamp; the provider must
        // not throw and should fall back to "now".
        val page = json.decodeFromString(TaskPage.serializer(), MINIMAL_SAMPLE)
        val single = page.results.single()
        assertEquals("Imported", single.content)
        assertNotNull(single.toDomain().createdAt)
    }

    @Test
    fun acceptsLegacyCreatedAtAlias() {
        // `added_at` is the v1 field, but we still accept `created_at` so a
        // mixed/older payload sorts correctly instead of collapsing to "now".
        val page = json.decodeFromString(TaskPage.serializer(), LEGACY_CREATED_SAMPLE)
        assertEquals("2019-12-11T22:36:50.000000Z", page.results.single().createdAt)
    }

    companion object {
        // Real v1 documents carry due/labels/priority etc.; we keep the fields
        // under test plus one extra to exercise `ignoreUnknownKeys`, wrapped in
        // the paginated envelope the API actually returns.
        private val TASKS_SAMPLE: String = """
            {
              "results": [
                {
                  "id": "2995104339",
                  "content": "Buy milk",
                  "description": "",
                  "labels": ["groceries"],
                  "priority": 1,
                  "added_at": "2019-12-11T22:36:50.000000Z",
                  "url": "https://todoist.com/showTask?id=2995104339"
                },
                {
                  "id": "2995104340",
                  "content": "Email landlord",
                  "description": "ask about the lease",
                  "parent_id": "2995104339",
                  "priority": 4,
                  "due": {
                    "date": "2026-04-14",
                    "datetime": "2026-04-14T13:00:00",
                    "string": "Apr 14 1:00 PM",
                    "is_recurring": false,
                    "timezone": "Europe/London"
                  },
                  "added_at": "2026-04-12T10:00:00.000000Z",
                  "completed_at": "2026-04-13T09:00:00.000000Z"
                }
              ],
              "next_cursor": null
            }
        """.trimIndent()

        private val MINIMAL_SAMPLE: String = """
            { "results": [ { "id": "1", "content": "Imported" } ], "next_cursor": null }
        """.trimIndent()

        private val LEGACY_CREATED_SAMPLE: String = """
            { "results": [ { "id": "1", "content": "Old", "created_at": "2019-12-11T22:36:50.000000Z" } ] }
        """.trimIndent()
    }
}
