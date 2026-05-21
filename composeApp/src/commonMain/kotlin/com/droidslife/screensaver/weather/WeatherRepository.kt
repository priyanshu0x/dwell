package com.droidslife.screensaver.weather

import com.droidslife.screensaver.location.LocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * Repository for weather data.
 * @param weatherApi The weather API client.
 * @param locationService The location service.
 */
class WeatherRepository(
    private val weatherApi: WeatherApi,
    private val locationService: LocationService
) {
    /**
     * Gets the weather data for the current location.
     * @return A flow of weather data.
     */
    fun getWeatherData(): Flow<WeatherState> = flow {
        emit(WeatherState.Loading)
        try {
            val location = locationService.getCurrentLocation()
            val weatherData = weatherApi.getWeatherData(location.latitude, location.longitude)
            emit(WeatherState.Success(weatherData, location))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(WeatherState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Fetches a multi-day forecast for the given city.
     */
    suspend fun forecast(city: String, days: Int = 5): Result<List<DayForecast>> = try {
        val response = weatherApi.fetchForecast(city, days)
        val mapped = response.forecast.forecastDay.map { day ->
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
        Result.success(mapped)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }
}

/**
 * Sealed class representing the state of weather data.
 */
sealed class WeatherState {
    /**
     * Loading state.
     */
    object Loading : WeatherState()

    /**
     * Success state.
     * @param weatherData The weather data.
     * @param location The location.
     */
    data class Success(
        val weatherData: WeatherData,
        val location: com.droidslife.screensaver.location.Location
    ) : WeatherState()

    /**
     * Error state.
     * @param message The error message.
     */
    data class Error(val message: String) : WeatherState()
}
