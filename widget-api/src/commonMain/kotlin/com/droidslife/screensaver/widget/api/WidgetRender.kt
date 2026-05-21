package com.droidslife.screensaver.widget.api

/** Which "slot" the host is asking the widget to fill. */
enum class WidgetRenderTarget {
    /** Full card with author-declared size; visible in Console mode. */
    Tile,
    /** Compact one-row chip (~60dp); shown in Cinematic drawer. */
    Chip,
    /** Single dim line of text, no chrome; shown in Ambient when widget is enabled. */
    Minimal,
}

/** Emotional tint for default renderers. */
enum class WidgetAccent { Default, Positive, Negative, Neutral }

/**
 * A widget's at-a-glance summary, used by host-default Chip/Minimal/Tile renderers
 * and by the Cinematic meta-line / Ambient minimal renderer.
 */
data class WidgetSummary(
    val primaryValue: String,
    val primaryLabel: String? = null,
    val subtitle: String? = null,
    val accent: WidgetAccent = WidgetAccent.Default,
)
