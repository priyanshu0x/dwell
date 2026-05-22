package com.droidslife.screensaver.ui

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * App-wide pointer icon palette. We use cursor shape sparingly — only on
 * affordances that change what the next click will do (drag-to-move,
 * drag-to-resize). Hover-only hand cursors come from
 * [androidx.compose.ui.input.pointer.PointerIcon.Hand] directly.
 *
 * Move/resize aren't in commonMain's PointerIcon set, so each platform
 * supplies its own native cursor.
 */
expect val MovePointerIcon: PointerIcon
expect val ResizeSEPointerIcon: PointerIcon
