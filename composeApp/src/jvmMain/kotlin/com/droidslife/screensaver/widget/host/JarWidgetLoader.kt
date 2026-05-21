package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WIDGET_API_VERSION
import com.droidslife.screensaver.widget.api.WidgetFactory
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

actual fun createWidgetLoader(): WidgetLoader = WidgetLoaderImpl(
    widgetsDir = Path.of(System.getProperty("user.home"), ".screensaver", "widgets"),
)

private class WidgetLoaderImpl(
    private val widgetsDir: Path,
) : WidgetLoader {
    override fun discoverAll(): List<WidgetDescriptor> {
        return JarWidgetLoader(widgetsDir).load() + DeclarativeWidgetLoader(widgetsDir).load()
    }
}

class JarWidgetLoader(
    private val widgetsDir: Path,
) {
    fun load(): List<WidgetDescriptor> {
        if (!Files.exists(widgetsDir)) return emptyList()

        return Files.list(widgetsDir).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension.equals("jar", ignoreCase = true) }
                .flatMap { jarPath -> loadJar(jarPath).stream() }
                .toList()
        }
    }

    private fun loadJar(jarPath: Path): List<WidgetDescriptor> {
        return runCatching {
            val loader = URLClassLoader(
                arrayOf(jarPath.toUri().toURL()),
                WidgetFactory::class.java.classLoader,
            )
            ServiceLoader.load(WidgetFactory::class.java, loader)
                .mapNotNull { factory ->
                    if (factory.apiVersion > WIDGET_API_VERSION) {
                        println("Skipping widget ${factory.id}: API ${factory.apiVersion} > host API $WIDGET_API_VERSION")
                        null
                    } else {
                        WidgetDescriptor(
                            id = factory.id,
                            displayName = factory.displayName,
                            category = factory.category,
                            factory = factory,
                            source = WidgetSource.Jar(jarPath.toString()),
                        )
                    }
                }
                .toList()
        }.getOrElse { error ->
            println("Failed to load widgets from $jarPath: ${error.message}")
            emptyList()
        }
    }
}
