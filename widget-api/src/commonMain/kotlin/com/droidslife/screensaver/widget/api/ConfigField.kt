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
}
