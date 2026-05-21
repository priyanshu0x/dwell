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
import kotlin.math.max
import kotlin.time.Clock

class ExpensesWidgetFactory : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.expenses"
    override val displayName: String = "Expenses"
    override val description: String = "Current-month expense tracking with local storage"
    override val category: WidgetCategory = WidgetCategory.FINANCE
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Enum(
            key = "currency",
            label = "Currency",
            options = listOf(
                ConfigField.EnumOption("USD", "USD"),
                ConfigField.EnumOption("EUR", "EUR"),
                ConfigField.EnumOption("GBP", "GBP"),
                ConfigField.EnumOption("INR", "INR"),
            ),
            default = "USD",
        ),
        ConfigField.Text(
            key = "categories",
            label = "Categories",
            default = "food,transport,entertainment,bills",
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = ExpensesWidget(config, scope)
}

private class ExpensesWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
) : Widget {
    override val preferredSpan: Int = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Expense.serializer())
    private var expenses by mutableStateOf<List<Expense>>(emptyList())
    private var amountInput by mutableStateOf("")
    private var categoryInput by mutableStateOf("")
    private var noteInput by mutableStateOf("")

    override fun onResume() {
        scope.coroutineScope.launch {
            expenses = scope.storage.read(storageKey(), String::class.java)
                ?.let { json.decodeFromString(serializer, it) }
                ?: emptyList()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val currency = config.enum("currency", "USD")
        val currentCategories = config.string("categories")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val total = expenses.sumOf { it.amount }

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text(
                    text = "$currency ${"%.2f".format(total)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { addExpense(currentCategories) }, enabled = amountInput.toDoubleOrNull() != null) {
                    Text("Add")
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
                    IconButton(onClick = { deleteExpense(expense.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete expense")
                    }
                }
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
    }

    private fun deleteExpense(id: String) {
        expenses = expenses.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val snapshot = json.encodeToString(serializer, expenses)
        scope.coroutineScope.launch {
            scope.storage.write(storageKey(), snapshot)
        }
    }

    private fun storageKey(): String {
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = (date.month.ordinal + 1).toString().padStart(2, '0')
        return "expenses-${date.year}-$month.json"
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
