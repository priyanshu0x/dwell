package com.droidslife.screensaver.widget.api

/**
 * Factory discovered by the host to describe and create widget instances.
 *
 * Kotlin/JVM widgets expose implementations through Java [java.util.ServiceLoader].
 * Built-in widgets use the same interface so third-party widgets exercise the
 * same registry, configuration, lifecycle, and storage path as first-party code.
 */
interface WidgetFactory {
    /**
     * Stable unique id for this widget type.
     *
     * Reverse-DNS form such as `com.example.mywidget` is recommended. The id is
     * used for registry identity, settings, storage, and secret namespacing, so
     * changing it makes the host treat the widget as a new type.
     */
    val id: String

    /**
     * Human-readable widget name shown in Settings and used as the default card header.
     */
    val displayName: String

    /**
     * Optional one-line description shown in widget management UI.
     */
    val description: String get() = ""

    /**
     * Broad grouping used by Settings for filtering and organization.
     */
    val category: WidgetCategory get() = WidgetCategory.OTHER

    /**
     * Widget API version this factory was compiled against.
     *
     * The host may reject factories targeting a newer unsupported version.
     */
    val apiVersion: Int get() = WIDGET_API_VERSION

    /**
     * Configuration fields rendered by the host Settings panel.
     *
     * Keys in this schema are provided to [create] through [WidgetConfig]. Secret
     * fields are stored outside normal settings JSON and resolved by calling
     * [WidgetConfig.secret].
     */
    val configSchema: List<ConfigField> get() = emptyList()

    /**
     * Creates a fresh widget instance for the supplied configuration and host scope.
     *
     * The host may call this again when configuration changes. Implementations
     * should keep long-running work tied to [WidgetScope.coroutineScope] so it is
     * cancelled when the widget is disabled or recreated.
     */
    fun create(config: WidgetConfig, scope: WidgetScope): Widget
}

/**
 * Coarse category for grouping widgets in Settings.
 */
enum class WidgetCategory {
    /** Time/date presentation widgets. */
    CLOCK,

    /** Weather, status, feeds, and read-only informational widgets. */
    INFORMATION,

    /** Todos, notes, habits, and other action-oriented widgets. */
    PRODUCTIVITY,

    /** Media playback or now-playing widgets. */
    MEDIA,

    /** Budget, account, market, or expense widgets. */
    FINANCE,

    /** Device, runtime, or operating-system status widgets. */
    SYSTEM,

    /** Fallback for widgets that do not fit another category. */
    OTHER,
}
