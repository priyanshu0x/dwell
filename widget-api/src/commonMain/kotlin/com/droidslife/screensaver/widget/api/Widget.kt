package com.droidslife.screensaver.widget.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Widget {
    /** Render inside a host-provided card. The host handles header and chrome. */
    @Composable
    fun Content(modifier: Modifier)

    /** Optional custom header. Default is the factory display name. */
    val header: String? get() = null

    /** Cell width in the grid: 1 standard, 2 wide, 3 full. */
    val preferredSpan: Int get() = 1

    /** Called when the dashboard becomes visible. */
    fun onResume() {}

    /** Called when the dashboard fades out. */
    fun onSuspend() {}

    /** Final teardown when the widget is disabled or the app exits. */
    fun onDispose() {}
}
