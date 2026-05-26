package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlinx.datetime.toLocalDateTime

private fun progressOf(state: PomodoroState): Float =
    if (state.phaseDurationSeconds <= 0) 0f
    else (state.remainingSeconds.toFloat() / state.phaseDurationSeconds).coerceIn(0f, 1f)

/** Circular arc that depletes clockwise as the phase elapses. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    track: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val diameter = size.minDimension - sw
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(sw))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(sw))
    }
}

/** Full Console tile: ring + MM:SS + editable focus label + controls + stats. */
@Composable
fun PomodoroTile(
    state: PomodoroState,
    history: PomodoroHistory,
    cyclesUntilLongBreak: Int,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onLabelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalConsoleAccent.current.primary
    val isRunning = state.phase != PomodoroPhase.Idle && !state.paused
    val cycle = displayCycle(state, cyclesUntilLongBreak)

    Box(modifier = modifier.fillMaxSize()) {
        WidgetHeader(
            label = "POMODORO · $cycle/$cyclesUntilLongBreak",
            settingsId = "com.droidslife.screensaver.pomodoro",
            modifier = Modifier.align(Alignment.TopStart),
        )

        // Center: ring with MM:SS (or READY) and the editable focus label.
        Box(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.55f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(
                progress = progressOf(state),
                color = if (isRunning) accent else DwellColors.TextLow,
                track = DwellColors.Stroke,
                strokeWidth = 5.dp,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.phase == PomodoroPhase.Idle) "READY" else formatTime(state.remainingSeconds),
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 40.sp,
                    color = if (isRunning) accent else DwellColors.TextHigh,
                    maxLines = 1,
                )
                FocusLabel(state.focusLabel, onLabelChange)
            }
        }

        // Bottom-left: phase tag + controls.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            PhaseTag(phaseLabel(state.phase).uppercase())
            when (state.phase) {
                PomodoroPhase.Idle -> ControlChip("Start", accent, onStart)
                else -> {
                    ControlChip(if (state.paused) "Resume" else "Pause", accent, onPauseResume)
                    ControlChip("Skip", DwellColors.TextMid, onSkip)
                    ControlChip("Reset", DwellColors.TextMid, onReset)
                }
            }
        }

        // Bottom-right: today/week counts + 7-day sparkline.
        StatsFooter(history, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun FocusLabel(label: String, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        var text by remember { mutableStateOf(label) }
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            cursorBrush = SolidColor(DwellColors.TextMid),
            textStyle = TextStyle(
                color = DwellColors.TextMid,
                fontSize = 11.sp,
                fontFamily = DwellFonts.interTight(),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable(enabled = false) {}
                .onPreviewKeyClose { editing = false; onChange(text) },
        )
    } else {
        Text(
            text = label.ifBlank { "+ add focus" },
            fontFamily = DwellFonts.interTight(),
            fontSize = 11.sp,
            color = if (label.isBlank()) DwellColors.TextLow else DwellColors.TextMid,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp).clickable { editing = true },
        )
    }
}

@Composable
private fun StatsFooter(history: PomodoroHistory, modifier: Modifier = Modifier) {
    val today = todayLocalDate()
    val todayCount = history.countOn(today)
    val weekCount = history.countInLastDays(today, 7)
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = "TODAY $todayCount   WEEK $weekCount",
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = DwellColors.TextLow,
        )
        Sparkline(history.last7(today), modifier = Modifier.padding(top = 4.dp).size(width = 70.dp, height = 14.dp))
    }
}

@Composable
private fun Sparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val accent = LocalConsoleAccent.current.primary
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val h = (size.height * (v.toFloat() / max)).coerceAtLeast(if (v > 0) 2f else 1f)
            val x = i * (barWidth + gap)
            drawRect(
                color = if (v > 0) accent else DwellColors.Stroke,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
}

/** Compact one-row chip for the Cinematic drawer. */
@Composable
fun PomodoroChip(state: PomodoroState, modifier: Modifier = Modifier) {
    val accent = LocalConsoleAccent.current.primary
    val isRunning = state.phase != PomodoroPhase.Idle && !state.paused
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (isRunning) accent else DwellColors.TextLow))
        Text(
            text = if (state.phase == PomodoroPhase.Idle) "--:--" else formatTime(state.remainingSeconds),
            fontSize = 14.sp,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
        )
        val tail = state.focusLabel.ifBlank { phaseLabel(state.phase) }
        Text(
            text = tail,
            fontSize = 10.sp,
            color = DwellColors.TextMid,
            maxLines = 1,
            fontFamily = DwellFonts.interTight(),
        )
    }
}

/** Single dim line for Ambient mode. */
@Composable
fun PomodoroMinimalLine(state: PomodoroState, modifier: Modifier = Modifier) {
    if (state.phase == PomodoroPhase.Idle) return
    val parts = listOf(
        phaseLabel(state.phase).lowercase(),
        formatTime(state.remainingSeconds),
    ) + if (state.focusLabel.isNotBlank()) listOf(state.focusLabel) else emptyList()
    Text(
        text = "● " + parts.joinToString(" · "),
        fontSize = 12.sp,
        color = DwellColors.TextLow,
        fontFamily = DwellFonts.interTight(),
        modifier = modifier,
    )
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

/** Position within the current block of N work cycles, 1-indexed, for the header. */
private fun displayCycle(state: PomodoroState, cyclesUntilLongBreak: Int): Int {
    val ledger = state.completedWorkCycles % cyclesUntilLongBreak
    return when (state.phase) {
        PomodoroPhase.Work -> ledger + 1
        PomodoroPhase.ShortBreak, PomodoroPhase.LongBreak ->
            if (ledger == 0) cyclesUntilLongBreak else ledger
        PomodoroPhase.Idle -> ledger.coerceAtLeast(1).coerceAtMost(cyclesUntilLongBreak)
    }
}

// Commit the label edit on Enter or Esc. Keeps the focus-label interaction
// self-contained so PomodoroTile doesn't need a key-event plumbing detour.
private fun Modifier.onPreviewKeyClose(onClose: () -> Unit): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.Escape)) {
        onClose(); true
    } else {
        false
    }
}

private fun todayLocalDate(): kotlinx.datetime.LocalDate =
    kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        .date
