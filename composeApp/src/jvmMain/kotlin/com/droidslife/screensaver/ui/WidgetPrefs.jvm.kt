package com.droidslife.screensaver.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

actual object WidgetPrefs {
    private val root: Path = Path.of(
        System.getProperty("user.home"),
        ".screensaver",
        "widget-prefs",
    )

    actual fun read(scopeId: String, key: String): String? = runCatching {
        val file = fileFor(scopeId, key)
        if (file.exists()) file.readText() else null
    }.getOrNull()

    actual fun write(scopeId: String, key: String, value: String) {
        runCatching {
            val dir = root.resolve(safe(scopeId))
            Files.createDirectories(dir)
            val target = dir.resolve(safe(key))
            // Write to a temp file then atomically move, so a crash mid-write
            // can't leave a truncated preference behind.
            val temp = Files.createTempFile(dir, safe(key), ".tmp")
            Files.writeString(temp, value)
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun fileFor(scopeId: String, key: String): Path = root.resolve(safe(scopeId)).resolve(safe(key))

    private fun safe(segment: String): String = segment.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
}
