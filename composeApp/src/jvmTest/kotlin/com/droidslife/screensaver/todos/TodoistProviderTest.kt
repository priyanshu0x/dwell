package com.droidslife.screensaver.todos

import com.droidslife.screensaver.todos.providers.TodoistTask
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the Todoist REST v2 `GET /tasks` wire shape TodoistProvider parses
 * against. The real document carries roughly 20 fields per task; we only
 * deserialize the four the widget renders (`id`, `content`, `is_completed`,
 * `created_at`), so this test fails fast if any of those keys move.
 *
 * The HTTP layer is exercised separately by an integration test; here we
 * stay at the JSON contract so the test doesn't need a Ktor mock engine.
 */
class TodoistProviderTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val listSerializer = ListSerializer(TodoistTask.serializer())

    @Test
    fun parsesHeadlineFieldsOutOfTasksResponse() {
        val tasks = json.decodeFromString(listSerializer, TASKS_SAMPLE)
        assertEquals(2, tasks.size)

        val first = tasks.first()
        assertEquals("2995104339", first.id)
        assertEquals("Buy milk", first.content)
        assertEquals(false, first.isCompleted)
        assertEquals("2019-12-11T22:36:50.000000Z", first.createdAt)

        val domain = first.toDomain()
        assertEquals("Buy milk", domain.text)
        assertEquals(false, domain.done)
        // The wire string is ISO-8601 with microseconds; Instant.parse handles it.
        assertEquals(domain.createdAt, domain.updatedAt)
    }

    @Test
    fun completedFlagFlowsThroughToDomain() {
        val tasks = json.decodeFromString(listSerializer, TASKS_SAMPLE)
        val closed = tasks.last()
        assertTrue(closed.isCompleted)
        assertEquals(true, closed.toDomain().done)
    }

    @Test
    fun toleratesMissingCreatedAt() {
        // Older Todoist accounts occasionally omit `created_at` on imported
        // tasks; provider must not throw and should fall back to "now".
        val tasks = json.decodeFromString(listSerializer, MINIMAL_SAMPLE)
        val single = tasks.single()
        assertEquals("Imported", single.content)
        val domain = single.toDomain()
        assertNotNull(domain.createdAt)
    }

    companion object {
        // Real Todoist `GET /tasks` documents include due/labels/priority etc.;
        // we keep just the fields under test plus one extra to exercise
        // `ignoreUnknownKeys`.
        private val TASKS_SAMPLE: String = """
            [
              {
                "id": "2995104339",
                "content": "Buy milk",
                "description": "",
                "is_completed": false,
                "labels": ["groceries"],
                "priority": 1,
                "created_at": "2019-12-11T22:36:50.000000Z",
                "url": "https://todoist.com/showTask?id=2995104339"
              },
              {
                "id": "2995104340",
                "content": "Email landlord",
                "is_completed": true,
                "created_at": "2026-04-12T10:00:00.000000Z"
              }
            ]
        """.trimIndent()

        private val MINIMAL_SAMPLE: String = """
            [
              { "id": "1", "content": "Imported", "is_completed": false }
            ]
        """.trimIndent()
    }
}
