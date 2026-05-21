package com.droidslife.screensaver.widget.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

class WidgetConfig(
    private val values: JsonObject,
    private val secretResolver: (String) -> String? = { null },
) {
    val rawJson: JsonObject get() = values

    fun string(key: String, default: String = ""): String {
        return primitive(key)?.content ?: default
    }

    fun int(key: String, default: Int = 0): Int {
        return primitive(key)?.intOrNull ?: default
    }

    fun bool(key: String, default: Boolean = false): Boolean {
        return primitive(key)?.booleanOrNull ?: default
    }

    fun durationMillis(key: String, default: Long = 0): Long {
        val value = primitive(key)?.content ?: return default
        return parseDurationMillis(value) ?: default
    }

    fun enum(key: String, default: String): String {
        return string(key, default)
    }

    fun secret(key: String): String? {
        return secretResolver(key)
    }

    fun raw(key: String): JsonElement? = values[key]

    private fun primitive(key: String): JsonPrimitive? = values[key] as? JsonPrimitive

    private fun parseDurationMillis(value: String): Long? {
        val trimmed = value.trim()
        val numeric = trimmed.toLongOrNull()
        if (numeric != null) return numeric

        val amount = trimmed.dropLast(1).toLongOrNull() ?: return null
        return when (trimmed.lastOrNull()) {
            's', 'S' -> amount * 1_000L
            'm', 'M' -> amount * 60_000L
            'h', 'H' -> amount * 3_600_000L
            else -> null
        }
    }
}
