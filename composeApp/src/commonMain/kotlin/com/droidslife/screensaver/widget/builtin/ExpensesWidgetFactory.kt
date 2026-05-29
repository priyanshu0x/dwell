package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.components.ShortcutPause
import com.droidslife.screensaver.components.WidgetStatusLine
import com.droidslife.screensaver.components.WidgetStatusSeverity
import com.droidslife.screensaver.components.pausesShortcutsWhileFocused
import com.droidslife.screensaver.network.CreateTransactionLine
import com.droidslife.screensaver.network.CreateTransactionRequest
import com.droidslife.screensaver.network.FastiflyAccount
import com.droidslife.screensaver.network.FastiflyCategory
import com.droidslife.screensaver.network.FastiflyClient
import com.droidslife.screensaver.network.FastiflyResult
import com.droidslife.screensaver.network.FastiflyTransactionGroup
import com.droidslife.screensaver.network.MeContext
import com.droidslife.screensaver.serialization.DwellJson
import com.droidslife.screensaver.ui.DwellActionButton
import com.droidslife.screensaver.ui.DwellAnchoredDialog
import com.droidslife.screensaver.ui.DwellChoiceChip
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellControlFrame
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellFormLabel
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
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

private const val WIDGET_ID = "com.droidslife.screensaver.expenses"

class ExpensesWidgetFactory : WidgetFactory {
    override val id: String = WIDGET_ID
    override val displayName: String = "Expenses"
    override val description: String = "Income / expense / transfer entry, monthly report, and recent activity — synced to Fastifly, offline-first"
    override val category: WidgetCategory = WidgetCategory.FINANCE
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 4, defaultRows = 3,
        maxCols = 8, maxRows = 4,
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.Text(
            key = "backendUrl",
            label = "Fastifly URL",
            placeholder = "http://localhost:3400",
            help = "Fastifly server origin. Leave blank to disable sync.",
        ),
        // Key is intentionally NOT "apiToken"/"apiKey": the shared config panel's
        // shouldHideField() hides those unless a Todoist/WeatherAPI `provider` is
        // selected, which would hide this field for the Expenses widget.
        ConfigField.Secret(
            key = "fastiflyApiKey",
            label = "API key",
            help = "A Fastifly API key (ffk_…) from Settings → API keys in the Fastifly web app.",
        ),
        ConfigField.Currency(
            key = "currency",
            label = "Display currency",
            default = "USD",
            popular = listOf("USD", "EUR", "GBP", "INR"),
            help = "Fallback shown until the ledger currency loads.",
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        val client = FastiflyClient(
            httpClient = scope.httpClient,
            baseUrlProvider = { config.string("backendUrl") },
            tokenProvider = { config.secret("fastiflyApiKey") },
        )
        return ExpensesWidget(config, scope, client)
    }
}

private enum class TxType(val wire: String, val label: String, val glyph: String) {
    Expense("expense", "Expense", "↓"),
    Income("income", "Income", "↑"),
    Transfer("transfer", "Transfer", "⇄"),
}

private enum class Phase { Unconfigured, Loading, Ready, Offline, AuthFailed, Error, NeedsSetup }

private data class ExpensesViewState(
    val context: MeContext? = null,
    val accounts: List<FastiflyAccount> = emptyList(),
    val categories: List<FastiflyCategory> = emptyList(),
    val recent: List<FastiflyTransactionGroup> = emptyList(),
    val pending: List<PendingTransaction> = emptyList(),
    val phase: Phase,
    val lastError: String? = null,
) {
    fun withCache(snapshot: CacheSnapshot?): ExpensesViewState =
        if (snapshot == null) {
            this
        } else {
            copy(
                context = snapshot.context,
                accounts = snapshot.accounts,
                categories = snapshot.categories,
                recent = snapshot.recent,
            )
        }

    fun toCacheSnapshot(): CacheSnapshot = CacheSnapshot(context, accounts, categories, recent)

    companion object {
        fun initial(configured: Boolean): ExpensesViewState =
            ExpensesViewState(phase = if (configured) Phase.Loading else Phase.Unconfigured)
    }
}

