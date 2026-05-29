package com.droidslife.screensaver.weather

import com.droidslife.screensaver.config.Constants
import com.droidslife.screensaver.settings.SecretStorage
import com.droidslife.screensaver.settings.WEATHER_API_KEY_SECRET_ID
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the WeatherAPI.com API.
 * Documentation: https://app.swaggerhub.com/apis-docs/WeatherAPI.com/WeatherAPI/1.0.2
 */
class WeatherApi(
    private val client: HttpClient,
    private val secretStorage: SecretStorage,
    private val weatherApiKeySecretIdProvider: () -> String = { WEATHER_API_KEY_SECRET_ID },
    private val weatherApiKeyProvider: (suspend () -> String?)? = null,
) {
    private val validatedClient = client.config {
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is ResponseException) {
                    throw cause.toWeatherApiException("WeatherAPI request failed")
                }
            }
        }
    }

    /**
     * Fetches weather data for the given location.
     * @param latitude The latitude of the location.
     * @param longitude The longitude of the location.
     * @return The weather data for the location.
     */
    suspend fun getWeatherData(
        latitude: Double,
        longitude: Double
    ): WeatherData =
        withWeatherApiErrors("Failed to load weather for coordinates $latitude,$longitude") {
            val apiKey = apiKey()
            validatedClient.get("${Constants.WeatherApi.BASE_URL}${Constants.WeatherApi.CURRENT_WEATHER_ENDPOINT}") {
                parameter("q", "$latitude,$longitude")
                parameter("key", apiKey)
            }.body()
        }

    /**
     * Fetches weather data for the given city name.
     * @param cityName The name of the city.
     * @return The weather data for the city.
     */
    suspend fun getWeatherDataByCity(cityName: String): WeatherData =
        withWeatherApiErrors("Failed to load weather for $cityName") {
            val apiKey = apiKey()
            validatedClient.get("${Constants.WeatherApi.BASE_URL}${Constants.WeatherApi.CURRENT_WEATHER_ENDPOINT}") {
                parameter("q", cityName)
                parameter("key", apiKey)
            }.body()
        }

    /**
     * Fetches a multi-day forecast for the given city.
     * @param cityName The city to forecast.
     * @param days Number of forecast days (1..10 per WeatherAPI).
     */
    suspend fun fetchForecast(cityName: String, days: Int = 5): ForecastResponse =
        withWeatherApiErrors("Failed to load forecast for $cityName") {
            val apiKey = apiKey()
            validatedClient.get("${Constants.WeatherApi.BASE_URL}/forecast.json") {
                parameter("q", cityName)
                parameter("days", days)
                parameter("aqi", "no")
                parameter("alerts", "no")
                parameter("key", apiKey)
            }.body()
        }

    /**
     * Searches for cities matching the given query.
     * @param query The search query.
     * @return A list of city search results.
     */
    suspend fun searchCity(query: String): List<CitySearchResult> =
        withWeatherApiErrors("Failed to search cities for '$query'") {
            val apiKey = apiKey()
            val response = validatedClient.get("${Constants.WeatherApi.BASE_URL}/search.json") {
                parameter("q", query)
                parameter("key", apiKey)
            }
            val body = response.bodyAsText()

            val json = Json { ignoreUnknownKeys = true }
            val jsonArray = json.parseToJsonElement(body).jsonArray

            jsonArray.map { jsonElement ->
                val jsonObject = jsonElement.jsonObject
                CitySearchResult(
                    id = jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    name = jsonObject["name"]?.jsonPrimitive?.content ?: "",
                    region = jsonObject["region"]?.jsonPrimitive?.content ?: "",
                    country = jsonObject["country"]?.jsonPrimitive?.content ?: "",
                    lat = jsonObject["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    lon = jsonObject["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                )
            }
        }

    private suspend fun apiKey(): String {
        return weatherApiKeyProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }
            ?: secretStorage.read(weatherApiKeySecretIdProvider())?.trim()?.takeIf { it.isNotBlank() }
            ?: System.getenv("WEATHERAPI")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw WeatherApiException(
                message = "WeatherAPI key is not configured",
                failure = WeatherApiFailure.MissingKey,
            )
    }

    private suspend fun <T> withWeatherApiErrors(
        fallbackMessage: String,
        block: suspend () -> T,
    ): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: WeatherApiException) {
            throw e.withContext(fallbackMessage)
        } catch (e: JsonConvertException) {
            throw WeatherApiException(
                message = "$fallbackMessage: could not parse WeatherAPI response",
                cause = e,
                failure = WeatherApiFailure.Schema(e),
            )
        } catch (e: SerializationException) {
            throw WeatherApiException(
                message = "$fallbackMessage: could not parse WeatherAPI response",
                cause = e,
                failure = WeatherApiFailure.Schema(e),
            )
        } catch (e: IOException) {
            throw WeatherApiException(
                message = "$fallbackMessage: network unavailable",
                cause = e,
                failure = WeatherApiFailure.Transient(e),
            )
        }
    }

}

