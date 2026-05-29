package com.droidslife.screensaver.weather.providers

import com.droidslife.screensaver.weather.WeatherApiException
import com.droidslife.screensaver.weather.WeatherApiFailure
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

    @Test
    fun rateLimitMapsToRateLimitedFailure() {
        val mapped = WeatherApiException(
            message = "API key has exceeded calls per month",
            httpStatusCode = 429,
            upstreamCode = 2007,
            upstreamMessage = "API key has exceeded calls per month",
            failure = WeatherApiFailure.RateLimited,
        ).toProviderFailure()

        assertTrue(mapped is WeatherProviderRateLimited)
        assertEquals("WeatherAPI rate limit reached - check plan/key", mapped.message)
    }
}