private class ExpensesWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
    private val client: FastiflyClient,
) : Widget {
    override val preferredSpan: Int = 1
    private val json = DwellJson.Persisted
    private val pendingSerializer = ListSerializer(PendingTransaction.serializer())

    private var viewState by mutableStateOf(ExpensesViewState.initial(client.isConfigured()))
    private val context: MeContext? get() = viewState.context
    private val accounts: List<FastiflyAccount> get() = viewState.accounts
    private val categories: List<FastiflyCategory> get() = viewState.categories
    private val recent: List<FastiflyTransactionGroup> get() = viewState.recent
    private val pending: List<PendingTransaction> get() = viewState.pending
    private val phase: Phase get() = viewState.phase
    private val lastError: String? get() = viewState.lastError

    // Add-form state
    private var inputVisible by mutableStateOf(false)
    private var txType by mutableStateOf(TxType.Expense)
    private var amountInput by mutableStateOf("")
    private var noteInput by mutableStateOf("")
    private var categoryId by mutableStateOf("")
    private var sourceId by mutableStateOf("")
    private var destId by mutableStateOf("")
    private var formError by mutableStateOf<String?>(null)

    private val currencyFallback: String get() = config.enum("currency", "USD")
    private val currency: String get() = context?.currencyCode?.takeIf { it.isNotBlank() } ?: currencyFallback

    // The host does not reliably drive onResume() (see PomodoroWidget), so the
    // first load is kicked off from Content via a LaunchedEffect. No "already
    // started" guard: if that effect's coroutine is cancelled by composition
    // churn, a sticky guard would leave the tile stuck on "Loading…" forever.
    // Re-entry simply re-runs start(), which is safe (idempotent reads + an
    // idempotent outbox push).
    override fun onResume() {
        scope.coroutineScope.launch { start() }
    }

    private suspend fun start() {
        // The initial view state is already Unconfigured for blank config, so
        // cache hydration can't delay the "Connect to Fastifly" prompt.
        if (!client.isConfigured()) {
            val cached = runCatching { readCache() }
                .onFailure { scope.log.warn("Expenses cache load failed: ${it.message}") }
                .getOrNull()
            val outbox = runCatching { readOutbox() }
                .onFailure { scope.log.warn("Expenses outbox load failed: ${it.message}") }
                .getOrDefault(emptyList())
            viewState = viewState.withCache(cached).copy(
                pending = outbox,
                phase = Phase.Unconfigured,
            )
            return
        }
        val cached = runCatching { readCache() }
            .onFailure { scope.log.warn("Expenses cache load failed: ${it.message}") }
            .getOrNull()
        val outbox = runCatching { readOutbox() }
            .onFailure { scope.log.warn("Expenses outbox load failed: ${it.message}") }
            .getOrDefault(emptyList())
        viewState = viewState.withCache(cached).copy(pending = outbox)
        refresh()
    }

    override fun summary(): WidgetSummary {
        val (income, expense) = monthlyTotals()
        val net = income - expense
        return WidgetSummary(
            primaryValue = formatMoney(expense, currency),
            primaryLabel = "Spend · ${monthShort()}",
            subtitle = "In ${formatMoney(income, currency)} · Net ${formatMoney(net, currency)}",
        )
    }

    @Composable
    override fun Content(modifier: Modifier) {
        LaunchedEffect(Unit) { start() }
        val month = monthShort().uppercase()
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                WidgetHeader(label = "EXPENSES · $month", settingsId = WIDGET_ID) {
                    IconButton(
                        onClick = { if (canAdd()) inputVisible = !inputVisible },
                        enabled = canAdd(),
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = if (inputVisible) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = if (inputVisible) "Hide add form" else "Add transaction",
                            tint = DwellColors.TextLow,
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        // Computed at render time from config — never depends on a
                        // background coroutine — so an unconfigured tile is guaranteed
                        // to show the connect prompt instead of a stuck "Loading…".
                        !client.isConfigured() ->
                            EmptyState("Connect to Fastifly", "Open settings (gear above) and add your Fastifly URL + API key.")
                        phase == Phase.Loading && context == null -> EmptyState("Loading…", null)
                        phase == Phase.AuthFailed && context == null ->
                            EmptyState("Fastifly credentials rejected", lastError ?: "Update the URL/API key in settings.")
                        phase == Phase.Error && context == null ->
                            EmptyState("Can't reach Fastifly", lastError ?: "Check the URL and API key.")
                        else -> Overview()
                    }
                }

                val (msg, sev) = statusLine()
                WidgetStatusLine(msg, severity = sev)
            }

            if (inputVisible && canAdd()) {
                AddTransactionDialog(onClose = { inputVisible = false })
            }
        }
    }

    @Composable
    private fun EmptyState(title: String, subtitle: String?) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, fontFamily = DwellFonts.interTight(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = DwellColors.TextHigh)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontFamily = DwellFonts.interTight(), fontSize = 11.sp, color = DwellColors.TextMid)
            }
        }
    }

    // --- Overview (summary + recent) ---------------------------------------

    @Composable
    private fun Overview() {
        val (income, expense) = monthlyTotals()
        val net = income - expense
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCell("IN", income, DwellColors.StatusAccent, Modifier.weight(1f))
                SummaryCell("OUT", expense, DwellColors.TextHigh, Modifier.weight(1f))
                SummaryCell("NET", net, if (net >= 0) DwellColors.StatusAccent else DwellColors.TextHigh, Modifier.weight(1f))
            }

            val tops = topCategories()
            if (tops.isNotEmpty()) {
                Text(
                    text = tops.joinToString(" · ") { (name, amt) -> "$name ${formatMoney(amt, currency)}" },
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 10.sp,
                    color = DwellColors.TextMid,
                    maxLines = 1,
                )
            }

            val rows = displayRows()
            if (rows.isEmpty()) {
                Text(
                    text = if (phase == Phase.NeedsSetup) {
                        "Add an account and a category in the Fastifly web app to start."
                    } else {
                        "No transactions yet this month."
                    },
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                    color = DwellColors.TextLow,
                )
            } else {
                rows.take(3).forEach { TransactionRow(it) }
            }
        }
    }

    @Composable
    private fun SummaryCell(label: String, minor: Long, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
        Column(modifier = modifier) {
            Text(label, fontFamily = DwellFonts.interTight(), fontSize = 9.sp, color = DwellColors.TextLow)
            Text(
                formatMoney(minor, currency),
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = valueColor,
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun TransactionRow(row: DisplayRow) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.glyph, fontFamily = DwellFonts.jetBrainsMono(), fontSize = 13.sp, color = DwellColors.TextMid)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.label.ifBlank { "—" },
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 12.sp,
                    color = if (row.pending) DwellColors.TextLow else DwellColors.TextHigh,
                    maxLines = 1,
                )
                Text(
                    if (row.pending) "syncing · ${row.dateLabel}" else row.dateLabel,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 9.sp,
                    color = DwellColors.TextLow,
                    maxLines = 1,
                )
            }
            Text(
                formatMoney(row.amountMinor, currency),
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 12.sp,
                color = if (row.income) DwellColors.StatusAccent else DwellColors.TextHigh,
                maxLines = 1,
            )
        }
    }

    // --- Add form ----------------------------------------------------------

    @Composable
    private fun AddTransactionDialog(onClose: () -> Unit) {
        val currentOnClose by rememberUpdatedState(onClose)
        DisposableEffect(Unit) {
            val pause = ShortcutPause.acquire { currentOnClose() }
            onDispose { ShortcutPause.release(pause) }
        }

        DwellAnchoredDialog(
            onDismiss = onClose,
            minWidth = 520.dp,
            maxWidth = 620.dp,
            maxHeight = 520.dp,
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DwellFormLabel("Transaction")
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            "Close",
                            tint = DwellColors.TextLow,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                AddFormFields(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DwellColors.Stroke),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DwellColors.DialogControlSurface)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    formError?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 12.sp,
                            color = DwellColors.StatusError,
                            maxLines = 2,
                        )
                    } ?: Spacer(Modifier.weight(1f))
                    DwellActionButton(
                        label = "Add",
                        onClick = { submit() },
                        primary = true,
                        leadingIcon = Icons.Filled.Check,
                        minWidth = 78.dp,
                    )
                }
            }
        }
    }

    @Composable
    private fun AddFormFields(modifier: Modifier = Modifier) {
        val sources = sourceAccountsFor(txType)
        val usableCategories = expenseCategories()
        val destinations = destinationAccountsFor(txType, sourceId)

        // Keep selections valid as the type or available options change.
        LaunchedEffect(txType, accounts, categories) {
            if (sources.none { it.id == sourceId }) sourceId = sources.firstOrNull()?.id.orEmpty()
            if (usableCategories.none { it.id == categoryId }) categoryId = usableCategories.firstOrNull()?.id.orEmpty()
        }
        LaunchedEffect(txType, sourceId, accounts) {
            val dests = destinationAccountsFor(txType, sourceId)
            if (dests.none { it.id == destId }) destId = dests.firstOrNull()?.id.orEmpty()
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DwellFormLabel("Type")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TxType.entries.forEach { type ->
                        DwellChoiceChip(
                            label = "${type.glyph} ${type.label}",
                            selected = txType == type,
                            color = DwellColors.StatusAccent,
                            onClick = {
                                txType = type
                                clearFormError()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DwellTextInput(
                    label = "Amount (${currency})",
                    value = amountInput,
                    onValueChange = {
                        amountInput = it.filter { c -> c.isDigit() || c == '.' }
                        clearFormError()
                    },
                    placeholder = "0.00",
                    modifier = Modifier.weight(1f),
                )
                DwellTextInput(
                    label = "Description",
                    value = noteInput,
                    onValueChange = {
                        noteInput = it
                        clearFormError()
                    },
                    placeholder = defaultDescription(),
                    modifier = Modifier.weight(1f),
                )
            }

            when (txType) {
                TxType.Expense -> {
                    FormPair(
                        left = {
                            Picker("From account", sources.map { it.id to it.name }, sourceId) {
                                sourceId = it
                                clearFormError()
                            }
                        },
                        right = {
                            Picker("Category", usableCategories.map { it.id to it.name }, categoryId) {
                                categoryId = it
                                clearFormError()
                            }
                        },
                    )
                }
                TxType.Income -> {
                    FormPair(
                        left = {
                            Picker("From source", sources.map { it.id to it.name }, sourceId) {
                                sourceId = it
                                clearFormError()
                            }
                        },
                        right = {
                            Picker("To account", destinations.map { it.id to it.name }, destId) {
                                destId = it
                                clearFormError()
                            }
                        },
                    )
                }
                TxType.Transfer -> {
                    FormPair(
                        left = {
                            Picker("From account", sources.map { it.id to it.name }, sourceId) {
                                sourceId = it
                                clearFormError()
                            }
                        },
                        right = {
                            Picker("To account", destinations.map { it.id to it.name }, destId) {
                                destId = it
                                clearFormError()
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun FormPair(
        left: @Composable () -> Unit,
        right: @Composable () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) { left() }
            Box(modifier = Modifier.weight(1f)) { right() }
        }
    }

    @Composable
    private fun Picker(label: String, options: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "—"
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DwellFormLabel(label)
            Box {
                DwellControlFrame(
                    modifier = Modifier.clickable(enabled = options.isNotEmpty()) { expanded = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (options.isEmpty()) "None available" else selectedLabel,
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 13.sp,
                            color = if (options.isEmpty()) DwellColors.TextLow else DwellColors.TextHigh,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text("▾", fontFamily = DwellFonts.interTight(), fontSize = 12.sp, color = DwellColors.TextMid)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (id, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
                    }
                }
            }
        }
    }

    @Composable
    private fun DwellTextInput(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DwellFormLabel(label)
            DwellControlFrame {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    cursorBrush = SolidColor(DwellColors.StatusAccent),
                    textStyle = TextStyle(
                        fontFamily = DwellFonts.interTight(),
                        fontSize = 13.sp,
                        color = DwellColors.TextHigh,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pausesShortcutsWhileFocused(),
                    decorationBox = { inner ->
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                fontFamily = DwellFonts.interTight(),
                                fontSize = 13.sp,
                                color = DwellColors.TextFaint,
                            )
                        }
                        inner()
                    },
                )
            }
        }
    }

    // --- Actions -----------------------------------------------------------

    private fun canAdd(): Boolean = phase == Phase.Ready || phase == Phase.Offline

    private fun submitValidationMessage(): String? {
        val minor = parseMinor(amountInput)
        if (context == null) return "Fastifly is still loading. Wait a moment, then try again."
        if (minor == null || minor <= 0L) return "Enter an amount greater than 0."
        if (sourceId.isBlank()) {
            return when (txType) {
                TxType.Income -> "Choose an income source."
                else -> "Choose the source account."
            }
        }
        return when (txType) {
            TxType.Expense -> {
                val category = categories.firstOrNull { it.id == categoryId }
                when {
                    categoryId.isBlank() -> "Choose a category."
                    category == null -> "Choose an available category."
                    category.counterpartyAccountId == null -> "Set a counterparty account for this category in Fastifly."
                    else -> null
                }
            }
            TxType.Income -> if (destId.isBlank()) "Choose the destination account." else null
            TxType.Transfer -> if (destId.isBlank()) "Choose the destination account." else null
        }
    }

    private fun submit() {
        val validationMessage = submitValidationMessage()
        if (validationMessage != null) {
            formError = validationMessage
            return
        }
        val ctx = context ?: return
        val minor = parseMinor(amountInput) ?: return
        val source = accounts.firstOrNull { it.id == sourceId } ?: return
        val description = noteInput.trim().ifBlank { defaultDescription() }
        val line = when (txType) {
            TxType.Expense -> {
                val cat = categories.firstOrNull { it.id == categoryId } ?: return
                val dest = cat.counterpartyAccountId ?: return
                CreateTransactionLine(amountMinor = minor.toString(), destinationAccountId = dest, categoryId = cat.id)
            }
            else -> CreateTransactionLine(amountMinor = minor.toString(), destinationAccountId = destId)
        }
        val request = CreateTransactionRequest(
            type = txType.wire,
            currencyCode = source.currencyCode.ifBlank { ctx.currencyCode },
            description = description,
            occurredAt = Clock.System.now().toString(),
            sourceAccountId = source.id,
            transactions = listOf(line),
        )
        val item = PendingTransaction(
            idempotencyKey = "dwell-${randomToken()}",
            request = request,
            displayLabel = description,
            displayType = txType.wire,
            displayAmountMinor = minor,
            displayOccurredAt = request.occurredAt,
        )
        amountInput = ""
        noteInput = ""
        formError = null
        inputVisible = false
        scope.coroutineScope.launch {
            val updatedPending = pending + item
            viewState = viewState.copy(pending = updatedPending)
            writeOutbox(updatedPending)
            pushOutbox()
        }
    }

    private fun clearFormError() {
        formError = null
    }

    private fun defaultDescription(): String = when (txType) {
        TxType.Expense -> categories.firstOrNull { it.id == categoryId }?.name ?: "Expense"
        TxType.Income -> "Income"
        TxType.Transfer -> "Transfer"
    }

    private fun FastiflyResult.Failure.isCredentialFailure(): Boolean = httpCode == 401 || httpCode == 403

    private fun FastiflyResult.Failure.displayMessage(): String =
        if (isCredentialFailure()) "Fastifly credentials rejected - update URL/API key in settings" else message

    private suspend fun refresh() {
        when (val ctxResult = client.meContext()) {
            FastiflyResult.Disabled -> {
                viewState = viewState.copy(phase = Phase.Unconfigured)
                return
            }
            is FastiflyResult.Failure -> {
                val nextPhase = when {
                    ctxResult.isCredentialFailure() -> Phase.AuthFailed
                    context != null -> Phase.Offline
                    else -> Phase.Error
                }
                viewState = viewState.copy(
                    lastError = ctxResult.displayMessage(),
                    phase = nextPhase,
                )
                return
            }
            is FastiflyResult.Success -> {
                val ctx = ctxResult.value
                val accountsResult = client.listAccounts(ctx)
                val categoriesResult = client.listCategories(ctx)
                val txnResult = client.listTransactions(ctx, limit = 25, fromOccurredAt = monthStartIso())

                val nextAccounts = when (accountsResult) {
                    is FastiflyResult.Success -> accountsResult.value
                    else -> accounts
                }
                val nextCategories = when (categoriesResult) {
                    is FastiflyResult.Success -> categoriesResult.value
                    else -> categories
                }
                val nextRecent = when (txnResult) {
                    is FastiflyResult.Success -> txnResult.value
                    else -> recent
                }
                val failures = listOf(accountsResult, categoriesResult, txnResult)
                    .mapNotNull { it as? FastiflyResult.Failure }
                val credentialFailure = failures.firstOrNull { it.isCredentialFailure() }
                val anyFailure = failures.isNotEmpty()
                val nextPhase = when {
                    credentialFailure != null -> Phase.AuthFailed
                    anyFailure && (nextAccounts.isEmpty() && nextRecent.isEmpty()) -> Phase.Error
                    anyFailure -> Phase.Offline
                    nextAccounts.isEmpty() || nextCategories.isEmpty() -> Phase.NeedsSetup
                    else -> Phase.Ready
                }

                viewState = viewState.copy(
                    context = ctx,
                    accounts = nextAccounts,
                    categories = nextCategories,
                    recent = nextRecent,
                    lastError = credentialFailure?.displayMessage() ?: failures.firstOrNull()?.message,
                    phase = nextPhase,
                )
            }
        }
        saveCache()
        pushOutbox()
    }

    private suspend fun pushOutbox() {
        val ctx = context ?: return
        val items = readOutbox()
        if (items.isEmpty()) {
            if (pending.isNotEmpty()) viewState = viewState.copy(pending = emptyList())
            return
        }
        var pushed = 0
        var index = 0
        var failure: String? = null
        while (index < items.size) {
            when (val result = client.createTransaction(ctx, items[index].request, items[index].idempotencyKey)) {
                is FastiflyResult.Success -> { index++; pushed++ }
                FastiflyResult.Disabled -> { failure = "Sync disabled"; break }
                is FastiflyResult.Failure -> {
                    // 409 = this idempotency key was already applied server-side
                    // (e.g. a prior attempt committed but its response was lost).
                    // Treat as done and drop it instead of retrying forever.
                    if (result.httpCode == 409) { index++; pushed++ } else { failure = result.message; break }
                }
            }
        }
        val remaining = items.drop(index)
        writeOutbox(remaining)
        var nextState = viewState.copy(
            pending = remaining,
            lastError = failure,
        )
        if (pushed > 0 && failure == null) {
            // Pull the authoritative server view so the just-synced rows replace
            // their optimistic placeholders.
            client.listTransactions(ctx, limit = 25, fromOccurredAt = monthStartIso()).let {
                if (it is FastiflyResult.Success) nextState = nextState.copy(recent = it.value)
            }
        }
        viewState = nextState
        if (pushed > 0 && failure == null) saveCache()
    }

    // --- Derived data ------------------------------------------------------

    /** Per-currency assumption of 2 minor digits — adequate for the tile display. */
    private fun formatMoney(minor: Long, currencyCode: String): String {
        val major = minor / 100.0
        val amount = if (abs(major) >= 1_000.0) formatK(major) else "%.2f".format(major)
        return "$currencyCode $amount"
    }

    private fun formatK(value: Double): String {
        val scaled = (value / 1_000.0 * 10.0).roundToLong() / 10.0
        return if (scaled % 1.0 == 0.0) "${scaled.toLong()}k" else "%.1fk".format(scaled)
    }

    private fun parseMinor(input: String): Long? {
        val value = input.trim().toDoubleOrNull() ?: return null
        return (value * 100.0).roundToLong()
    }

    /** Transaction magnitude = sum of the positive (incoming) postings. */
    private fun magnitude(group: FastiflyTransactionGroup): Long =
        group.journals.sumOf { journal ->
            journal.postings.sumOf { posting ->
                (posting.amountMinor.toLongOrNull() ?: 0L).coerceAtLeast(0L)
            }
        }

    private fun monthlyTotals(): Pair<Long, Long> {
        var income = 0L
        var expense = 0L
        recent.forEach { group ->
            when (group.type) {
                "income" -> income += magnitude(group)
                "expense" -> expense += magnitude(group)
            }
        }
        pending.forEach { p ->
            when (p.displayType) {
                "income" -> income += p.displayAmountMinor
                "expense" -> expense += p.displayAmountMinor
            }
        }
        return income to expense
    }

    private fun topCategories(): List<Pair<String, Long>> {
        val counterpartyToName = categories
            .mapNotNull { category ->
                category.counterpartyAccountId?.let { accountId -> accountId to category.name }
            }
            .toMap()
        val byCategory = mutableMapOf<String, Long>()
        recent.filter { it.type == "expense" }.forEach { group ->
            val accountId = group.journals
                .flatMap { it.postings }
                .firstOrNull { counterpartyToName.containsKey(it.accountId) }
                ?.accountId
            val name = accountId?.let { counterpartyToName[it] } ?: "Other"
            byCategory[name] = (byCategory[name] ?: 0L) + magnitude(group)
        }
        return byCategory.entries.sortedByDescending { it.value }.take(2).map { it.key to it.value }
    }

    private fun displayRows(): List<DisplayRow> {
        val pendingRows = pending.map { p ->
            DisplayRow(
                glyph = TxType.entries.firstOrNull { it.wire == p.displayType }?.glyph ?: "•",
                label = p.displayLabel,
                amountMinor = p.displayAmountMinor,
                dateLabel = shortDate(p.displayOccurredAt),
                income = p.displayType == "income",
                pending = true,
            )
        }
        val serverRows = recent.map { group ->
            val journal = group.journals.firstOrNull()
            DisplayRow(
                glyph = TxType.entries.firstOrNull { it.wire == group.type }?.glyph ?: "•",
                label = group.title ?: journal?.description ?: group.type.replaceFirstChar { it.uppercase() },
                amountMinor = magnitude(group),
                dateLabel = shortDate(journal?.occurredAt),
                income = group.type == "income",
                pending = false,
            )
        }
        return pendingRows + serverRows
    }

    private fun sourceAccountsFor(type: TxType): List<FastiflyAccount> = accounts.filter { account ->
        account.isActive && when (type) {
            TxType.Income -> account.kind == "revenue"
            else -> account.kind == "asset" || account.kind == "liability"
        }
    }

    private fun destinationAccountsFor(type: TxType, fromId: String): List<FastiflyAccount> {
        if (type == TxType.Expense) return emptyList()
        val source = accounts.firstOrNull { it.id == fromId }
        return accounts.filter { account ->
            account.isActive &&
                account.id != fromId &&
                (account.kind == "asset" || account.kind == "liability") &&
                (source == null || account.currencyCode == source.currencyCode)
        }
    }

    private fun expenseCategories(): List<FastiflyCategory> {
        val activeAccountIds = accounts.filter { it.isActive }.map { it.id }.toSet()
        return categories.filter { it.counterpartyAccountId != null && it.counterpartyAccountId in activeAccountIds }
    }

    private fun statusLine(): Pair<String?, WidgetStatusSeverity> {
        val unsynced = pending.size
        return when (phase) {
            // The body's empty-state already explains these — no duplicate line.
            Phase.Unconfigured, Phase.Loading, Phase.Error -> null to WidgetStatusSeverity.Info
            Phase.NeedsSetup -> "Set up accounts & categories in Fastifly web" to WidgetStatusSeverity.Warning
            Phase.AuthFailed ->
                (if (context == null) null else lastError ?: "Fastifly credentials rejected - update settings") to
                    WidgetStatusSeverity.Error
            Phase.Offline ->
                (if (unsynced > 0) "Offline — $unsynced queued" else "Offline — showing cached data") to WidgetStatusSeverity.Warning
            Phase.Ready ->
                (if (unsynced > 0) "$unsynced syncing…" else null) to WidgetStatusSeverity.Info
        }
    }

    // --- Time helpers ------------------------------------------------------

    private fun monthShort(): String = monthShortName(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).month,
    )

    private fun monthStartIso(): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = (today.month.ordinal + 1).toString().padStart(2, '0')
        // UTC midnight on the 1st as the month lower bound — adequate for a
        // current-month tile summary without per-zone instant conversion.
        return "${today.year}-$month-01T00:00:00.000Z"
    }

    private fun shortDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "${monthShortName(date.month)} ${date.day}"
    }

    private fun monthShortName(month: Month): String = when (month) {
        Month.JANUARY -> "Jan"; Month.FEBRUARY -> "Feb"; Month.MARCH -> "Mar"
        Month.APRIL -> "Apr"; Month.MAY -> "May"; Month.JUNE -> "Jun"
        Month.JULY -> "Jul"; Month.AUGUST -> "Aug"; Month.SEPTEMBER -> "Sep"
        Month.OCTOBER -> "Oct"; Month.NOVEMBER -> "Nov"; Month.DECEMBER -> "Dec"
    }

    private fun randomToken(): String =
        (1..16).joinToString("") { Random.nextInt(16).toString(16) }

    // --- Persistence (offline cache + outbox) ------------------------------

    private fun saveCache() {
        val snapshot = viewState.toCacheSnapshot()
        scope.coroutineScope.launch {
            scope.storage.write(CACHE_KEY, json.encodeToString(CacheSnapshot.serializer(), snapshot))
        }
    }

    private suspend fun readCache(): CacheSnapshot? {
        val raw = scope.storage.read(CACHE_KEY, String::class.java) ?: return null
        return runCatching { json.decodeFromString(CacheSnapshot.serializer(), raw) }.getOrNull()
    }

    private suspend fun readOutbox(): List<PendingTransaction> =
        scope.storage.read(OUTBOX_KEY, String::class.java)
            ?.let { runCatching { json.decodeFromString(pendingSerializer, it) }.getOrNull() }
            ?: emptyList()

    private suspend fun writeOutbox(items: List<PendingTransaction>) {
        if (items.isEmpty()) scope.storage.delete(OUTBOX_KEY)
        else scope.storage.write(OUTBOX_KEY, json.encodeToString(pendingSerializer, items))
    }

    private companion object {
        const val CACHE_KEY = "fastifly-expenses-cache.json"
        const val OUTBOX_KEY = "fastifly-expenses-outbox.json"
    }
}

private data class DisplayRow(
    val glyph: String,
    val label: String,
    val amountMinor: Long,
    val dateLabel: String,
    val income: Boolean,
    val pending: Boolean,
)

@Serializable
private data class CacheSnapshot(
    val context: MeContext? = null,
    val accounts: List<FastiflyAccount> = emptyList(),
    val categories: List<FastiflyCategory> = emptyList(),
    val recent: List<FastiflyTransactionGroup> = emptyList(),
)

@Serializable
private data class PendingTransaction(
    val idempotencyKey: String,
    val request: CreateTransactionRequest,
    val displayLabel: String,
    val displayType: String,
    val displayAmountMinor: Long,
    val displayOccurredAt: String,
)
