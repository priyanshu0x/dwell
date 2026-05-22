package com.droidslife.screensaver.weather.providers

import com.droidslife.screensaver.weather.DayForecast

/**
 * Provider-agnostic representation of the current weather. Each [WeatherProvider]
 * maps its native response into this shape so widgets render uniformly regardless
 * of the underlying source.
 */
data class CurrentWeather(
    val tempC: Double,
    val feelsLikeC: Double?,
    val humidity: Int?,
    val conditionCode: Int?,
    val conditionText: String,
    val city: String,
    val iconUrl: String? = null,
)

/**
 * Port for the Weather widget. Implementations adapt a specific upstream API
 * (e.g. wttr.in, WeatherAPI.com) into the shared [CurrentWeather] / [DayForecast]
 * domain types.
 *
 * Implementations must throw on transport / parse failures so the repository
 * can surface a typed error state to the UI.
 */
interface WeatherProvider {
    /** Stable provider id persisted in widget config (e.g. `"wttr"`, `"weatherapi"`). */
    val id: String

    /** Human-readable label shown in the Settings provider picker. */
    val displayName: String

    /** Whether the provider requires the user to configure an API key secret. */
    val requiresApiKey: Boolean

    /**
     * Fetches the current weather for [city]. Throws on failure.
     */
    suspend fun current(city: String): CurrentWeather

    /**
     * Fetches a multi-day forecast for [city]. Throws on failure.
     *
     * Providers may return fewer than [days] entries when the upstream API caps
     * the window.
     */
    suspend fun forecast(city: String, days: Int): List<DayForecast>
}

/**
 * Thrown by provider implementations when the upstream API requires credentials
 * the host has not configured. The repository maps this to an "unconfigured"
 * UI state so the user can be guided to Settings.
 */
class WeatherProviderUnconfigured(message: String) : Exception(message)
