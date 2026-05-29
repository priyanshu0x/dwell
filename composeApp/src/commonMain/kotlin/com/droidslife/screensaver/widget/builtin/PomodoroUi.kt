package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.droidslife.screensaver.components.pausesShortcutsWhileFocused
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.ui.DwellActionButton
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tiny = maxWidth < 320.dp || maxHeight < 180.dp
        val compact = tiny || maxWidth < 420.dp || maxHeight < 230.dp
        val showFocus = maxWidth >= 280.dp && maxHeight >= 165.dp
        val showStats = maxWidth >= 380.dp && maxHeight >= 200.dp
        val showSparkline = maxWidth >= 440.dp && maxHeight >= 225.dp
        val ringLimit = min(maxWidth.value, maxHeight.value).dp
        val ringSize = min(
            maxWidth.value * when {
                tiny -> 0.42f
                compact -> 0.48f
                else -> 0.55f
            },
            maxHeight.value * when {
                tiny -> 0.46f
                compact -> 0.56f
                else -> 0.66f
            },
        ).dp
            .coerceAtLeast(if (tiny) 72.dp else 104.dp)
            .coerceAtMost(ringLimit)
        val timerSize = when {
            tiny -> 24.sp
            compact -> 32.sp
            else -> 40.sp
        }
        val footerMaxWidth = if (showStats) 0.64f else 1f

        WidgetHeader(
            label = "POMODORO · $cycle/$cyclesUntilLongBreak",
            settingsId = "com.droidslife.screensaver.pomodoro",
            modifier = Modifier.align(Alignment.TopStart),
        )

        // Center: ring with MM:SS (or READY) and the editable focus label.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(
                    top = if (tiny) 18.dp else 22.dp,
                    bottom = if (tiny) 28.dp else 34.dp,
                )
                .size(ringSize)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(
                progress = progressOf(state),
                color = if (isRunning) accent else DwellColors.TextLow,
                track = DwellColors.Stroke,
                strokeWidth = when {
                    tiny -> 3.dp
                    compact -> 4.dp
                    else -> 5.dp
                },
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.phase == PomodoroPhase.Idle) "READY" else formatTime(state.remainingSeconds),
                    fontFamily = DwellFonts.jetBrainsMono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = timerSize,
                    color = if (isRunning) accent else DwellColors.TextHigh,
                    maxLines = 1,
                )
                if (showFocus) {
                    FocusLabel(
                        label = state.focusLabel,
                        fontSize = if (compact) 10.sp else 11.sp,
                        onChange = onLabelChange,
                    )
                }
            }
        }

        // Bottom-left: phase tag + controls.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (tiny) 4.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(footerMaxWidth),
        ) {
            PhaseTag(
                text = phaseLabel(state.phase).uppercase(),
                compact = compact,
                modifier = Modifier.padding(top = if (tiny) 4.dp else 5.dp, end = 2.dp),
            )
            when (state.phase) {
                PomodoroPhase.Idle -> ControlChip("Start", primary = true, compact = compact, tiny = tiny, onClick = onStart)
                else -> {
                    ControlChip(if (state.paused) "Resume" else "Pause", primary = true, compact = compact, tiny = tiny, onClick = onPauseResume)
                    ControlChip("Skip", primary = false, compact = compact, tiny = tiny, onClick = onSkip)
                    ControlChip("Reset", primary = false, compact = compact, tiny = tiny, onClick = onReset)
                }
            }
        }

        // Bottom-right: today/week counts + 7-day sparkline.
        if (showStats) {
            StatsFooter(
                history = history,
                compact = compact,
                showSparkline = showSparkline,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun FocusLabel(label: String, fontSize: androidx.compose.ui.unit.TextUnit, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        var text by remember { mutableStateOf(label) }
        // Track gained-focus so the first (unfocused) onFocusChanged callback,
        // which fires before requestFocus lands, doesn't close the field early.
        var hadFocus by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            cursorBrush = SolidColor(DwellColors.TextMid),
            textStyle = TextStyle(
                color = DwellColors.TextMid,
                fontSize = fontSize,
                fontFamily = DwellFonts.interTight(),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .padding(top = 2.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) hadFocus = true
                    else if (hadFocus) { editing = false; onChange(text) }
                }
                .pausesShortcutsWhileFocused()
                .onPreviewKeyClose { editing = false; onChange(text) },
        )
    } else {
        Text(
            text = label.ifBlank { "+ add focus" },
            fontFamily = DwellFonts.interTight(),
            fontSize = fontSize,
            color = if (label.isBlank()) DwellColors.TextLow else DwellColors.TextMid,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp).clickable { editing = true },
        )
    }
}

@Composable
private fun StatsFooter(
    history: PomodoroHistory,
    compact: Boolean,
    showSparkline: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = todayLocalDate()
    val todayCount = history.countOn(today)
    val weekCount = history.countInLastDays(today, 7)
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = "TODAY $todayCount   WEEK $weekCount",
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 8.sp else 9.sp,
            letterSpacing = if (compact) 1.sp else 1.5.sp,
            color = DwellColors.TextLow,
        )
        if (showSparkline) {
            Sparkline(
                history.last7(today),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(width = if (compact) 56.dp else 70.dp, height = if (compact) 11.dp else 14.dp),
            )
        }
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
private fun PhaseTag(text: String, compact: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = DwellFonts.interTight(),
        fontWeight = FontWeight.SemiBold,
        fontSize = if (compact) 8.sp else 9.sp,
        letterSpacing = if (compact) 1.4.sp else 2.25.sp,
        color = DwellColors.TextLow,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun ControlChip(label: String, primary: Boolean, compact: Boolean, tiny: Boolean, onClick: () -> Unit) {
    DwellActionButton(
        label = label,
        onClick = onClick,
        primary = primary,
        minWidth = when {
            tiny -> if (label.length > 5) 54.dp else 40.dp
            compact -> if (label.length > 5) 58.dp else 44.dp
            else -> if (label.length > 5) 62.dp else 48.dp
        },
        height = if (tiny) 22.dp else 24.dp,
        fontSize = if (tiny) 10.sp else 11.sp,
        horizontalPadding = if (tiny) 7.dp else 8.dp,
        cornerRadius = if (tiny) 7.dp else 8.dp,
    )
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

/** Local calendar date "now" — shared by the tile stats and the widget's ledger. */
internal fun todayLocalDate(): kotlinx.datetime.LocalDate =
    kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        .date
