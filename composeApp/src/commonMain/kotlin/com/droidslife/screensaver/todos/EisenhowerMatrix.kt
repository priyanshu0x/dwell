package com.droidslife.screensaver.todos

import com.droidslife.screensaver.todos.providers.Todo
import kotlinx.datetime.LocalDate

/**
 * The four Eisenhower quadrants, ordered Do → Schedule → Delegate → Drop.
 * [important] and [urgent] are the two axes; the widget lays them out as a 2×2
 * grid (urgent on the right, important on top).
 */
enum class Quadrant(val label: String, val important: Boolean, val urgent: Boolean) {
    Do("DO", important = true, urgent = true),
    Schedule("SCHEDULE", important = true, urgent = false),
    Delegate("DELEGATE", important = false, urgent = true),
    Drop("DROP", important = false, urgent = false);

    companion object {
        fun of(important: Boolean, urgent: Boolean): Quadrant =
            entries.first { it.important == important && it.urgent == urgent }
    }
}

/**
 * Pure Eisenhower-matrix rules: how a [Todo] maps onto the Importance × Urgency
 * grid, and what a drag into a quadrant should change. Deliberately free of any
 * Compose / Ktor / storage dependency and operating only on the [Todo] domain
 * model, so this same logic can later move into a Kotlin server module verbatim.
 *
 * Axes (per the agreed mapping):
 *  - Importance ← Todoist priority. p1/p2 (api 4/3) are important; p3/p4 not.
 *  - Urgency    ← due date. Due on or before "today" is urgent; later / none is not.
 */
object EisenhowerMatrix {
    /** Todoist priority at/above which a task counts as important (p2 = api 3). */
    const val IMPORTANT_MIN_PRIORITY: Int = 3

    /** Priority written when promoting a task into the important rows. */
    const val DEFAULT_IMPORTANT_PRIORITY: Int = 4 // p1

    /** Priority written when demoting a task out of the important rows. */
    const val DEFAULT_UNIMPORTANT_PRIORITY: Int = 1 // p4

    fun isImportant(todo: Todo): Boolean = todo.priority >= IMPORTANT_MIN_PRIORITY

    fun isUrgent(todo: Todo, today: LocalDate): Boolean {
        val date = todo.due?.date ?: return false
        return date <= today
    }

    fun quadrantOf(todo: Todo, today: LocalDate): Quadrant =
        Quadrant.of(important = isImportant(todo), urgent = isUrgent(todo, today))

    /** Buckets [todos] into all four quadrants (empty lists included). */
    fun bucket(todos: List<Todo>, today: LocalDate): Map<Quadrant, List<Todo>> {
        val grouped = todos.groupBy { quadrantOf(it, today) }
        return Quadrant.entries.associateWith { grouped[it].orEmpty() }
    }

    /**
     * Priority to write when a task is dropped into [target]'s importance row.
     * Crosses the importance threshold only when needed, otherwise preserves the
     * task's existing nuance (e.g. a p1 dropped into another important quadrant
     * stays p1 rather than being flattened).
     */
    fun priorityFor(target: Quadrant, currentPriority: Int): Int = when {
        target.important && currentPriority < IMPORTANT_MIN_PRIORITY -> DEFAULT_IMPORTANT_PRIORITY
        !target.important && currentPriority >= IMPORTANT_MIN_PRIORITY -> DEFAULT_UNIMPORTANT_PRIORITY
        else -> currentPriority
    }

    /** True when moving [todo] into [target] flips its urgency — i.e. the due
     *  date must change, which is when the widget prompts with a date picker. */
    fun changesUrgency(todo: Todo, target: Quadrant, today: LocalDate): Boolean =
        isUrgent(todo, today) != target.urgent

    /**
     * The due date to pre-select when a drag flips urgency, chosen so the task
     * actually lands in [target]: today for an urgent quadrant, or the next day
     * (the first non-urgent date) otherwise. The user can still override it.
     */
    fun suggestedDueDate(target: Quadrant, today: LocalDate): LocalDate =
        if (target.urgent) today else LocalDate.fromEpochDays(today.toEpochDays() + 1)
}
