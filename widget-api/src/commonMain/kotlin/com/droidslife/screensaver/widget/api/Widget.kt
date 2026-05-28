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
     * Optional legacy single-slot renderer.
     *
     * Kept as an optional default for widgets that pre-date the per-target
     * [Render] contract. New widgets should override [Render] instead; the
     * default [Render] implementation forwards to [Content] for the [Tile]
     * target and produces a host-default text summary for [Chip] / [Minimal].
     */
    @Composable
    fun Content(modifier: Modifier) {
        // Empty default. Widgets that only override Render(target) never see this.
    }

    /**
     * Per-target render. Default forwards to [Content] for every target so
     * widgets that pre-date the per-target contract keep working. Hosts that
     * need per-target chrome should override this method and dispatch on
     * [target]; the [DefaultTileRender] / [DefaultChipRender] /
     * [DefaultMinimalRender] helpers in the host module render summaries when
     * a widget has nothing specific to draw.
     */
    @Composable
    fun Render(target: WidgetRenderTarget, scope: WidgetScope, modifier: Modifier = Modifier) {
        Content(modifier)
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
     * Opt in to drawing the widget's own compact [WidgetRenderTarget.Chip] in
     * the Cinematic drawer. When `false` (default) the host renders a text
     * summary via its default chip renderer.
     */
    val rendersOwnChip: Boolean get() = false

    /**
     * Opt in to drawing the widget's own [WidgetRenderTarget.Minimal] line in
     * Ambient mode. When `false` (default) the widget is not shown in Ambient.
     */
    val rendersOwnMinimal: Boolean get() = false

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
