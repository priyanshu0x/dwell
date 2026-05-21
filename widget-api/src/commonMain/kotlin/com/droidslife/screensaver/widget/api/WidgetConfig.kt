package com.droidslife.screensaver.widget.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Read-only configuration values supplied to a widget instance.
 *
 * Values come from the host Settings UI or a declarative widget manifest. Secret
 * fields are intentionally resolved through [secret] so their values do not need
 * to live in the normal JSON configuration document.
 */
class WidgetConfig(
    private val values: JsonObject,
    private val secretResolver: (String) -> String? = { null },
) {
    /**
     * Raw JSON object for advanced widgets that need custom nested settings.
     */
    val rawJson: JsonObject get() = values

    /**
     * Reads a string value or [default] when missing or not a primitive.
     */
    fun string(key: String, default: String = ""): String {
        return primitive(key)?.content ?: default
    }

    /**
     * Reads an integer value or [default] when missing or not an integer.
     */
    fun int(key: String, default: Int = 0): Int {
        return primitive(key)?.intOrNull ?: default
    }

    /**
     * Reads a boolean value or [default] when missing or not a boolean.
     */
    fun bool(key: String, default: Boolean = false): Boolean {
        return primitive(key)?.booleanOrNull ?: default
    }

    /**
     * Reads a duration as milliseconds.
     *
     * Supported values are raw millisecond numbers or whole-number strings with
     * `s`, `m`, or `h` suffixes, for example `30s`, `5m`, or `1h`.
     */
    fun durationMillis(key: String, default: Long = 0): Long {
        val value = primitive(key)?.content ?: return default
        return parseDurationMillis(value) ?: default
    }

    /**
     * Reads a selected enum option value.
     */
    fun enum(key: String, default: String): String {
        return string(key, default)
    }

    /**
     * Reads a list of strings.
     *
     * Accepts either a JSON array of strings, or a legacy comma-separated string
     * primitive (for backward compatibility with values saved before the chip
     * editor was introduced). Returns [default] when missing.
     */
    fun stringList(key: String, default: List<String> = emptyList()): List<String> {
        val element = values[key] ?: return default
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
                .map { it.trim() }
                .filter { it.isNotBlank() }
            is JsonPrimitive -> element.content
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> default
        }
    }

    /**
     * Resolves a secret value by field key.
     *
     * Returns `null` when the secret is missing or the host cannot access the
     * configured secret store.
     */
    fun secret(key: String): String? {
        return secretResolver(key)
    }

    /**
     * Reads the raw JSON element for [key].
     */
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
