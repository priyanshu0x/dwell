package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.network.BackendResult
import com.droidslife.screensaver.storage.SyncRepository
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.math.max
import kotlin.time.Clock

class ExpensesWidgetFactory(
    private val backendGateway: BackendGateway,
) : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.expenses"
    override val displayName: String = "Expenses"
    override val description: String = "Current-month expense tracking with local storage"
    override val category: WidgetCategory = WidgetCategory.FINANCE
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Currency(
            key = "currency",
            label = "Currency",
            default = "USD",
            popular = listOf("USD", "EUR", "GBP", "INR"),
        ),
        ConfigField.StringList(
            key = "categories",
            label = "Categories",
            default = listOf("food", "transport", "entertainment", "bills"),
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = ExpensesWidget(config, scope, backendGateway)
}

private class ExpensesWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
    backendGateway: BackendGateway,
) : Widget {
    override val preferredSpan: Int = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Expense.serializer())
    private val syncRepository = SyncRepository("expenses", scope.storage, backendGateway)
    private var expenses by mutableStateOf<List<Expense>>(emptyList())
    private var amountInput by mutableStateOf("")
    private var categoryInput by mutableStateOf("")
    private var noteInput by mutableStateOf("")
    private var unsyncedCount by mutableStateOf(0)
    private var inputVisible by mutableStateOf(false)

    override fun onResume() {
        scope.coroutineScope.launch {
            expenses = scope.storage.read(storageKey(), String::class.java)
                ?.let { json.decodeFromString(serializer, it) }
                ?: emptyList()
            mergeRemoteExpenses()
            pushPending()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val currency = config.enum("currency", "USD")
        val currentCategories = config.stringList(
            "categories",
            default = listOf("food", "transport", "entertainment", "bills"),
        )
        val total = expenses.sumOf { it.amount }

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Total + toggle add form
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$currency ${"%.2f".format(total)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { inputVisible = !inputVisible }) {
                    Icon(
                        imageVector = if (inputVisible) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (inputVisible) "Hide add form" else "Add expense",
                    )
                }
            }

            if (inputVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { addExpense(currentCategories) }, enabled = amountInput.toDoubleOrNull() != null) {
                        Text("Add")
                    }
                }
            }

            ExpenseBars(expenses = expenses, modifier = Modifier.fillMaxWidth().height(56.dp))

            expenses.sortedByDescending { it.occurredAt }.take(5).forEach { expense ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(expense.category, fontWeight = FontWeight.SemiBold)
                        if (expense.note.isNotBlank()) {
                            Text(
                                expense.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("$currency ${"%.2f".format(expense.amount)}")
                    if (inputVisible) {
                        IconButton(onClick = { deleteExpense(expense.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Delete expense")
                        }
                    }
                }
            }

            if (unsyncedCount > 0) {
                Text(
                    text = "$unsyncedCount unsynced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    private fun addExpense(categories: List<String>) {
        val amount = amountInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val category = categoryInput.trim().ifBlank { categories.firstOrNull() ?: "general" }
        expenses = expenses + Expense(
            id = "expense-$now-${expenses.size}",
            amount = amount,
            category = category,
            note = noteInput.trim(),
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        )
        amountInput = ""
        categoryInput = ""
        noteInput = ""
        persist()
        queueUpsert(expenses.last())
    }

    private fun deleteExpense(id: String) {
        expenses = expenses.filterNot { it.id == id }
        persist()
        scope.coroutineScope.launch {
            syncRepository.enqueueDelete(id)
            pushPending()
        }
    }

    private fun persist() {
        val snapshot = json.encodeToString(serializer, expenses)
        scope.coroutineScope.launch {
            scope.storage.write(storageKey(), snapshot)
        }
    }

    private fun queueUpsert(expense: Expense) {
        scope.coroutineScope.launch {
            syncRepository.enqueueUpsert(expense.id, expense.toJsonObject(), expense.updatedAt)
            pushPending()
        }
    }

    private suspend fun mergeRemoteExpenses() {
        when (val result = syncRepository.pullRemote()) {
            BackendResult.Disabled -> Unit
            is BackendResult.Failure -> {
                scope.log.warn("Expense sync pull failed", result.cause)
                unsyncedCount = syncRepository.pendingCount()
            }
            is BackendResult.Success -> {
                val remote = result.value.mapNotNull { runCatching { it.toExpense() }.getOrNull() }
                val currentMonthKey = storageKey()
                val remoteForMonth = remote.filter { storageKeyFor(it.occurredAt) == currentMonthKey }
                if (remoteForMonth.isNotEmpty()) {
                    expenses = (expenses + remoteForMonth)
                        .groupBy { it.id }
                        .map { (_, values) -> values.maxBy { it.updatedAt } }
                    persist()
                }
            }
        }
    }

    private suspend fun pushPending() {
        val result = syncRepository.pushPending()
        if (result.lastError != null) {
            scope.log.warn("Expense sync push failed: ${result.lastError}")
        }
        unsyncedCount = result.remaining
    }

    private fun storageKey(): String {
        return storageKeyFor(Clock.System.now().toEpochMilliseconds())
    }

    private fun storageKeyFor(epochMillis: Long): String {
        val date = kotlin.time.Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val month = (date.month.ordinal + 1).toString().padStart(2, '0')
        return "expenses-${date.year}-$month.json"
    }

    private fun Expense.toJsonObject(): JsonObject {
        return json.encodeToJsonElement(Expense.serializer(), this) as JsonObject
    }

    private fun JsonObject.toExpense(): Expense {
        return json.decodeFromJsonElement(Expense.serializer(), this)
    }
}

@Composable
private fun ExpenseBars(expenses: List<Expense>, modifier: Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.padding(vertical = 6.dp)) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val totals = (6 downTo 0).map { offset ->
            val date = today.minus(offset, DateTimeUnit.DAY)
            expenses
                .filter { expense ->
                    val expenseDate = kotlin.time.Instant.fromEpochMilliseconds(expense.occurredAt)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                    expenseDate == date
                }
                .sumOf { it.amount }
        }
        val maxValue = max(1.0, totals.maxOrNull() ?: 0.0)
        val slot = size.width / totals.size
        drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        totals.forEachIndexed { index, total ->
            val height = ((total / maxValue).toFloat() * size.height).coerceAtLeast(if (total > 0) 4f else 0f)
            val x = index * slot + slot * 0.25f
            drawRect(
                color = if (total > 0.0) barColor else Color.Transparent,
                topLeft = Offset(x, size.height - height),
                size = androidx.compose.ui.geometry.Size(slot * 0.5f, height),
            )
        }
    }
}

@Serializable
private data class Expense(
    val id: String,
    val amount: Double,
    val category: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
