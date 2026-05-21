package com.droidslife.screensaver.widget.host

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class JsonPathBinder {
    fun selectOne(root: JsonElement, path: String): JsonElement? {
        return select(root, path).firstOrNull()
    }

    fun select(root: JsonElement, path: String): List<JsonElement> {
        if (path == "$") return listOf(root)
        if (!path.startsWith("$.")) return emptyList()

        val segments = path.removePrefix("$.").split(".")
        return segments.fold(listOf(root)) { current, segment ->
            current.flatMap { element -> selectSegment(element, segment) }
        }
    }

    private fun selectSegment(element: JsonElement, segment: String): List<JsonElement> {
        val arrayWildcard = segment.endsWith("[*]")
        val key = if (arrayWildcard) segment.removeSuffix("[*]") else segment
        val selected = if (key.isBlank()) element else (element as? JsonObject)?.get(key) ?: return emptyList()

        return if (arrayWildcard) {
            (selected as? JsonArray)?.toList() ?: emptyList()
        } else {
            listOf(selected)
        }
    }
}
