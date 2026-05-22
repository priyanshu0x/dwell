package com.droidslife.screensaver.weather

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droidslife.screensaver.location.FALLBACK_CITY
import com.droidslife.screensaver.location.Location
import com.droidslife.screensaver.location.TimeZoneUtils
import com.droidslife.screensaver.settings.PreferencesRepository
import com.droidslife.screensaver.weather.providers.WeatherProviderUnconfigured
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * View model for weather data.
 *
 * Loads current weather + forecast through the provider-agnostic
 * [WeatherRepository]. The optional [weatherApi] dependency is retained so the
 * legacy WeatherAPI.com-only `searchCity` autocomplete keeps working when the
 * user has configured that provider.
 */
class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val weatherApi: WeatherApi,
    private val preferencesRepository: PreferencesRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The current state of weather data.
     */
    var state by mutableStateOf<WeatherState>(WeatherState.Loading)
        private set

    /**
     * The current state of city search.
     */
    var citySearchState by mutableStateOf<CitySearchState>(CitySearchState.Initial)
        private set

    /**
     * The currently selected city.
     */
    var selectedCity by mutableStateOf<String?>(null)
        private set

    private val _forecast = MutableStateFlow<ForecastState>(ForecastState.Loading)

    /**
     * 5-day forecast state. Tied to whichever city is currently selected by
     * the user; [refreshForecast] is invoked whenever the current weather is
     * (re)loaded.
     */
    val forecast: StateFlow<ForecastState> = _forecast.asStateFlow()

    init {
        viewModelScope.launch {
            // Check if there's a stored city preference
            val storedCity = preferencesRepository.getCurrentCity()

            if (storedCity != null) {
                loadWeatherDataForCity(storedCity)
            } else {
                // If there's no stored city, try to get a city based on the timezone
                val timeZoneCity = TimeZoneUtils.getCityFromTimeZone(weatherApi)

                val city = timeZoneCity ?: FALLBACK_CITY
                loadWeatherDataForCity(city)
                preferencesRepository.setCurrentCity(city)
            }
        }
    }

    /**
     * Loads weather data for the device's resolved location through the
     * configured provider.
     */
    fun loadWeatherData() {
        weatherRepository.getWeatherData()
            .onEach { state = it }
            .launchIn(viewModelScope)
    }

    /**
     * Loads weather data for the specified city through the configured
     * provider.
     */
    fun loadWeatherDataForCity(cityName: String) {
        viewModelScope.launch {
            state = WeatherState.Loading
            val result = weatherRepository.current(cityName)
            state = result.fold(
                onSuccess = { current ->
                    WeatherState.Success(
                        current = current,
                        location = Location(
                            latitude = 0.0,
                            longitude = 0.0,
                            city = current.city.ifBlank { cityName },
                            country = "",
                        ),
                    )
                },
                onFailure = { err ->
                    if (err is WeatherProviderUnconfigured) WeatherState.Unconfigured
                    else WeatherState.Error(err.message ?: "Unknown error")
                },
            )
            selectedCity = cityName
        }
        refreshForecast(cityName)
    }

    /**
     * Refreshes the multi-day forecast for the given city. Marks state
     * [ForecastState.Unconfigured] when the active provider requires a key the
     * host has not configured.
     */
    fun refreshForecast(cityName: String) {
        if (cityName.isBlank()) return
        viewModelScope.launch {
            _forecast.value = ForecastState.Loading
            val result = weatherRepository.forecast(cityName, days = 5)
            _forecast.value = result.fold(
                onSuccess = { ForecastState.Loaded(it) },
                onFailure = { err ->
                    if (err is WeatherProviderUnconfigured) ForecastState.Unconfigured
                    else ForecastState.Failed
                },
            )
        }
    }

    /**
     * Searches for cities matching the given query.
     *
     * Backed by WeatherAPI.com — only available when the user has configured
     * a valid WeatherAPI key. Surfaces an error state otherwise (the
     * upstream client throws `WeatherApiException` when the secret is missing).
     */
    fun searchCities(query: String) {
        if (query.isBlank()) {
            citySearchState = CitySearchState.Initial
            return
        }

        viewModelScope.launch {
            citySearchState = CitySearchState.Loading
            try {
                val cities = weatherApi.searchCity(query)
                citySearchState = if (cities.isEmpty()) {
                    CitySearchState.Empty
                } else {
                    CitySearchState.Success(cities)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                citySearchState = CitySearchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Selects a city and loads its weather data.
     * @param city The city to select.
     */
    fun selectCity(city: CitySearchResult) {
        loadWeatherDataForCity(city.name)
        citySearchState = CitySearchState.Initial

        // Store the selected city in preferences
        viewModelScope.launch {
            preferencesRepository.setCurrentCity(city.name)
        }
    }

    /**
     * Gets the weather icon URL for the current weather.
     * @return The URL of the weather icon, or null if the weather data is not available.
     */
    fun getWeatherIconUrl(): String? {
        return when (val currentState = state) {
            is WeatherState.Success -> currentState.current.iconUrl
            else -> null
        }
    }
}

/**
 * State of the multi-day forecast fetch.
 */
sealed interface ForecastState {
    object Loading : ForecastState
    data class Loaded(val days: List<DayForecast>) : ForecastState
    object Failed : ForecastState
    object Unconfigured : ForecastState
}

/**
 * Sealed class representing the state of city search.
 */
sealed class CitySearchState {
    /**
     * Initial state (no search performed yet).
     */
    object Initial : CitySearchState()

    /**
     * Loading state.
     */
    object Loading : CitySearchState()

    /**
     * Success state with search results.
     * @param cities The list of cities found.
     */
    data class Success(val cities: List<CitySearchResult>) : CitySearchState()

    /**
     * Empty state (no cities found).
     */
    object Empty : CitySearchState()

    /**
     * Error state.
     * @param message The error message.
     */
    data class Error(val message: String) : CitySearchState()
}
