package com.droidslife.screensaver.weather

import com.droidslife.screensaver.serialization.DwellJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

actual fun createWeatherCacheStore(): WeatherCacheStore = WeatherCacheStoreImpl()

/**
 * JVM implementation backed by a single JSON file alongside the settings file.
 *
 * Reads synchronously on the calling thread on first hydrate — the file is
 * small (one entry per (provider, city) the user has ever loaded) and the VM
 * is constructed on app startup, so a sub-millisecond blocking read is fine
 * and lets the first widget render hit the cache without waiting for an
 * async coroutine to come back.
 */
private class WeatherCacheStoreImpl : WeatherCacheStore {
    private val path: Path = Path.of(
        System.getProperty("user.home"),
        ".screensaver",
        "weather-cache.json",
    )
    private val json = DwellJson.Persisted

    override fun loadSync(): WeatherCacheSnapshot {
        return runCatching {
            if (!path.exists()) return WeatherCacheSnapshot()
            json.decodeFromString(WeatherCacheSnapshot.serializer(), path.readText())
        }.getOrDefault(WeatherCacheSnapshot())
    }

    override suspend fun save(snapshot: WeatherCacheSnapshot) {
        withContext(Dispatchers.IO) {
            runCatching {
                path.parent?.createDirectories()
                path.writeText(json.encodeToString(snapshot))
            }
        }
    }
}
