package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.components.pausesShortcutsWhileFocused
import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.network.BackendResult
import com.droidslife.screensaver.storage.SyncRepository
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.launch
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock

private const val WIDGET_ID = "com.droidslife.screensaver.expenses"

class ExpensesWidgetFactory(
    private val backendGateway: BackendGateway,
) : WidgetFactory {
    override val id: String = WIDGET_ID
    override val displayName: String = "Expenses"
    override val description: String = "Current-month expense tracking with local storage"
    override val category: WidgetCategory = WidgetCategory.FINANCE
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 4, defaultRows = 2,
        maxCols = 8, maxRows = 3,
    )
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

    override fun summary(): WidgetSummary {
        val currency = config.enum("currency", "USD")
        val total = expenses.sumOf { it.amount }
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val monthName = monthShortName(now.month)
        val topCategories = expenses
            .groupBy { it.category }
            .map { (cat, items) -> cat to items.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(3)
        val subtitle = if (topCategories.isEmpty()) {
            "No expenses yet"
        } else {
            topCategories.joinToString(" · ") { (cat, amt) -> "$cat $currency ${"%.2f".format(amt)}" }
        }
        return WidgetSummary(
            primaryValue = "$currency ${"%.2f".format(total)}",
            primaryLabel = "Spend · $monthName",
            subtitle = subtitle,
        )
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
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val monthLabel = monthShortName(now.month).uppercase()

        val topCategories = expenses
            .groupBy { it.category }
            .map { (cat, items) -> cat to items.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(3)
        val subtitle = if (topCategories.isEmpty()) {
            "No expenses yet"
        } else {
            topCategories.joinToString(" · ") { (cat, amt) ->
                "$cat $currency ${"%.0f".format(amt)}"
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            WidgetHeader(
                label = "SPEND · $monthLabel",
                settingsId = WIDGET_ID,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                IconButton(
                    onClick = { inputVisible = !inputVisible },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = if (inputVisible) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (inputVisible) "Hide add form" else "Add expense",
                        tint = DwellColors.TextLow,
                    )
                }
            }

            if (inputVisible) {
                // Add form covers the tile while open; collapses back to the
                // big-value layout on close.
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                ) {
                    Spacer(Modifier.size(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it.filter { char -> char.isDigit() || char == '.' } },
                            label = { Text("Amount") },
                            modifier = Modifier.weight(0.8f).pausesShortcutsWhileFocused(),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = { categoryInput = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f).pausesShortcutsWhileFocused(),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth().pausesShortcutsWhileFocused(),
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { addExpense(currentCategories) },
                            enabled = amountInput.toDoubleOrNull() != null,
                        ) {
                            Text("Add")
                        }
                    }
                }
            } else {
                Text(
                    text = "$currency ${"%.2f".format(total)}",
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 36.sp,
                    color = DwellColors.TextHigh,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Text(
                text = if (unsyncedCount > 0) "$unsyncedCount unsynced" else subtitle,
                fontFamily = DwellFonts.interTight(),
                fontSize = 10.sp,
                color = if (unsyncedCount > 0) DwellColors.StatusError else DwellColors.TextMid,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart),
            )
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
