package com.droidslife.screensaver.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

actual fun createSecretStorage(): SecretStorage = JvmSecretStorage()

private class JvmSecretStorage : SecretStorage {
    private val json = Json { prettyPrint = true }
    private val path = Path.of(System.getProperty("user.home"), ".screensaver", "secrets.dat")
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun read(id: String): String? = withContext(Dispatchers.IO) {
        load()[id]?.let(::decode)
    }

    override suspend fun write(id: String, value: String) = withContext(Dispatchers.IO) {
        val next = load().toMutableMap()
        next[id] = encode(value)
        path.parent?.createDirectories()
        path.writeText(json.encodeToString(serializer, next))
        mirrorToWindowsCredentialManager(id, value)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val next = load().toMutableMap()
        next.remove(id)
        path.parent?.createDirectories()
        path.writeText(json.encodeToString(serializer, next))
        if (isWindows()) {
            runCatching {
                ProcessBuilder("cmdkey", "/delete:${target(id)}")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
        }
    }

    private fun load(): Map<String, String> {
        return runCatching {
            if (!path.exists()) emptyMap() else json.decodeFromString(serializer, path.readText())
        }.getOrDefault(emptyMap())
    }

    private fun encode(value: String): String {
        val bytes = value.encodeToByteArray()
        val key = salt()
        return bytes.mapIndexed { index, byte -> (byte.toInt() xor key[index % key.size].toInt()).toByte() }
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun decode(value: String): String {
        val key = salt()
        val bytes = value.chunked(2)
            .mapIndexed { index, hex -> (hex.toInt(16) xor key[index % key.size].toInt()).toByte() }
            .toByteArray()
        return bytes.decodeToString()
    }

    private fun salt(): ByteArray {
        val installIdPath = path.parent.resolve("install.id")
        if (!installIdPath.exists()) {
            path.parent.createDirectories()
            installIdPath.writeText(java.util.UUID.randomUUID().toString())
        }
        return MessageDigest.getInstance("SHA-256").digest(installIdPath.readText().encodeToByteArray())
    }

    private fun mirrorToWindowsCredentialManager(id: String, value: String) {
        if (!isWindows()) return
        runCatching {
            ProcessBuilder("cmdkey", "/generic:${target(id)}", "/user:ScreenSaverApp", "/pass:$value")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun target(id: String): String = "ScreenSaverApp:$id"
    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}
