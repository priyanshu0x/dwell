package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

actual class ScopedStorage actual constructor(
    private val widgetId: String,
) : WidgetStorage {
    private val directory: Path = Path.of(
        System.getProperty("user.home"),
        ".screensaver",
        "widget-data",
        safePathSegment(widgetId),
    )

    override suspend fun <T : Any> read(key: String, type: Class<T>): T? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!Files.exists(file)) return@withContext null

        runCatching {
            ObjectInputStream(Files.newInputStream(file)).use { input ->
                val value = input.readObject()
                if (type.isInstance(value)) type.cast(value) else null
            }
        }.getOrNull()
    }

    override suspend fun <T : Any> write(key: String, value: T) = withContext(Dispatchers.IO) {
        require(value is Serializable) {
            "Widget storage value for '$key' must implement java.io.Serializable"
        }

        Files.createDirectories(directory)
        val target = fileFor(key)
        val temp = Files.createTempFile(directory, target.fileName.toString(), ".tmp")
        try {
            ObjectOutputStream(Files.newOutputStream(temp)).use { output ->
                output.writeObject(value)
            }
            moveIntoPlace(temp, target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        Files.deleteIfExists(fileFor(key))
        Unit
    }

    private fun fileFor(key: String): Path = directory.resolve("${sha256(key)}.bin")

    private fun moveIntoPlace(temp: Path, target: Path) {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun toString(): String = "ScopedStorage(widgetId=$widgetId)"
}

private fun safePathSegment(value: String): String {
    return value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "widget" }
}

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}
