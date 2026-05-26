package com.droidslife.screensaver.weather

import kotlinx.serialization.Serializable

/**
 * Persistent cache shape for [WeatherViewModel]. Kept process-stable so the
 * widget can paint the last-known temperature within the first frame after a
 * fresh launch (instead of always sitting on a network round-trip).
 *
 * Mirror types rather than the live domain shapes — `CurrentWeather` /
 * `DayForecast` / `LocalDate` aren't all `@Serializable`, and freezing the
 * cache shape independently means future tweaks to the domain types don't
 * silently invalidate every user's saved cache.
 */
@Serializable
data class WeatherCacheSnapshot(
    val entries: List<WeatherCacheEntry> = emptyList(),
)

@Serializable
data class WeatherCacheEntry(
    val providerId: String,
    val city: String,
    val current: WeatherCacheCurrent? = null,
    val currentFetchedAtMs: Long? = null,
    val forecast: List<WeatherCacheDay>? = null,
    val forecastFetchedAtMs: Long? = null,
)

@Serializable
data class WeatherCacheCurrent(
    val tempC: Double,
    val feelsLikeC: Double? = null,
    val humidity: Int? = null,
    val conditionCode: Int? = null,
    val conditionText: String,
    val city: String,
    val iconUrl: String? = null,
)

@Serializable
data class WeatherCacheDay(
    val dateIso: String,
    val high: Int,
    val low: Int,
    val conditionCode: Int,
    val conditionText: String,
    val iconUrl: String,
)

/**
 * Platform-specific persistent store for [WeatherCacheSnapshot]. The VM
 * hydrates synchronously at construction so the first widget render hits the
 * cache instead of going to the network.
 */
interface WeatherCacheStore {
    fun loadSync(): WeatherCacheSnapshot
    suspend fun save(snapshot: WeatherCacheSnapshot)
}

expect fun createWeatherCacheStore(): WeatherCacheStore
