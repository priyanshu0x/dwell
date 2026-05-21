package com.droidslife.screensaver.widget.api

sealed interface ConfigField {
    val key: String
    val label: String
    val required: Boolean
    val help: String?

    data class Text(
        override val key: String,
        override val label: String,
        val default: String = "",
        val placeholder: String? = null,
        override val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField

    data class Secret(
        override val key: String,
        override val label: String,
        override val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField

    data class IntField(
        override val key: String,
        override val label: String,
        val default: Int = 0,
        val min: Int? = null,
        val max: Int? = null,
        override val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField

    data class Bool(
        override val key: String,
        override val label: String,
        val default: Boolean = false,
        override val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField

    data class Enum(
        override val key: String,
        override val label: String,
        val options: List<EnumOption>,
        val default: String,
        override val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField

    data class Duration(
        override val key: String,
        override val label: String,
        val default: String = "30s",
        override val required: Boolean = false,
        override val help: String? = null,
    ) : ConfigField

    data class EnumOption(val value: String, val label: String)
}
