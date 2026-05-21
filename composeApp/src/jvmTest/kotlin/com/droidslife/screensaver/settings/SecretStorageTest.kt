package com.droidslife.screensaver.settings

import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SecretStorageTest {
    @Test
    fun obfuscatedFallbackRoundTripsAndDoesNotStorePlaintext() {
        val path = createTempDirectory(prefix = "screen-saver-secrets").resolve("secrets.dat")
        val store = ObfuscatedFileSecretStore(path)

        store.write("api.key", "super-secret-value")

        assertEquals("super-secret-value", store.read("api.key"))
        assertFalse(path.readText().contains("super-secret-value"))
    }

    @Test
    fun obfuscatedFallbackDeleteRemovesValue() {
        val path = createTempDirectory(prefix = "screen-saver-secrets").resolve("secrets.dat")
        val store = ObfuscatedFileSecretStore(path)

        store.write("api.key", "super-secret-value")
        store.delete("api.key")

        assertNull(store.read("api.key"))
        assertEquals(true, path.exists())
    }
}
