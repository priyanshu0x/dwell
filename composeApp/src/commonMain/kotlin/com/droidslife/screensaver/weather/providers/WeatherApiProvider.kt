package com.droidslife.screensaver.weather.providers

import com.droidslife.screensaver.config.Constants
import com.droidslife.screensaver.weather.DayForecast
import com.droidslife.screensaver.weather.WeatherApi
import com.droidslife.screensaver.weather.WeatherApiException
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * [WeatherProvider] adapter over WeatherAPI.com.
 *
 * Wraps the existing [WeatherApi] HTTP client (now scoped to the WeatherAPI.com
 * upstream) and maps its responses to the provider-agnostic domain types.
 *
 * The provider requires an API key. When [apiKey] is blank the provider throws
 * [WeatherProviderUnconfigured] so the repository can surface an actionable
 * "Add an API key" state to the user.
 *
 * @suppress unused-parameter `http` — held for symmetry with [WttrInProvider]
 *                              and future direct calls that bypass [WeatherApi].
 */
@Suppress("UNUSED_PARAMETER")
class WeatherApiProvider(
    http: HttpClient,
    private val apiKey: String,
) : WeatherProvider {
    override val id: String = ID
    override val displayName: String = "WeatherAPI.com"
    override val requiresApiKey: Boolean = true

    private val api: WeatherApi = WeatherApi(
        client = http,
        // Stub a SecretStorage that just returns the provided key — the provider
        // pre-resolves the secret so the underlying [WeatherApi] doesn't need
        // host wiring of its own.
        secretStorage = object : com.droidslife.screensaver.settings.SecretStorage {
            override suspend fun read(id: String): String? = apiKey
            override suspend fun write(id: String, value: String) {}
            override suspend fun delete(id: String) {}
        },
        weatherApiKeySecretIdProvider = { Constants.WeatherApi.BASE_URL },
    )

    override suspend fun current(city: String): CurrentWeather {
        ensureConfigured()
        val data = api.getWeatherDataByCity(city)
        val iconUrl = data.current.condition.icon.let { if (it.startsWith("//")) "https:$it" else it }
        return CurrentWeather(
            tempC = data.current.tempC,
            feelsLikeC = data.current.feelslikeC,
            humidity = data.current.humidity,
            conditionCode = data.current.condition.code,
            conditionText = data.current.condition.text,
            city = data.location.name.ifBlank { city },
            iconUrl = iconUrl,
        )
    }

    override suspend fun forecast(city: String, days: Int): List<DayForecast> {
        ensureConfigured()
        val response = api.fetchForecast(city, days)
        return response.forecast.forecastDay.map { day ->
            val icon = day.day.condition.icon.let { if (it.startsWith("//")) "https:$it" else it }
            DayForecast(
                date = LocalDate.parse(day.date),
                high = day.day.maxTempC.roundToInt(),
                low = day.day.minTempC.roundToInt(),
                conditionCode = day.day.condition.code,
                conditionText = day.day.condition.text,
                iconUrl = icon,
            )
        }
    }

    private fun ensureConfigured() {
        if (apiKey.isBlank()) {
            throw WeatherProviderUnconfigured("WeatherAPI.com key is not configured")
        }
    }

    companion object {
        const val ID: String = "weatherapi"
    }
}

internal fun WeatherApiException.toProviderUnconfiguredOrSelf(): Throwable {
    val msg = message.orEmpty()
    return if (msg.contains("key is not configured", ignoreCase = true)) {
        WeatherProviderUnconfigured(msg)
    } else this
}
