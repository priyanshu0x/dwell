package com.droidslife.screensaver.settings

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase.FILETIME
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

actual fun createSecretStorage(): SecretStorage = JvmSecretStorage()

private class JvmSecretStorage(
    private val osStore: OsSecretStore = createOsSecretStore(),
    private val fallbackStore: ObfuscatedFileSecretStore = ObfuscatedFileSecretStore(),
) : SecretStorage {
    override suspend fun read(id: String): String? = withContext(Dispatchers.IO) {
        osStore.read(id) ?: fallbackStore.read(id)
    }

    override suspend fun write(id: String, value: String) = withContext(Dispatchers.IO) {
        if (osStore.write(id, value)) {
            fallbackStore.delete(id)
        } else {
            fallbackStore.write(id, value)
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        osStore.delete(id)
        fallbackStore.delete(id)
    }
}

private interface OsSecretStore {
    fun read(id: String): String?
    fun write(id: String, value: String): Boolean
    fun delete(id: String): Boolean
}

private object NoopSecretStore : OsSecretStore {
    override fun read(id: String): String? = null
    override fun write(id: String, value: String): Boolean = false
    override fun delete(id: String): Boolean = false
}

private fun createOsSecretStore(): OsSecretStore {
    val osName = System.getProperty("os.name")
    return when {
        osName.startsWith("Windows", ignoreCase = true) -> WindowsCredentialStore()
        osName.contains("Linux", ignoreCase = true) -> SecretToolStore()
        else -> NoopSecretStore
    }
}

internal class ObfuscatedFileSecretStore(
    private val path: Path = Path.of(System.getProperty("user.home"), ".screensaver", "secrets.dat"),
) {
    private val json = Json { prettyPrint = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun read(id: String): String? = load()[id]?.let(::decode)

    fun write(id: String, value: String) {
        val next = load().toMutableMap()
        next[id] = encode(value)
        path.parent.createDirectories()
        path.writeText(json.encodeToString(serializer, next))
    }

    fun delete(id: String) {
        val next = load().toMutableMap()
        next.remove(id)
        path.parent.createDirectories()
        path.writeText(json.encodeToString(serializer, next))
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
}

private class WindowsCredentialStore : OsSecretStore {
    override fun read(id: String): String? {
        val credentialRef = PointerByReference()
        val ok = runCatching {
            WinCred.INSTANCE.CredReadW(WString(target(id)), CRED_TYPE_GENERIC, 0, credentialRef)
        }.getOrDefault(false)
        if (!ok) return null

        return try {
            val credential = Credential(credentialRef.value)
            val blob = credential.CredentialBlob ?: return null
            blob.getByteArray(0, credential.CredentialBlobSize).decodeToString()
        } finally {
            WinCred.INSTANCE.CredFree(credentialRef.value)
        }
    }

    override fun write(id: String, value: String): Boolean {
        val bytes = value.encodeToByteArray()
        if (bytes.size > CRED_MAX_CREDENTIAL_BLOB_SIZE) return false

        val memory = Memory(bytes.size.toLong())
        memory.write(0, bytes, 0, bytes.size)
        val credential = Credential().apply {
            Type = CRED_TYPE_GENERIC
            TargetName = WString(target(id))
            CredentialBlobSize = bytes.size
            CredentialBlob = memory
            Persist = CRED_PERSIST_LOCAL_MACHINE
            UserName = WString("ScreenSaverApp")
        }
        credential.write()

        return runCatching { WinCred.INSTANCE.CredWriteW(credential, 0) }.getOrDefault(false)
    }

    override fun delete(id: String): Boolean {
        return runCatching { WinCred.INSTANCE.CredDeleteW(WString(target(id)), CRED_TYPE_GENERIC, 0) }
            .getOrDefault(false)
    }

    private fun target(id: String): String = "ScreenSaverApp:$id"

    private companion object {
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
        const val CRED_MAX_CREDENTIAL_BLOB_SIZE = 5120
    }
}

private interface WinCred : StdCallLibrary {
    fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
    fun CredWriteW(credential: Credential, flags: Int): Boolean
    fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
    fun CredFree(buffer: Pointer)

    companion object {
        val INSTANCE: WinCred = Native.load("Advapi32", WinCred::class.java)
    }
}

@Suppress("PropertyName")
private class Credential() : Structure() {
    @JvmField var Flags: Int = 0
    @JvmField var Type: Int = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWritten: FILETIME = FILETIME()
    @JvmField var CredentialBlobSize: Int = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist: Int = 0
    @JvmField var AttributeCount: Int = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null

    constructor(pointer: Pointer) : this() {
        useMemory(pointer)
        read()
    }

    override fun getFieldOrder(): List<String> = listOf(
        "Flags",
        "Type",
        "TargetName",
        "Comment",
        "LastWritten",
        "CredentialBlobSize",
        "CredentialBlob",
        "Persist",
        "AttributeCount",
        "Attributes",
        "TargetAlias",
        "UserName",
    )
}

private class SecretToolStore : OsSecretStore {
    override fun read(id: String): String? {
        if (!available()) return null
        val result = runCommand("secret-tool", "lookup", "application", APP, "id", id)
        return result.takeIf { it.exitCode == 0 }?.stdout?.trimEnd('\r', '\n')?.takeIf { it.isNotEmpty() }
    }

    override fun write(id: String, value: String): Boolean {
        if (!available()) return false
        val result = runCommand(
            "secret-tool",
            "store",
            "--label",
            "Screen Saver App",
            "application",
            APP,
            "id",
            id,
            stdin = value,
        )
        return result.exitCode == 0
    }

    override fun delete(id: String): Boolean {
        if (!available()) return false
        val result = runCommand("secret-tool", "clear", "application", APP, "id", id)
        return result.exitCode == 0
    }

    private fun available(): Boolean = runCommand("secret-tool", "--version").exitCode == 0

    private companion object {
        const val APP = "ScreenSaverApp"
    }
}

private data class CommandResult(val exitCode: Int, val stdout: String)

private fun runCommand(vararg command: String, stdin: String? = null): CommandResult {
    return runCatching {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        if (stdin != null) {
            process.outputStream.bufferedWriter().use { writer -> writer.write(stdin) }
        } else {
            process.outputStream.close()
        }
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return CommandResult(-1, "")
        }
        CommandResult(process.exitValue(), process.inputStream.readBytes().decodeToString())
    }.getOrDefault(CommandResult(-1, ""))
}