private suspend fun ResponseException.toWeatherApiException(fallbackMessage: String): WeatherApiException {
    val error = runCatching {
        weatherApiErrorJson.decodeFromString(WeatherApiErrorEnvelope.serializer(), response.bodyAsText()).error
    }.getOrNull()
    val upstreamMessage = error?.message?.takeIf { it.isNotBlank() }
    val statusCode = response.status.value
    val failure = when {
        error?.code == 1002 -> WeatherApiFailure.MissingKey
        statusCode == 429 || error?.code == 2007 -> WeatherApiFailure.RateLimited
        statusCode == 401 || statusCode == 403 || error?.code in credentialErrorCodes ->
            WeatherApiFailure.Unauthorized
        statusCode >= 500 -> WeatherApiFailure.Transient(this)
        else -> WeatherApiFailure.Unknown
    }
    return WeatherApiException(
        message = upstreamMessage?.let { "$fallbackMessage: $it" }
            ?: "$fallbackMessage (HTTP ${response.status.value} ${response.status.description})",
        cause = this,
        httpStatusCode = response.status.value,
        upstreamCode = error?.code,
        upstreamMessage = upstreamMessage,
        failure = failure,
    )
}

private val credentialErrorCodes = setOf(2006, 2008, 2009)

private val weatherApiErrorJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

class WeatherApiException(
    message: String,
    cause: Throwable? = null,
    val httpStatusCode: Int? = null,
    val upstreamCode: Int? = null,
    val upstreamMessage: String? = null,
    val failure: WeatherApiFailure = WeatherApiFailure.Unknown,
) : Exception(message, cause)

private fun WeatherApiException.withContext(fallbackMessage: String): WeatherApiException {
    val detail = upstreamMessage?.takeIf { it.isNotBlank() }
        ?: message?.takeIf { it.isNotBlank() }
    return WeatherApiException(
        message = detail?.let { "$fallbackMessage: $it" } ?: fallbackMessage,
        cause = cause,
        httpStatusCode = httpStatusCode,
        upstreamCode = upstreamCode,
        upstreamMessage = upstreamMessage,
        failure = failure,
    )
}

sealed interface WeatherApiFailure {
    data object MissingKey : WeatherApiFailure
    data object Unauthorized : WeatherApiFailure
    data object RateLimited : WeatherApiFailure
    data class Transient(val cause: Throwable) : WeatherApiFailure
    data class Schema(val cause: Throwable) : WeatherApiFailure
    data object Unknown : WeatherApiFailure
}

@Serializable
private data class WeatherApiErrorEnvelope(
    val error: WeatherApiError? = null,
)

@Serializable
private data class WeatherApiError(
    val code: Int? = null,
    val message: String? = null,
)

/**
 * Data class representing the weather data returned by the WeatherAPI.com API.
 */
@Serializable
data class WeatherData(
    val location: Location,
    val current: Current
)

@Serializable
data class Location(
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    @SerialName("tz_id") val tzId: String,
    @SerialName("localtime_epoch") val localtimeEpoch: Long,
    val localtime: String
)

@Serializable
data class Current(
    @SerialName("last_updated_epoch") val lastUpdatedEpoch: Long,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("temp_c") val tempC: Double,
    @SerialName("temp_f") val tempF: Double,
    @SerialName("is_day") val isDay: Int,
    val condition: Condition,
    @SerialName("wind_mph") val windMph: Double,
    @SerialName("wind_kph") val windKph: Double,
    @SerialName("wind_degree") val windDegree: Int,
    @SerialName("wind_dir") val windDir: String,
    @SerialName("pressure_mb") val pressureMb: Double,
    @SerialName("pressure_in") val pressureIn: Double,
    @SerialName("precip_mm") val precipMm: Double,
    @SerialName("precip_in") val precipIn: Double,
    val humidity: Int,
    val cloud: Int,
    @SerialName("feelslike_c") val feelslikeC: Double,
    @SerialName("feelslike_f") val feelslikeF: Double,
    @SerialName("vis_km") val visKm: Double,
    @SerialName("vis_miles") val visMiles: Double,
    val uv: Double,
    @SerialName("gust_mph") val gustMph: Double,
    @SerialName("gust_kph") val gustKph: Double
)

@Serializable
data class Condition(
    val text: String,
    val icon: String,
    val code: Int
)

/**
 * Forecast response from WeatherAPI.com `forecast.json`.
 */
@Serializable
data class ForecastResponse(
    val location: Location,
    val current: Current,
    val forecast: Forecast,
)

@Serializable
data class Forecast(
    @SerialName("forecastday") val forecastDay: List<ForecastDay>,
)

@Serializable
data class ForecastDay(
    val date: String,
    @SerialName("date_epoch") val dateEpoch: Long,
    val day: ForecastDayData,
)

@Serializable
data class ForecastDayData(
    @SerialName("maxtemp_c") val maxTempC: Double,
    @SerialName("mintemp_c") val minTempC: Double,
    @SerialName("maxtemp_f") val maxTempF: Double? = null,
    @SerialName("mintemp_f") val minTempF: Double? = null,
    val condition: Condition,
)

/**
 * Data class representing a city search result from the WeatherAPI.com API.
 */
@Serializable
data class CitySearchResult(
    val id: Int,
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double
)
