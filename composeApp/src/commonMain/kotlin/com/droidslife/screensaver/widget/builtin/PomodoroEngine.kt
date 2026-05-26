package com.droidslife.screensaver.widget.builtin

import kotlinx.serialization.Serializable

/** Phase the timer is currently in. */
enum class PomodoroPhase { Idle, Work, ShortBreak, LongBreak }

/** Immutable phase/cycle state. Persisted via [PomodoroSnapshot]. */
@Serializable
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.Idle,
    val phaseDurationSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val paused: Boolean = false,
    val completedWorkCycles: Int = 0,
    val focusLabel: String = "",
)

/** Durations + cadence derived from widget config. */
data class PomodoroSettings(
    val workSeconds: Int,
    val shortBreakSeconds: Int,
    val longBreakSeconds: Int,
    val cyclesUntilLongBreak: Int,
)

/** Title/body for the phase-change alert. */
data class PhaseAlert(val title: String, val message: String)

/**
 * Result of a phase transition: the next state, whether a Work phase elapsed
 * naturally (credit toward the day's count), and an optional alert (null when
 * the user skipped rather than letting the phase elapse).
 */
data class PomodoroTransition(
    val state: PomodoroState,
    val workCycleCompleted: Boolean = false,
    val alert: PhaseAlert? = null,
)

/** Pure phase state machine. No clock, no Compose — fully unit-testable. */
object PomodoroEngine {

    fun start(state: PomodoroState, s: PomodoroSettings): PomodoroState =
        state.copy(
            phase = PomodoroPhase.Work,
            phaseDurationSeconds = s.workSeconds,
            remainingSeconds = s.workSeconds,
            paused = false,
        )

    /** No-op on Idle; otherwise flips the paused flag. */
    fun pauseResume(state: PomodoroState): PomodoroState =
        if (state.phase == PomodoroPhase.Idle) state else state.copy(paused = !state.paused)

    /** Returns to Idle and zeroes cycles, but keeps the user's focus label. */
    fun reset(state: PomodoroState): PomodoroState = PomodoroState(focusLabel = state.focusLabel)

    /** Phase elapsed naturally: counts a completed work cycle and emits an alert. */
    fun complete(state: PomodoroState, s: PomodoroSettings): PomodoroTransition =
        transition(state, s, natural = true)

    /** User skipped: advances phases but earns no cycle credit and no alert. */
    fun skip(state: PomodoroState, s: PomodoroSettings): PomodoroTransition =
        transition(state, s, natural = false)

    private fun transition(state: PomodoroState, s: PomodoroSettings, natural: Boolean): PomodoroTransition {
        return when (state.phase) {
            PomodoroPhase.Idle -> PomodoroTransition(state)

            PomodoroPhase.Work -> {
                // Only a naturally-elapsed work phase counts toward the ledger.
                val cycles = if (natural) state.completedWorkCycles + 1 else state.completedWorkCycles
                val longBreak = cycles > 0 && cycles % s.cyclesUntilLongBreak == 0
                val duration = if (longBreak) s.longBreakSeconds else s.shortBreakSeconds
                val next = state.copy(
                    phase = if (longBreak) PomodoroPhase.LongBreak else PomodoroPhase.ShortBreak,
                    phaseDurationSeconds = duration,
                    remainingSeconds = duration,
                    paused = false,
                    completedWorkCycles = cycles,
                )
                val alert = if (natural) {
                    val minutes = if (longBreak) s.longBreakSeconds / 60 else s.shortBreakSeconds / 60
                    PhaseAlert(
                        title = if (longBreak) "Long break" else "Break time",
                        message = "Nice work — step away for $minutes min.",
                    )
                } else null
                PomodoroTransition(next, workCycleCompleted = natural, alert = alert)
            }

            PomodoroPhase.ShortBreak, PomodoroPhase.LongBreak -> {
                val next = state.copy(
                    phase = PomodoroPhase.Work,
                    phaseDurationSeconds = s.workSeconds,
                    remainingSeconds = s.workSeconds,
                    paused = false,
                )
                val alert = if (natural) PhaseAlert("Back to work", "Break's over — focus time.") else null
                PomodoroTransition(next, workCycleCompleted = false, alert = alert)
            }
        }
    }
}
