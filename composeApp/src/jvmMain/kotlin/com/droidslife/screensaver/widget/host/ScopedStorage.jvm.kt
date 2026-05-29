package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.ObjectInputFilter
import java.io.ObjectInputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

actual class ScopedStorage actual constructor(
    private val widgetId: String,
) : WidgetStorage {
    private val json = Json
    private val directory: Path = Path.of(
        System.getProperty("user.home"),
        ".screensaver",
        "widget-data",
        safePathSegment(widgetId),
    )

    override suspend fun <T : Any> read(key: String, type: Class<T>): T? = withContext(Dispatchers.IO) {
        if (type != String::class.java) return@withContext null

        val file = jsonFileFor(key)
        if (Files.exists(file)) {
            return@withContext readJsonString(file, type)
        }

        readLegacyString(key, type)
    }

    override suspend fun <T : Any> write(key: String, value: T) = withContext(Dispatchers.IO) {
        require(value is String) {
            "Widget storage value for '$key' must be a String; encode structured values as JSON first"
        }

        writeJsonString(key, value)
        Files.deleteIfExists(legacyFileFor(key))
        Unit
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        Files.deleteIfExists(jsonFileFor(key))
        Files.deleteIfExists(legacyFileFor(key))
        Unit
    }

    private fun <T : Any> readJsonString(file: Path, type: Class<T>): T? =
        runCatching {
            val value = json.decodeFromString(String.serializer(), Files.readString(file))
            type.cast(value)
        }.getOrNull()

    private fun <T : Any> readLegacyString(key: String, type: Class<T>): T? {
        val legacyFile = legacyFileFor(key)
        if (!Files.exists(legacyFile)) return null

        val value = runCatching {
            ObjectInputStream(Files.newInputStream(legacyFile)).use { input ->
                input.setObjectInputFilter(legacyStringFilter)
                input.readObject() as? String
            }
        }.getOrNull() ?: return null

        writeJsonString(key, value)
        Files.deleteIfExists(legacyFile)
        return type.cast(value)
    }

    private fun writeJsonString(key: String, value: String) {
        Files.createDirectories(directory)
        val target = jsonFileFor(key)
        val temp = Files.createTempFile(directory, target.fileName.toString(), ".tmp")
        try {
            Files.writeString(temp, json.encodeToString(String.serializer(), value))
            moveIntoPlace(temp, target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun jsonFileFor(key: String): Path = directory.resolve("${sha256(key)}.json")

    private fun legacyFileFor(key: String): Path = directory.resolve("${sha256(key)}.bin")

    private fun moveIntoPlace(temp: Path, target: Path) {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun toString(): String = "ScopedStorage(widgetId=$widgetId)"
}

private val legacyStringFilter = ObjectInputFilter { info ->
    val serialClass = info.serialClass()
    when {
        serialClass == null -> ObjectInputFilter.Status.UNDECIDED
        serialClass == String::class.java -> ObjectInputFilter.Status.ALLOWED
        serialClass.isPrimitive -> ObjectInputFilter.Status.ALLOWED
        else -> ObjectInputFilter.Status.REJECTED
    }
}

private fun safePathSegment(value: String): String {
    return value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "widget" }
}

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}
