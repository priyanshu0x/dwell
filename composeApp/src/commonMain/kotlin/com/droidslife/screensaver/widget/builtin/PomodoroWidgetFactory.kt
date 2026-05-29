package com.droidslife.screensaver.widget.builtin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.serialization.DwellJson
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.time.Clock

private const val WIDGET_ID = "com.droidslife.screensaver.pomodoro"

class PomodoroWidgetFactory : WidgetFactory {
    override val id: String = WIDGET_ID
    override val displayName: String = "Pomodoro"
    override val description: String = "Local 25/5/15 pomodoro timer with cycle tracking"
    override val category: WidgetCategory = WidgetCategory.PRODUCTIVITY
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 2,
        defaultCols = 4, defaultRows = 2,
        maxCols = 6, maxRows = 3,
    )
    override val configSchema: List<ConfigField> = listOf(
        ConfigField.IntField(
            key = "workMinutes",
            label = "Work minutes",
            default = 25,
            min = 1,
            max = 180,
        ),
        ConfigField.IntField(
            key = "shortBreakMinutes",
            label = "Short break minutes",
            default = 5,
            min = 1,
            max = 60,
        ),
        ConfigField.IntField(
            key = "longBreakMinutes",
            label = "Long break minutes",
            default = 15,
            min = 1,
            max = 120,
        ),
        ConfigField.IntField(
            key = "cyclesUntilLongBreak",
            label = "Cycles until long break",
            default = 4,
            min = 2,
            max = 12,
        ),
        ConfigField.Bool(
            key = "enableSound",
            label = "Play a chime when a phase ends",
            default = true,
        ),
        ConfigField.Bool(
            key = "enableNotifications",
            label = "Show a desktop notification when a phase ends",
            default = true,
        ),
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return PomodoroWidget(config, scope)
    }
}

/** Snapshot persisted between runs: logical state plus the wall-clock end time. */
@Serializable
private data class PomodoroSnapshot(
    val state: PomodoroState,
    val endEpochMillis: Long?,
)

