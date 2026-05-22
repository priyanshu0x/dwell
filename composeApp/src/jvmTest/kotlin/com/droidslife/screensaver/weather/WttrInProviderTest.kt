package com.droidslife.screensaver.weather

import com.droidslife.screensaver.weather.providers.WttrInProvider
import com.droidslife.screensaver.weather.providers.WttrInResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the wttr.in `?format=j1` JSON shape WttrInProvider parses against.
 *
 * The wttr.in document is verbose; we only sample the fields the widget cares
 * about (current temp / feels-like / humidity / weatherCode / weatherDesc, plus
 * per-day max/min and the noon `hourly[]` entry). If wttr.in ever changes any
 * of those keys this test fails fast.
 *
 * We deliberately exercise the JSON contract directly instead of round-tripping
 * an HTTP call so the test doesn't depend on a Ktor mock engine in the
 * common-test dependencies.
 */
class WttrInProviderTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parsesHeadlineFieldsOutOfJ1Response() {
        val response = json.decodeFromString(WttrInResponse.serializer(), J1_SAMPLE)

        val current = response.current.first()
        assertEquals("21", current.tempC)
        assertEquals("22", current.feelsLikeC)
        assertEquals("78", current.humidity)
        assertEquals("116", current.weatherCode)
        assertEquals("Partly cloudy", current.weatherDesc.first().value)

        assertEquals("Mumbai", response.cityLabel())

        assertEquals(2, response.weather.size)
        val today = response.weather.first()
        assertEquals("2026-05-22", today.date)
        assertEquals("28", today.maxtempC)
        assertEquals("19", today.mintempC)
        // The provider picks the `time = "1200"` sample as the headline
        // condition; verify it's present.
        val noon = today.hourly.firstOrNull { it.time == "1200" }
        assertNotNull(noon)
        assertEquals("116", noon.weatherCode)
        assertEquals("Partly cloudy", noon.weatherDesc.first().value)
    }

    @Test
    fun toleratesMissingOptionalFields() {
        // wttr.in sometimes omits `nearest_area` and per-hour `weatherDesc`
        // entries. Provider must not throw.
        val response = json.decodeFromString(WttrInResponse.serializer(), MINIMAL_SAMPLE)
        assertTrue(response.nearestArea.isEmpty())
        assertEquals("", response.cityLabel())
        assertEquals(1, response.weather.size)
        assertEquals(1, response.weather.first().hourly.size)
    }

    @Test
    fun wwoCodeMappingCoversTheHeadlineBuckets() {
        // The forecast widget renders one glyph per WeatherAPI-code bucket; the
        // provider remaps wttr.in's World Weather Online codes into those
        // buckets so a single glyph table works across providers.
        assertEquals(1000, WttrInProvider.mapWwoToWeatherApiCode(113))     // Clear
        assertEquals(1003, WttrInProvider.mapWwoToWeatherApiCode(116))     // Partly cloudy
        assertEquals(1183, WttrInProvider.mapWwoToWeatherApiCode(266))     // Light drizzle
        assertEquals(1213, WttrInProvider.mapWwoToWeatherApiCode(338))     // Heavy snow
        assertEquals(1273, WttrInProvider.mapWwoToWeatherApiCode(386))     // Thundery showers
        // Unknown codes fall through to a generic cloudy bucket so the glyph
        // table still renders something sensible.
        assertEquals(1006, WttrInProvider.mapWwoToWeatherApiCode(99_999))
    }

    companion object {
        private val J1_SAMPLE: String = """
            {
              "current_condition": [
                {
                  "temp_C": "21",
                  "FeelsLikeC": "22",
                  "humidity": "78",
                  "weatherCode": "116",
                  "weatherDesc": [{"value": "Partly cloudy"}]
                }
              ],
              "nearest_area": [
                {"areaName": [{"value": "Mumbai"}]}
              ],
              "weather": [
                {
                  "date": "2026-05-22",
                  "maxtempC": "28",
                  "mintempC": "19",
                  "hourly": [
                    {"time": "0",    "weatherCode": "113", "weatherDesc": [{"value":"Clear"}]},
                    {"time": "1200", "weatherCode": "116", "weatherDesc": [{"value":"Partly cloudy"}]},
                    {"time": "1800", "weatherCode": "119", "weatherDesc": [{"value":"Cloudy"}]}
                  ]
                },
                {
                  "date": "2026-05-23",
                  "maxtempC": "31",
                  "mintempC": "22",
                  "hourly": [
                    {"time": "1200", "weatherCode": "113", "weatherDesc": [{"value":"Sunny"}]}
                  ]
                }
              ]
            }
        """.trimIndent()

        private val MINIMAL_SAMPLE: String = """
            {
              "current_condition": [
                {"temp_C": "10", "weatherDesc": [{"value": "Foggy"}]}
              ],
              "weather": [
                {
                  "date": "2026-05-22",
                  "maxtempC": "12",
                  "mintempC": "8",
                  "hourly": [
                    {"time": "1200"}
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
