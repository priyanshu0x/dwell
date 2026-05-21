package com.droidslife.screensaver.widget.api

interface WidgetFactory {
    /** Stable unique ID, lowercase reverse-DNS recommended. */
    val id: String

    /** Human label shown in settings and as the default widget header. */
    val displayName: String

    /** Optional one-line description. */
    val description: String get() = ""

    /** Used for settings grouping. */
    val category: WidgetCategory get() = WidgetCategory.OTHER

    /** Widget API version this widget targets. */
    val apiVersion: Int get() = WIDGET_API_VERSION

    /** Per-widget config schema rendered by the host settings panel. */
    val configSchema: List<ConfigField> get() = emptyList()

    /** Creates a fresh widget instance. */
    fun create(config: WidgetConfig, scope: WidgetScope): Widget
}

enum class WidgetCategory {
    CLOCK,
    INFORMATION,
    PRODUCTIVITY,
    MEDIA,
    FINANCE,
    SYSTEM,
    OTHER,
}