private class PomodoroWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
) : Widget {
    private val json = DwellJson.Persisted

    var state by mutableStateOf(PomodoroState())
        private set
    var history by mutableStateOf(PomodoroHistory())
        private set

    private var endEpochMillis: Long? = null
    private var loaded = false
    private var tickerJob: Job? = null

    override val rendersOwnChip: Boolean = true
    override val rendersOwnMinimal: Boolean = true

    private val settings: PomodoroSettings
        get() = PomodoroSettings(
            workSeconds = config.int("workMinutes", 25).coerceAtLeast(1) * 60,
            shortBreakSeconds = config.int("shortBreakMinutes", 5).coerceAtLeast(1) * 60,
            longBreakSeconds = config.int("longBreakMinutes", 15).coerceAtLeast(1) * 60,
            cyclesUntilLongBreak = config.int("cyclesUntilLongBreak", 4).coerceAtLeast(2),
        )

    private val soundEnabled: Boolean get() = config.bool("enableSound", true)
    private val notificationsEnabled: Boolean get() = config.bool("enableNotifications", true)

    override fun summary(): WidgetSummary = WidgetSummary(
        primaryValue = if (state.phase == PomodoroPhase.Idle) "--:--" else formatTime(state.remainingSeconds),
        primaryLabel = "Pomodoro",
        subtitle = phaseLabel(state.phase),
    )

    init {
        // The host doesn't drive onResume(), and the timer must keep running for
        // as long as the instance is enabled — regardless of which mode is
        // visible — so load persisted state and resume as soon as we're created.
        ensureLoaded()
    }

    override fun onResume() = ensureLoaded()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true // set synchronously so a second call can't launch a duplicate load
        scope.coroutineScope.launch {
            val snap = runCatching { scope.storage.read("pomodoro.json", String::class.java) }.getOrNull()
                ?.let { runCatching { json.decodeFromString(PomodoroSnapshot.serializer(), it) }.getOrNull() }
            if (snap != null) {
                state = snap.state
                endEpochMillis = snap.endEpochMillis
            }
            val hist = runCatching { scope.storage.read("pomodoro-history.json", String::class.java) }.getOrNull()
                ?.let { runCatching { json.decodeFromString(PomodoroHistory.serializer(), it) }.getOrNull() }
            if (hist != null) history = hist
            reconcileAfterLoad()
        }
    }

    override fun onDispose() {
        // No final persist here: state is already written on every transition,
        // control, and ~15s tick, and the host cancels our scope immediately
        // after this returns — so a launched write wouldn't run anyway.
        tickerJob?.cancel()
    }

    @Composable
    override fun Render(target: WidgetRenderTarget, scope: WidgetScope, modifier: Modifier) {
        when (target) {
            WidgetRenderTarget.Tile -> PomodoroTile(
                state = state,
                history = history,
                cyclesUntilLongBreak = settings.cyclesUntilLongBreak,
                onStart = ::start,
                onPauseResume = ::togglePause,
                onSkip = ::skip,
                onReset = ::reset,
                onLabelChange = ::setFocusLabel,
                modifier = modifier,
            )
            WidgetRenderTarget.Chip -> PomodoroChip(state, modifier)
            WidgetRenderTarget.Minimal -> PomodoroMinimalLine(state, modifier)
        }
    }

    // ── Controls ──────────────────────────────────────────────────────

    private fun start() { state = PomodoroEngine.start(state, settings); enterRunning() }

    private fun togglePause() {
        state = PomodoroEngine.pauseResume(state)
        if (state.paused) {
            endEpochMillis = null
            tickerJob?.cancel()
        } else {
            endEpochMillis = nowMs() + state.remainingSeconds * 1000L
            restartTicker()
        }
        persist()
    }

    private fun skip() { state = PomodoroEngine.skip(state, settings).state; enterRunning() }

    private fun reset() {
        state = PomodoroEngine.reset(state)
        endEpochMillis = null
        tickerJob?.cancel()
        persist()
    }

    private fun setFocusLabel(label: String) {
        state = state.copy(focusLabel = label.take(40))
        persist()
    }

    /** Common path after a control puts us into a fresh running phase. */
    private fun enterRunning() {
        endEpochMillis = nowMs() + state.remainingSeconds * 1000L
        persist()
        restartTicker()
    }

    // ── Ticker (drift-free, lifecycle-bound) ──────────────────────────

    private fun restartTicker() {
        tickerJob?.cancel()
        if (state.phase == PomodoroPhase.Idle || state.paused) return
        tickerJob = scope.coroutineScope.launch {
            while (isActive) {
                val end = endEpochMillis ?: break
                val remaining = ceil((end - nowMs()) / 1000.0).toInt().coerceAtLeast(0)
                if (remaining <= 0) { onPhaseElapsed(); break }
                if (remaining != state.remainingSeconds) {
                    state = state.copy(remainingSeconds = remaining)
                    if (remaining % 15 == 0) persist()
                }
                delay(200)
            }
        }
    }

    private fun onPhaseElapsed() {
        val before = state
        val transition = PomodoroEngine.complete(before, settings)
        state = transition.state
        if (transition.workCycleCompleted) recordSession(before)
        transition.alert?.let { fire(it) }
        endEpochMillis = nowMs() + state.remainingSeconds * 1000L
        persist()
        restartTicker()
    }

    /**
     * After loading a snapshot, fast-forward a single elapsed phase if the timer
     * ran out while the app was closed, then resume ticking.
     */
    private fun reconcileAfterLoad() {
        if (state.phase == PomodoroPhase.Idle || state.paused) return
        val end = endEpochMillis ?: return
        if (nowMs() >= end) onPhaseElapsed() else restartTicker()
    }

    // ── Side effects ──────────────────────────────────────────────────

    private fun recordSession(completed: PomodoroState) {
        history = history.record(
            todayLocalDate(),
            PomodoroSession(nowMs(), completed.focusLabel, settings.workSeconds),
        )
        persistHistory()
    }

    private fun fire(alert: PhaseAlert) {
        firePomodoroAlert(
            PomodoroAlert(
                title = alert.title,
                message = alert.message,
                playSound = soundEnabled,
                showNotification = notificationsEnabled,
            ),
        )
    }

    // ── Persistence ───────────────────────────────────────────────────

    private fun persist() {
        val encoded = json.encodeToString(PomodoroSnapshot.serializer(), PomodoroSnapshot(state, endEpochMillis))
        scope.coroutineScope.launch {
            runCatching { scope.storage.write("pomodoro.json", encoded) }
                .onFailure { scope.log.warn("Pomodoro persist failed", it) }
        }
    }

    private fun persistHistory() {
        val encoded = json.encodeToString(PomodoroHistory.serializer(), history)
        scope.coroutineScope.launch {
            runCatching { scope.storage.write("pomodoro-history.json", encoded) }
                .onFailure { scope.log.warn("Pomodoro history persist failed", it) }
        }
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}

internal fun formatTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

internal fun phaseLabel(phase: PomodoroPhase): String = when (phase) {
    PomodoroPhase.Idle -> "Idle"
    PomodoroPhase.Work -> "Work"
    PomodoroPhase.ShortBreak -> "Break"
    PomodoroPhase.LongBreak -> "Long Break"
}
