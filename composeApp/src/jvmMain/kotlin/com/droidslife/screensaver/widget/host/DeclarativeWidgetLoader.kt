package com.droidslife.screensaver.widget.host

import co.touchlab.kermit.Logger
import com.droidslife.screensaver.widget.api.WidgetCategory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

class DeclarativeWidgetLoader(
    private val widgetsDir: Path,
    private val parser: ManifestParser = ManifestParser(),
) {
    private val logger = Logger.withTag("DeclarativeWidgetLoader")

    fun load(): List<WidgetDescriptor> {
        if (!widgetsDir.exists()) return emptyList()

        return Files.list(widgetsDir).use { paths ->
            paths.iterator().asSequence()
                .filter { it.isDirectory() && it.resolve("widget.yaml").exists() }
                .mapNotNull(::loadFolder)
                .toList()
        }
    }

    private fun loadFolder(folder: Path): WidgetDescriptor? {
        return runCatching {
            val manifest = parser.parse(folder.resolve("widget.yaml").readText())
            val factory = DeclarativeWidgetFactory(manifest, folder)
            WidgetDescriptor(
                id = factory.id,
                displayName = factory.displayName,
                category = manifest.category.toWidgetCategory(),
                factory = factory,
                source = WidgetSource.Declarative(folder.toString()),
            )
        }.getOrElse { error ->
            logger.e(error) { "Failed to load declarative widget from $folder" }
            null
        }
    }
}

private fun String.toWidgetCategory(): WidgetCategory {
    return WidgetCategory.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        ?: WidgetCategory.OTHER
}
