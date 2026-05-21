package com.droidslife.screensaver.widget.host

import kotlinx.serialization.Serializable

@Serializable
data class WidgetManifest(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "other",
    val apiVersion: Int,
    val template: String,
    val preferredSpan: Int = 1,
    val refresh: String = "60s",
    val source: SourceManifest,
    val bindings: Map<String, String> = emptyMap(),
    val config: List<ConfigFieldManifest> = emptyList(),
)

@Serializable
data class SourceManifest(
    val type: String,
    val command: List<String> = emptyList(),
    val url: String = "",
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val path: String = "",
    val timeout: String = "10s",
    val cacheBust: Boolean = true,
)

@Serializable
data class ConfigFieldManifest(
    val key: String,
    val label: String,
    val type: String,
    val default: String = "",
    val required: Boolean = false,
    val help: String? = null,
    val options: List<ConfigOptionManifest> = emptyList(),
)

@Serializable
data class ConfigOptionManifest(
    val value: String,
    val label: String,
)
