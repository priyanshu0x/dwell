package com.droidslife.screensaver.weather.providers

import com.droidslife.screensaver.weather.WeatherApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WeatherApiProviderTest {
    @Test
    fun invalidApiKeyMapsToCredentialFailure() {
        val mapped = WeatherApiException(
            message = "Failed to load weather: API key provided is invalid",
            httpStatusCode = 401,
            upstreamCode = 2006,
            upstreamMessage = "API key provided is invalid",
        ).toProviderFailure()

        assertTrue(mapped is WeatherProviderCredentialFailure)
        assertEquals("WeatherAPI key/account problem - update it in settings", mapped.message)
    }

    @Test
    fun missingApiKeyMapsToUnconfigured() {
        val mapped = WeatherApiException(
            message = "WeatherAPI key is not configured",
            upstreamCode = 1002,
            upstreamMessage = "API key not provided",
        ).toProviderFailure()

        assertTrue(mapped is WeatherProviderUnconfigured)
    }

    @Test
    fun transportFailureStaysGeneric() {
        val error = WeatherApiException("Request timed out")

        assertSame(error, error.toProviderFailure())
    }
}
