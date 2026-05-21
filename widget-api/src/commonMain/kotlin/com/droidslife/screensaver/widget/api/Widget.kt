package com.droidslife.screensaver.widget.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Runtime widget instance rendered by the dashboard.
 *
 * Instances are created by [WidgetFactory.create]. A widget should keep any
 * background work in the [WidgetScope.coroutineScope] it receives at creation
 * time and release non-coroutine resources from [onDispose].
 */
interface Widget {
    /**
     * Renders the widget body inside a host-provided card.
     *
     * The host owns dashboard chrome, card padding, and the optional header. The
     * supplied [modifier] must be applied to the root composable so the host can
     * size and place the widget correctly.
     */
    @Composable
    fun Content(modifier: Modifier)

    /**
     * Per-target render. Default delegates to Content for Tile; Chip/Minimal use host defaults.
     * Override per-target to customize.
     */
    @Composable
    fun Render(target: WidgetRenderTarget, scope: WidgetScope, modifier: Modifier = Modifier) {
        when (target) {
            WidgetRenderTarget.Tile    -> Content(modifier)
            WidgetRenderTarget.Chip    -> Content(modifier) // host will wrap with chip chrome
            WidgetRenderTarget.Minimal -> Content(modifier) // host will wrap with minimal chrome
        }
    }

    /** At-a-glance summary. Required for non-Console renderers. */
    fun summary(): WidgetSummary = WidgetSummary(primaryValue = "—")

    /**
     * Optional card header override.
     *
     * Return `null` to let the host use [WidgetFactory.displayName].
     */
    val header: String? get() = null

    /**
     * Requested dashboard grid width.
     *
     * Current host layout treats `1` as standard, `2` as wide, and `3` as full
     * width. Values outside that range may be clamped by the host.
     */
    val preferredSpan: Int get() = 1

    /**
     * Called when the dashboard becomes visible.
     *
     * Use this to refresh transient data or resume polling that was paused by
     * [onSuspend].
     */
    fun onResume() {}

    /**
     * Called when the dashboard is hidden or starts fading out.
     *
     * The widget may be resumed later, so do not permanently release resources
     * here unless they can be recreated from [onResume].
     */
    fun onSuspend() {}

    /**
     * Final teardown when the widget is disabled, recreated, or the app exits.
     *
     * The host cancels the widget coroutine scope after this callback.
     */
    fun onDispose() {}
}
