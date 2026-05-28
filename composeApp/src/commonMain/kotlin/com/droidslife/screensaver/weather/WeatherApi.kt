package com.droidslife.screensaver.weather

import com.droidslife.screensaver.config.Constants
import com.droidslife.screensaver.settings.SecretStorage
import com.droidslife.screensaver.settings.WEATHER_API_KEY_SECRET_ID
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    /**
     * Fetches weather data for the given location.
     * @param latitude The latitude of the location.
     * @param longitude The longitude of the location.
     * @return The weather data for the location.
     */
    suspend fun getWeatherData(
        latitude: Double,
        longitude: Double
    ): WeatherData = withContext(Dispatchers.Default) {
        try {
            val apiKey = apiKey()
            client.get("${Constants.WeatherApi.BASE_URL}${Constants.WeatherApi.CURRENT_WEATHER_ENDPOINT}") {
                parameter("q", "$latitude,$longitude")
                parameter("key", apiKey)
            }.body<WeatherData>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WeatherApiException("Failed to load weather for coordinates $latitude,$longitude", e)
        }
    }

    /**
     * Fetches weather data for the given city name.
     * @param cityName The name of the city.
     * @return The weather data for the city.
     */
    suspend fun getWeatherDataByCity(cityName: String): WeatherData = withContext(Dispatchers.Default) {
        try {
            val apiKey = apiKey()
            client.get("${Constants.WeatherApi.BASE_URL}${Constants.WeatherApi.CURRENT_WEATHER_ENDPOINT}") {
                parameter("q", cityName)
                parameter("key", apiKey)
            }.body<WeatherData>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WeatherApiException("Failed to load weather for $cityName", e)
        }
    }

    /**
     * Fetches a multi-day forecast for the given city.
     * @param cityName The city to forecast.
     * @param days Number of forecast days (1..10 per WeatherAPI).
     */
    suspend fun fetchForecast(cityName: String, days: Int = 5): ForecastResponse =
        withContext(Dispatchers.Default) {
            try {
                val apiKey = apiKey()
                client.get("${Constants.WeatherApi.BASE_URL}/forecast.json") {
                    parameter("q", cityName)
                    parameter("days", days)
                    parameter("aqi", "no")
                    parameter("alerts", "no")
                    parameter("key", apiKey)
                }.body<ForecastResponse>()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw WeatherApiException("Failed to load forecast for $cityName", e)
            }
        }

    /**
     * Searches for cities matching the given query.
     * @param query The search query.
     * @return A list of city search results.
     */
    suspend fun searchCity(query: String): List<CitySearchResult> = withContext(Dispatchers.Default) {
        try {
            val apiKey = apiKey()
            val response = client.get("${Constants.WeatherApi.BASE_URL}/search.json") {
                parameter("q", query)
                parameter("key", apiKey)
            }.body<String>()

            val json = Json { ignoreUnknownKeys = true }
            val jsonArray = json.parseToJsonElement(response).jsonArray

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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WeatherApiException("Failed to search cities for '$query'", e)
        }
    }

    private suspend fun apiKey(): String {
        return weatherApiKeyProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }
            ?: secretStorage.read(weatherApiKeySecretIdProvider())?.trim()?.takeIf { it.isNotBlank() }
            ?: System.getenv("WEATHERAPI")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw WeatherApiException("WeatherAPI key is not configured")
    }
}

class WeatherApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
