package com.droidslife.screensaver.widget.host

import kotlinx.coroutines.test.runTest
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopedStorageJvmTest {
    @Test
    fun stringsRoundTripThroughJsonStorage() = withTempHome { home ->
        runTest {
            val storage = ScopedStorage("test.widget")

            storage.write("cache", """{"items":[1,2,3]}""")

            assertEquals("""{"items":[1,2,3]}""", storage.read("cache", String::class.java))
            assertTrue(Files.exists(storageFile(home, "test.widget", "cache", "json")))
            assertFalse(Files.exists(storageFile(home, "test.widget", "cache", "bin")))
        }
    }

    @Test
    fun legacyStringStorageMigratesToJson() = withTempHome { home ->
        val legacyFile = storageFile(home, "test.widget", "cache", "bin")
        Files.createDirectories(legacyFile.parent)
        ObjectOutputStream(Files.newOutputStream(legacyFile)).use { output ->
            output.writeObject("legacy-value")
        }

        runTest {
            val storage = ScopedStorage("test.widget")

            assertEquals("legacy-value", storage.read("cache", String::class.java))
            assertFalse(Files.exists(legacyFile))
            assertTrue(Files.exists(storageFile(home, "test.widget", "cache", "json")))
            assertEquals("legacy-value", storage.read("cache", String::class.java))
        }
    }

    @Test
    fun unsupportedValueTypesAreRejected() = withTempHome {
        runTest {
            val storage = ScopedStorage("test.widget")

            val error = runCatching { storage.write("count", 42) }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
        }
    }
}

private fun withTempHome(block: (Path) -> Unit) {
    val previousHome = System.getProperty("user.home")
    val home = createTempDirectory("dwell-storage-test")
    try {
        System.setProperty("user.home", home.toString())
        block(home)
    } finally {
        System.setProperty("user.home", previousHome)
        home.toFile().deleteRecursively()
    }
}

private fun storageFile(home: Path, widgetId: String, key: String, extension: String): Path =
    home.resolve(".screensaver")
        .resolve("widget-data")
        .resolve(widgetId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "widget" })
        .resolve("${sha256(key)}.$extension")

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}
