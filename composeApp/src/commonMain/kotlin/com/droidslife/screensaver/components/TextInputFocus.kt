package com.droidslife.screensaver.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * Tracks whether any editable text field currently holds focus, so the
 * window-level shortcut handler ([KeyEventHandler.handleWindowKeyEvent]) can
 * stand down while the user is typing. Without this, plain keys (S, M, V, 1-3…)
 * and edit chords (Ctrl+C / Ctrl+X / Ctrl+A) get hijacked as app shortcuts
 * instead of reaching the field.
 *
 * Reference-counted because focus can jump straight from one field to another:
 * the new field reports gaining focus before the old one reports losing it, so
 * a plain boolean would briefly read "not typing" mid-handoff.
 */
object TextInputFocus {
    private var focusCount by mutableIntStateOf(0)

    /** True while at least one editable field is focused. */
    val isActive: Boolean get() = focusCount > 0

    fun acquire() {
        focusCount++
    }

    fun release() {
        if (focusCount > 0) focusCount--
    }
}

/**
 * Reference-counted pause for widget-local modal surfaces that are not global
 * app dialogs. They need first chance at Esc without the window handler hiding
 * the whole dashboard.
 */
object ShortcutPause {
    private data class PauseEntry(
        val id: Long,
        val onEscape: (() -> Unit)?,
    )

    class Token internal constructor(internal val id: Long)

    private val pauses = mutableListOf<PauseEntry>()
    private var nextId = 0L
    private var activeCount by mutableIntStateOf(0)

    val isActive: Boolean get() = activeCount > 0

    fun acquire(): Token = acquireInternal(onEscape = null)

    fun acquire(onEscape: () -> Unit): Token = acquireInternal(onEscape)

    fun release(token: Token) {
        val index = pauses.indexOfLast { it.id == token.id }
        if (index >= 0) {
            pauses.removeAt(index)
            activeCount = pauses.size
        }
    }

    fun release() {
        if (pauses.isNotEmpty()) {
            pauses.removeAt(pauses.lastIndex)
            activeCount = pauses.size
        }
    }

    fun handleEscape(): Boolean {
        val handler = pauses.asReversed().firstNotNullOfOrNull { it.onEscape } ?: return false
        handler()
        return true
    }

    private fun acquireInternal(onEscape: (() -> Unit)?): Token {
        val token = Token(++nextId)
        pauses += PauseEntry(token.id, onEscape)
        activeCount = pauses.size
        return token
    }
}

/**
 * Marks a composable's focus target as text entry, pausing app keyboard
 * shortcuts while it's focused. Apply to every editable field's `modifier`.
 *
 * The [DisposableEffect] releases the count if a *focused* field leaves
 * composition (e.g. an inline add-form collapsing or a settings sheet closing),
 * which doesn't reliably emit a focus-lost event on its own.
 */
fun Modifier.pausesShortcutsWhileFocused(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (focused) {
                TextInputFocus.release()
                focused = false
            }
        }
    }
    onFocusChanged { state ->
        if (state.isFocused && !focused) {
            focused = true
            TextInputFocus.acquire()
        } else if (!state.isFocused && focused) {
            focused = false
            TextInputFocus.release()
        }
    }
}
