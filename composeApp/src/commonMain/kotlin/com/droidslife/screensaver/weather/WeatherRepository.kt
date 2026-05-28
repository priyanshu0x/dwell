package com.droidslife.screensaver.weather

import com.droidslife.screensaver.location.FALLBACK_CITY
import com.droidslife.screensaver.location.LocationService
import com.droidslife.screensaver.settings.SecretStorage
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.weather.providers.CurrentWeather
import com.droidslife.screensaver.weather.providers.WeatherApiProvider
import com.droidslife.screensaver.weather.providers.WeatherProvider
import com.droidslife.screensaver.weather.providers.WeatherProviderUnconfigured
import com.droidslife.screensaver.weather.providers.WttrInProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Thin façade over the configurable set of [WeatherProvider] adapters.
 *
 * The repository picks a provider per call using the user's saved widget
 * configuration so swapping providers (Settings → Widgets → Weather → Source)
 * doesn't require restarting the dashboard.
 *
 * @param httpClient        Shared Ktor client used by all provider adapters.
 * @param locationService   Geo helper used by the legacy [getWeatherData] path.
 * @param settingsViewModel Read-only access to widget configs + the saved
 *                          WeatherAPI key (still surfaced through the existing
 *                          secret store).
 * @param secretStorage     Backing store the repository reads the WeatherAPI
 *                          key from when the user selects that provider.
 */
class WeatherRepository(
    private val httpClient: HttpClient,
    private val locationService: LocationService,
    private val settingsViewModel: SettingsViewModel,
    private val secretStorage: SecretStorage,
) {
    /**
     * Resolves the active provider from settings. Returns wttr.in by default so
     * the widget works out of the box without any user configuration.
     */
    private suspend fun activeProvider(): WeatherProvider {
        val providerId = providerIdFromSettings()
        return when (providerId) {
            WeatherApiProvider.ID -> {
                val key = secretStorage
                    .read(settingsViewModel.settings.weatherApiKeySecretId)
                    ?.trim()
                    .orEmpty()
                WeatherApiProvider(httpClient, key)
            }
            else -> WttrInProvider(httpClient)
        }
    }

    /**
     * Exposed so callers that cache results can key the cache by provider —
     * switching the source in Settings then doesn't surface stale data from
     * the previous one.
     */
    fun activeProviderId(): String = providerIdFromSettings()

    private fun providerIdFromSettings(): String {
        val widgetConfig = settingsViewModel.settings.widgetConfigs[WEATHER_WIDGET_ID]
            ?: return WttrInProvider.ID
        return widgetConfig["provider"]?.let { element ->
            (element as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: WttrInProvider.ID
    }

    /**
     * Streams current weather using the device location and the configured
     * provider. Kept for callers that pre-date city-driven loads.
     */
    fun getWeatherData(): Flow<WeatherState> = flow {
        emit(WeatherState.Loading)
        try {
            val location = locationService.getCurrentLocation()
            val provider = activeProvider()
            val current = provider.current(location.city.ifBlank { FALLBACK_CITY })
            emit(WeatherState.Success(current, location))
        } catch (e: CancellationException) {
            throw e
        } catch (e: WeatherProviderUnconfigured) {
            emit(WeatherState.Unconfigured)
        } catch (e: Exception) {
            emit(WeatherState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Fetches current weather for an explicit [city] through the configured
     * provider.
     */
    suspend fun current(city: String): Result<CurrentWeather> = try {
        Result.success(activeProvider().current(city))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Fetches a multi-day forecast for [city] through the configured provider.
     */
    suspend fun forecast(city: String, days: Int = 5): Result<List<DayForecast>> = try {
        Result.success(activeProvider().forecast(city, days))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val WEATHER_WIDGET_ID: String = "com.droidslife.screensaver.weather"
    }
}

/**
 * UI-facing state for the current-weather fetch. Provider-agnostic — the
 * underlying transport (wttr.in vs WeatherAPI.com) is intentionally hidden.
 */
sealed class WeatherState {
    /** Initial / in-flight state. */
    object Loading : WeatherState()

    /** Successful fetch carrying the provider-agnostic [CurrentWeather]. */
    data class Success(
        val current: CurrentWeather,
        val location: com.droidslife.screensaver.location.Location,
    ) : WeatherState()

    /** Generic failure (network, parse, upstream 5xx). */
    data class Error(val message: String) : WeatherState()

    /**
     * The active provider requires credentials the host hasn't configured. The
     * UI surfaces this with an actionable "Add an API key" affordance.
     */
    object Unconfigured : WeatherState()
}
