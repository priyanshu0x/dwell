package com.droidslife.screensaver.widget.builtin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val S = PomodoroSettings(
    workSeconds = 25 * 60,
    shortBreakSeconds = 5 * 60,
    longBreakSeconds = 15 * 60,
    cyclesUntilLongBreak = 4,
)

class PomodoroEngineTest {

    @Test
    fun startEntersWorkWithFullDuration() {
        val s = PomodoroEngine.start(PomodoroState(), S)
        assertEquals(PomodoroPhase.Work, s.phase)
        assertEquals(S.workSeconds, s.remainingSeconds)
        assertEquals(S.workSeconds, s.phaseDurationSeconds)
        assertFalse(s.paused)
    }

    @Test
    fun completingWorkGoesToShortBreakAndCountsCycle() {
        val work = PomodoroEngine.start(PomodoroState(), S)
        val t = PomodoroEngine.complete(work, S)
        assertEquals(PomodoroPhase.ShortBreak, t.state.phase)
        assertEquals(1, t.state.completedWorkCycles)
        assertEquals(S.shortBreakSeconds, t.state.remainingSeconds)
        assertEquals(S.shortBreakSeconds, t.state.phaseDurationSeconds)
        assertTrue(t.workCycleCompleted)
        assertNotNull(t.alert)
    }

    @Test
    fun fourthCompletedWorkTriggersLongBreak() {
        var state = PomodoroState(completedWorkCycles = 3, phase = PomodoroPhase.Work, remainingSeconds = 1)
        val t = PomodoroEngine.complete(state, S)
        assertEquals(PomodoroPhase.LongBreak, t.state.phase)
        assertEquals(4, t.state.completedWorkCycles)
        assertEquals(S.longBreakSeconds, t.state.remainingSeconds)
        assertEquals(S.longBreakSeconds, t.state.phaseDurationSeconds)
    }

    @Test
    fun completingBreakReturnsToWorkWithoutCounting() {
        val state = PomodoroState(phase = PomodoroPhase.ShortBreak, completedWorkCycles = 1, remainingSeconds = 1)
        val t = PomodoroEngine.complete(state, S)
        assertEquals(PomodoroPhase.Work, t.state.phase)
        assertEquals(1, t.state.completedWorkCycles)
        assertFalse(t.workCycleCompleted)
        assertNotNull(t.alert)
    }

    @Test
    fun skipWorkAdvancesWithoutCountingOrAlerting() {
        val work = PomodoroEngine.start(PomodoroState(), S)
        val t = PomodoroEngine.skip(work, S)
        assertEquals(PomodoroPhase.ShortBreak, t.state.phase)
        assertEquals(0, t.state.completedWorkCycles)
        assertFalse(t.workCycleCompleted)
        assertNull(t.alert)
    }

    @Test
    fun skipBreakAdvancesToWorkWithoutAlerting() {
        val state = PomodoroState(phase = PomodoroPhase.ShortBreak, completedWorkCycles = 1, remainingSeconds = 1)
        val t = PomodoroEngine.skip(state, S)
        assertEquals(PomodoroPhase.Work, t.state.phase)
        assertEquals(1, t.state.completedWorkCycles)
        assertFalse(t.workCycleCompleted)
        assertNull(t.alert)
    }

    @Test
    fun skipIdleIsNoOp() {
        val t = PomodoroEngine.skip(PomodoroState(), S)
        assertEquals(PomodoroPhase.Idle, t.state.phase)
        assertNull(t.alert)
    }

    @Test
    fun pauseResumeTogglesButIdleIsUnaffected() {
        val work = PomodoroEngine.start(PomodoroState(), S)
        assertTrue(PomodoroEngine.pauseResume(work).paused)
        assertFalse(PomodoroEngine.pauseResume(PomodoroEngine.pauseResume(work)).paused)
        assertFalse(PomodoroEngine.pauseResume(PomodoroState()).paused) // Idle stays unpaused
    }

    @Test
    fun resetClearsPhaseAndCyclesButKeepsLabel() {
        val state = PomodoroState(
            phase = PomodoroPhase.Work, completedWorkCycles = 3, remainingSeconds = 99, focusLabel = "spec",
        )
        val s = PomodoroEngine.reset(state)
        assertEquals(PomodoroPhase.Idle, s.phase)
        assertEquals(0, s.completedWorkCycles)
        assertEquals(0, s.remainingSeconds)
        assertEquals("spec", s.focusLabel)
    }

    @Test
    fun idlePhaseCompletionIsNoOp() {
        val t = PomodoroEngine.complete(PomodoroState(), S)
        assertEquals(PomodoroPhase.Idle, t.state.phase)
        assertNull(t.alert)
    }
}
