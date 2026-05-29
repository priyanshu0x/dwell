package com.droidslife.screensaver.weather.providers

import com.droidslife.screensaver.weather.DayForecast
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * [WeatherProvider] backed by https://wttr.in (community-run, no API key).
 *
 * Hits `https://wttr.in/<city>?format=j1`, which returns a single JSON document
 * containing both the current conditions and a 3-day forecast. The shape mirrors
 * the World Weather Online wire format the project derives from:
 *
 *  - `current_condition[0]`  → temperature, feels-like, humidity, weather code/desc.
 *  - `weather[]`             → one entry per upcoming day with `maxtempC`, `mintempC`,
 *                              `hourly[]`, and a representative `weatherDesc`.
 *
 * wttr.in is intentionally slow and rate-limited (~60 req/min/IP). We apply a
 * tight per-request timeout and surface failures as plain exceptions so the
 * repository can map them to the existing error / loading states.
 *
 * No API key is required; the only soft constraint upstream is the
 * `User-Agent` — wttr.in serves ANSI to curl-style agents and JSON to others.
 * We send a custom UA so we are always treated as a structured-data client.
 */
class WttrInProvider(
    private val http: HttpClient,
) : WeatherProvider {
    override val id: String = ID
    override val displayName: String = "wttr.in (no key needed)"
    override val requiresApiKey: Boolean = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    override suspend fun current(city: String): CurrentWeather {
        try {
            val resolvedCity = city.ifBlank { DEFAULT_CITY }
            val body = fetchJson(resolvedCity)
            val condition = body.current.firstOrNull()
                ?: error("wttr.in returned an empty current_condition for $resolvedCity")
            return CurrentWeather(
                tempC = condition.tempC.toDoubleOrNull() ?: 0.0,
                feelsLikeC = condition.feelsLikeC?.toDoubleOrNull(),
                humidity = condition.humidity?.toIntOrNull(),
                windKph = condition.windspeedKmph?.toDoubleOrNull(),
                visKm = condition.visibility?.toDoubleOrNull(),
                conditionCode = condition.weatherCode?.toIntOrNull(),
                conditionText = condition.weatherDesc.firstOrNull()?.value?.trim().orEmpty(),
                city = body.cityLabel().ifBlank { resolvedCity },
                iconUrl = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to load weather for $city from wttr.in", e)
        }
    }

    override suspend fun forecast(city: String, days: Int): List<DayForecast> {
        try {
            val resolvedCity = city.ifBlank { DEFAULT_CITY }
            val body = fetchJson(resolvedCity)
            return body.weather.take(days).map { day ->
                val descText = day.hourly
                    // Pick the noon entry (time = "1200") as the headline condition,
                    // falling back to the first sample if hourly is sparse.
                    .firstOrNull { it.time == "1200" }
                    ?.weatherDesc?.firstOrNull()?.value
                    ?: day.hourly.firstOrNull()?.weatherDesc?.firstOrNull()?.value
                    ?: ""
                val code = day.hourly
                    .firstOrNull { it.time == "1200" }
                    ?.weatherCode?.toIntOrNull()
                    ?: day.hourly.firstOrNull()?.weatherCode?.toIntOrNull()
                    ?: 0
                DayForecast(
                    date = LocalDate.parse(day.date),
                    high = day.maxtempC.toDoubleOrNull()?.roundToInt() ?: 0,
                    low = day.mintempC.toDoubleOrNull()?.roundToInt() ?: 0,
                    conditionCode = mapWwoToWeatherApiCode(code),
                    conditionText = descText.trim(),
                    iconUrl = "",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to load forecast for $city from wttr.in", e)
        }
    }

    private suspend fun fetchJson(city: String): WttrInResponse {
        val url = URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            host = "wttr.in"
            // wttr.in expects the location as the URL path; spaces become "+".
            path(city.replace(' ', '+'))
            parameters.append("format", "j1")
        }.buildString()
        // wttr.in is community-run and can be slow; bound each request with a
        // tight timeout so the widget never hangs while a fetch grinds.
        val text = withTimeout(REQUEST_TIMEOUT_MS) {
            http.get(url) {
                // wttr.in serves text to curl/wget UAs; force a non-default UA so the
                // service consistently returns JSON. The actual identifier is
                // cosmetic — wttr.in only sniffs for the "curl"/"wget" prefix.
                header("User-Agent", "Dwell/1.0 (+https://dwell.app)")
            }.body<String>()
        }
        return json.decodeFromString(WttrInResponse.serializer(), text)
    }

    companion object {
        const val ID: String = "wttr"

        /** wttr.in resolves a blank path to the requestor's IP geolocation; we
         *  still send an explicit fallback to keep behavior deterministic when
         *  the host hasn't configured a city. */
        // Reuses the app-wide single-source-of-truth fallback so we don't have
        // to chase scattered city strings when the default changes.
        private val DEFAULT_CITY: String
            get() = com.droidslife.screensaver.location.FALLBACK_CITY

        // Conservative; wttr.in regularly takes 2-3s to respond. Bound via
        // `withTimeout` to keep this provider stricter than the shared client.
        private const val REQUEST_TIMEOUT_MS: Long = 5_000

        /**
         * Map a World Weather Online weather code (used by wttr.in) into the
         * closest WeatherAPI.com code so existing glyph mappings in the
         * Forecast widget keep working without per-provider switches.
         *
         * The mapping is coarse on purpose — only the buckets that the
         * forecast widget's [conditionGlyph] cares about are routed; everything
         * else falls through to a generic cloudy code.
         */
        internal fun mapWwoToWeatherApiCode(wwoCode: Int): Int = when (wwoCode) {
            113 -> 1000                       // Clear / Sunny
            116 -> 1003                       // Partly cloudy
            119 -> 1006                       // Cloudy
            122 -> 1009                       // Overcast
            143, 248, 260 -> 1135             // Mist / Fog
            176, 263, 266, 281, 284,
            293, 296, 299, 302, 305, 308,
            311, 314, 353, 356, 359 -> 1183  // Rain-ish
            179, 182, 185, 227, 230, 317,
            320, 323, 326, 329, 332, 335,
            338, 350, 362, 365, 368, 371,
            374, 377 -> 1213                  // Sleet / Snow
            386, 389, 392, 395 -> 1273        // Thunder
            else -> 1006
        }
    }
}

// -----------------------------------------------------------------------------
// JSON shapes
// -----------------------------------------------------------------------------
//
// wttr.in's `?format=j1` document is verbose; we only deserialize the fields the
// widgets need, leaning on the host JSON config to drop the rest.

@Serializable
internal data class WttrInResponse(
    @SerialName("current_condition") val current: List<WttrCurrentCondition> = emptyList(),
    @SerialName("weather") val weather: List<WttrWeatherDay> = emptyList(),
    @SerialName("nearest_area") val nearestArea: List<WttrNearestArea> = emptyList(),
) {
    fun cityLabel(): String {
        val area = nearestArea.firstOrNull() ?: return ""
        return area.areaName.firstOrNull()?.value?.trim().orEmpty()
    }
}

@Serializable
internal data class WttrCurrentCondition(
    @SerialName("temp_C") val tempC: String = "0",
    @SerialName("FeelsLikeC") val feelsLikeC: String? = null,
    val humidity: String? = null,
    val windspeedKmph: String? = null,
    val visibility: String? = null,
    val weatherCode: String? = null,
    val weatherDesc: List<WttrValue> = emptyList(),
)

@Serializable
internal data class WttrWeatherDay(
    val date: String,
    @SerialName("maxtempC") val maxtempC: String = "0",
    @SerialName("mintempC") val mintempC: String = "0",
    val hourly: List<WttrHourly> = emptyList(),
)

@Serializable
internal data class WttrHourly(
    val time: String,
    val weatherCode: String? = null,
    val weatherDesc: List<WttrValue> = emptyList(),
)

@Serializable
internal data class WttrNearestArea(
    val areaName: List<WttrValue> = emptyList(),
)

@Serializable
internal data class WttrValue(
    val value: String,
)
