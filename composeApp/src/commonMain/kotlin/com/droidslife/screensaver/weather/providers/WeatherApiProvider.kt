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
        val data = try {
            api.getWeatherDataByCity(city)
        } catch (e: WeatherApiException) {
            throw e.toProviderFailure()
        }
        val iconUrl = data.current.condition.icon.let { if (it.startsWith("//")) "https:$it" else it }
        return CurrentWeather(
            tempC = data.current.tempC,
            feelsLikeC = data.current.feelslikeC,
            humidity = data.current.humidity,
            windKph = data.current.windKph,
            visKm = data.current.visKm,
            conditionCode = data.current.condition.code,
            conditionText = data.current.condition.text,
            city = data.location.name.ifBlank { city },
            iconUrl = iconUrl,
        )
    }

    override suspend fun forecast(city: String, days: Int): List<DayForecast> {
        ensureConfigured()
        val response = try {
            api.fetchForecast(city, days)
        } catch (e: WeatherApiException) {
            throw e.toProviderFailure()
        }
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

internal fun WeatherApiException.toProviderFailure(): Throwable {
    if (isMissingKey()) {
        return WeatherProviderUnconfigured(message.orEmpty())
    }
    if (isCredentialOrAccountFailure()) {
        return WeatherProviderCredentialFailure("WeatherAPI key/account problem - update it in settings")
    }
    return this
}

private fun WeatherApiException.isMissingKey(): Boolean {
    val msg = combinedMessage()
    return upstreamCode == 1002 || msg.contains("key is not configured") || msg.contains("key not provided")
}

private fun WeatherApiException.isCredentialOrAccountFailure(): Boolean {
    if (httpStatusCode == 401 || httpStatusCode == 403) return true
    if (upstreamCode in setOf(2006, 2007, 2008, 2009)) return true
    val msg = combinedMessage()
    return listOf("invalid api key", "api key provided is invalid", "api key has been disabled")
        .any { msg.contains(it) }
}

private fun WeatherApiException.combinedMessage(): String =
    listOfNotNull(message, upstreamMessage, cause?.message).joinToString(" ").lowercase()
