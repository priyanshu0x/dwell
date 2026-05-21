package com.droidslife.screensaver.widget.api

/**
 * Declarative configuration field rendered by the host Settings UI.
 *
 * Field keys must be stable because they become persisted setting keys and, for
 * [Secret], secret-store aliases.
 */
sealed interface ConfigField {
    /**
     * Stable machine key used in persisted configuration.
     */
    val key: String

    /**
     * Human-readable label displayed in Settings.
     */
    val label: String

    /**
     * Whether the host should require a value before enabling the widget.
     */
    val required: Boolean

    /**
     * Optional short helper text displayed with the field.
     */
    val help: String?

    /**
     * Single-line text input.
     */
    data class Text(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Value used when no setting has been saved. */
        val default: String = "",
        /** Optional placeholder shown while the input is empty. */
        val placeholder: String? = null,
        /** Whether the host should require a non-empty value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Secret input stored outside settings JSON.
     *
     * Widgets resolve the stored value with [WidgetConfig.secret]. The normal
     * JSON config contains only the field key/alias, not the secret value.
     */
    data class Secret(
        /** Stable machine key and secret alias. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Whether the host should require a stored secret before enabling the widget. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Integer input with optional inclusive bounds.
     */
    data class IntField(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Value used when no setting has been saved. */
        val default: Int = 0,
        /** Optional inclusive minimum. */
        val min: Int? = null,
        /** Optional inclusive maximum. */
        val max: Int? = null,
        /** Whether the host should require a value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Boolean toggle input.
     */
    data class Bool(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Value used when no setting has been saved. */
        val default: Boolean = false,
        /** Whether the host should require an explicit saved value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Single-select input constrained to a fixed option list.
     */
    data class Enum(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Available choices. */
        val options: List<EnumOption>,
        /** Selected option value used when no setting has been saved. */
        val default: String,
        /** Whether the host should require a selected value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Duration input stored as text and parsed by [WidgetConfig.durationMillis].
     */
    data class Duration(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Default duration. Supports raw milliseconds or `s`, `m`, and `h` suffixes. */
        val default: String = "30s",
        /** Whether the host should require a value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Option for an [Enum] field.
     */
    data class EnumOption(val value: String, val label: String)

    /**
     * List-of-strings input rendered as a chip editor.
     *
     * Stored as a JSON array of strings in the persisted widget configuration.
     * For backward compatibility the host may also read a single comma-separated
     * string and migrate it to a JSON array on next save.
     */
    data class StringList(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Default list of values used when no setting has been saved. */
        val default: List<String> = emptyList(),
        /** Whether the host should require at least one entry. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Duration selected from a fixed list of options. Stored as the canonical
     * string form (e.g. "5s", "1m") so it can be parsed by
     * [WidgetConfig.durationMillis].
     */
    data class DurationChoice(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Available choices (value strings parseable by durationMillis). */
        val options: List<DurationOption>,
        /** Selected value used when no setting has been saved. */
        val default: String,
        /** Whether the host should require a selected value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Option for a [DurationChoice] field.
     */
    data class DurationOption(val value: String, val label: String)

    /**
     * Currency selected from a searchable list of ISO 4217 currencies. The host
     * may pin a "popular" subset at the top of the list.
     */
    data class Currency(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Selected currency code used when no setting has been saved. */
        val default: String,
        /** Currency codes shown at the top of the list as "Popular". */
        val popular: List<String> = emptyList(),
        /** Whether the host should require a selected value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField

    /**
     * Marker for a special preview-driven design picker (used by the built-in
     * Clock widget). Each option is a digit-style design id; the host renders a
     * small preview alongside the radio.
     */
    data class DesignPicker(
        /** Stable machine key used in persisted configuration. */
        override val key: String,
        /** Human-readable label displayed in Settings. */
        override val label: String,
        /** Available design ids (rendered with previews by the host). */
        val designIds: List<Int>,
        /** Default design id used when no setting has been saved. */
        val default: Int,
        /** Whether the host should require a selected value. */
        override val required: Boolean = false,
        /** Optional short helper text displayed with the field. */
        override val help: String? = null,
    ) : ConfigField
}
