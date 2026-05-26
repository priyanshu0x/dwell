package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return PomodoroWidget(config, scope)
    }
}

/** Phase the timer is currently in. */
private enum class PomodoroPhase { Idle, Work, ShortBreak, LongBreak }

/** Serialized snapshot persisted between runs. */
@Serializable
private data class PomodoroSnapshot(
    val phase: String,
    val remainingSeconds: Int,
    val paused: Boolean,
    val completedWorkCycles: Int,
)

private class PomodoroWidget(
    private val config: WidgetConfig,
    private val scope: WidgetScope,
) : Widget {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var phase by mutableStateOf(PomodoroPhase.Idle)
    private var remainingSeconds by mutableStateOf(0)
    private var paused by mutableStateOf(false)
    private var completedWorkCycles by mutableStateOf(0)
    private var loaded by mutableStateOf(false)

    private val workSeconds: Int get() = config.int("workMinutes", 25).coerceAtLeast(1) * 60
    private val shortBreakSeconds: Int get() = config.int("shortBreakMinutes", 5).coerceAtLeast(1) * 60
    private val longBreakSeconds: Int get() = config.int("longBreakMinutes", 15).coerceAtLeast(1) * 60
    private val cyclesUntilLongBreak: Int get() = config.int("cyclesUntilLongBreak", 4).coerceAtLeast(2)

    override fun summary(): WidgetSummary {
        return WidgetSummary(
            primaryValue = if (phase == PomodoroPhase.Idle) "--:--" else formatTime(remainingSeconds),
            primaryLabel = "Pomodoro",
            subtitle = "${phaseLabel()} · ${displayCycle()}/$cyclesUntilLongBreak",
        )
    }

    override fun onResume() {
        if (loaded) return
        scope.coroutineScope.launch {
            val raw = runCatching {
                scope.storage.read("pomodoro.json", String::class.java)
            }.getOrNull()
            val snap = raw?.let {
                runCatching { json.decodeFromString(PomodoroSnapshot.serializer(), it) }.getOrNull()
            }
            if (snap != null) {
                phase = parsePhase(snap.phase)
                remainingSeconds = snap.remainingSeconds.coerceAtLeast(0)
                paused = snap.paused
                completedWorkCycles = snap.completedWorkCycles.coerceAtLeast(0)
            }
            loaded = true
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val accent = LocalConsoleAccent.current.primary
        val isRunning = phase != PomodoroPhase.Idle && !paused

        // Ticker lives here so it's automatically cancelled when the widget
        // leaves composition (drawer closes, settings toggle off, etc.).
        LaunchedEffect(phase, paused, loaded) {
            if (!loaded) return@LaunchedEffect
            if (phase == PomodoroPhase.Idle || paused) return@LaunchedEffect
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
                if (remainingSeconds <= 0) {
                    advancePhase()
                    persist()
                    break
                }
                // Persist every 15s so a crash mid-phase doesn't lose much.
                if (remainingSeconds % 15 == 0) persist()
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            WidgetHeader(
                label = "POMODORO · ${displayCycle()}/$cyclesUntilLongBreak",
                settingsId = WIDGET_ID,
                modifier = Modifier.align(Alignment.TopStart),
            )

            // Center: big MM:SS or READY
            val centerText = if (phase == PomodoroPhase.Idle) "READY" else formatTime(remainingSeconds)
            Text(
                text = centerText,
                fontFamily = DwellFonts.jetBrainsMono(),
                fontWeight = FontWeight.Medium,
                fontSize = 64.sp,
                color = if (isRunning) accent else DwellColors.TextHigh,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )

            // Bottom-left: phase + controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                PhaseTag(text = phaseLabel().uppercase())
                when (phase) {
                    PomodoroPhase.Idle -> ControlChip("Start", accent) { start() }
                    else -> {
                        ControlChip(if (paused) "Resume" else "Pause", accent) {
                            paused = !paused
                            persist()
                        }
                        ControlChip("Skip", DwellColors.TextMid) { skip() }
                        ControlChip("Reset", DwellColors.TextMid) { reset() }
                    }
                }
            }
        }
    }

    // ── State transitions ─────────────────────────────────────────────

    private fun start() {
        phase = PomodoroPhase.Work
        remainingSeconds = workSeconds
        paused = false
        persist()
    }

    private fun skip() {
        // Skip behaves like the phase elapsed naturally (no bell, no count if
        // the user is bailing early — only completed natural cycles count
        // toward the long-break ledger).
        when (phase) {
            PomodoroPhase.Idle -> Unit
            PomodoroPhase.Work -> beginBreak()
            PomodoroPhase.ShortBreak, PomodoroPhase.LongBreak -> beginWork()
        }
        paused = false
        persist()
    }

    private fun reset() {
        phase = PomodoroPhase.Idle
        remainingSeconds = 0
        paused = false
        completedWorkCycles = 0
        persist()
    }

    private fun advancePhase() {
        when (phase) {
            PomodoroPhase.Idle -> Unit
            PomodoroPhase.Work -> {
                completedWorkCycles += 1
                beginBreak()
                playPomodoroBell()
            }
            PomodoroPhase.ShortBreak, PomodoroPhase.LongBreak -> {
                beginWork()
                playPomodoroBell()
            }
        }
        paused = false
    }

    private fun beginBreak() {
        phase = if (completedWorkCycles > 0 && completedWorkCycles % cyclesUntilLongBreak == 0) {
            PomodoroPhase.LongBreak
        } else {
            PomodoroPhase.ShortBreak
        }
        remainingSeconds = if (phase == PomodoroPhase.LongBreak) longBreakSeconds else shortBreakSeconds
    }

    private fun beginWork() {
        phase = PomodoroPhase.Work
        remainingSeconds = workSeconds
    }

    // ── Display helpers ───────────────────────────────────────────────

    private fun phaseLabel(): String = when (phase) {
        PomodoroPhase.Idle -> "Idle"
        PomodoroPhase.Work -> "Work"
        PomodoroPhase.ShortBreak -> "Break"
        PomodoroPhase.LongBreak -> "Long Break"
    }

    /** Position in the current "block" of N work cycles, 1-indexed. */
    private fun displayCycle(): Int {
        val ledger = completedWorkCycles % cyclesUntilLongBreak
        return when (phase) {
            // When working, show "this is cycle N+1 of the block".
            PomodoroPhase.Work -> ledger + 1
            // On break, the just-completed cycle is what we count from.
            PomodoroPhase.ShortBreak, PomodoroPhase.LongBreak ->
                if (ledger == 0) cyclesUntilLongBreak else ledger
            PomodoroPhase.Idle -> ledger.coerceAtLeast(1).coerceAtMost(cyclesUntilLongBreak)
        }
    }

    // ── Persistence ───────────────────────────────────────────────────

    private fun persist() {
        val snapshot = PomodoroSnapshot(
            phase = phase.name,
            remainingSeconds = remainingSeconds,
            paused = paused,
            completedWorkCycles = completedWorkCycles,
        )
        val encoded = json.encodeToString(PomodoroSnapshot.serializer(), snapshot)
        scope.coroutineScope.launch {
            runCatching { scope.storage.write("pomodoro.json", encoded) }
                .onFailure { scope.log.warn("Pomodoro persist failed", it) }
        }
    }

    private fun parsePhase(name: String): PomodoroPhase = runCatching {
        PomodoroPhase.valueOf(name)
    }.getOrDefault(PomodoroPhase.Idle)
}

private fun formatTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "${m.toString().padStart(2, '0')}:${r.toString().padStart(2, '0')}"
}

@Composable
private fun PhaseTag(text: String) {
    Text(
        text = text,
        fontFamily = DwellFonts.interTight(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 2.25.sp,
        color = DwellColors.TextLow,
    )
}

@Composable
private fun ControlChip(label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = tint,
        )
    }
}
