package com.droidslife.screensaver.widget.host

import com.charleskorn.kaml.Yaml

class ManifestParser(
    private val yaml: Yaml = Yaml.default,
) {
    fun parse(content: String): WidgetManifest {
        return yaml.decodeFromString(WidgetManifest.serializer(), content)
    }
}
