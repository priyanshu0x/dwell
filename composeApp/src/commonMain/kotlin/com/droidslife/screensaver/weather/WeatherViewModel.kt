package com.droidslife.screensaver.weather

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droidslife.screensaver.location.FALLBACK_CITY
import com.droidslife.screensaver.location.Location
import com.droidslife.screensaver.location.TimeZoneUtils
import com.droidslife.screensaver.settings.PreferencesRepository
import com.droidslife.screensaver.weather.providers.CurrentWeather
import com.droidslife.screensaver.weather.providers.WeatherProviderUnconfigured
import kotlin.time.Clock
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
import kotlinx.datetime.LocalDate

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
    private val preferencesRepository: PreferencesRepository,
    private val cacheStore: WeatherCacheStore,
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
     * 5-day forecast state. Refreshed alongside the current-weather fetch in
     * [loadWeatherDataForCity].
     */
    val forecast: StateFlow<ForecastState> = _forecast.asStateFlow()

    private val _syncStatus = MutableStateFlow<WeatherSyncStatus>(WeatherSyncStatus.Healthy)

    /**
     * Live health of the most recent refresh. Decoupled from [state] because a
     * background refresh can fail while we still want to render the cached
     * [WeatherState.Success] underneath — the state machine alone can't express
     * "good data, but it's stale". The widget collects this and shows a calm
     * status line instead of the failure landing silently.
     */
    val syncStatus: StateFlow<WeatherSyncStatus> = _syncStatus.asStateFlow()

    // Stale-while-revalidate cache. Keyed by (provider, city) so swapping the
    // source in Settings doesn't surface stale data from the previous provider.
    // Single-threaded access (all VM coroutines run on Dispatchers.Main), so no
    // explicit synchronization is required.
    private data class CacheKey(val providerId: String, val city: String)
    private data class CurrentEntry(val data: CurrentWeather, val location: Location, val fetchedAtMs: Long)
    private data class ForecastEntry(val days: List<DayForecast>, val fetchedAtMs: Long)

    private val currentCache = mutableMapOf<CacheKey, CurrentEntry>()
    private val forecastCache = mutableMapOf<CacheKey, ForecastEntry>()
    private val refreshingCurrent = mutableSetOf<CacheKey>()
    private val refreshingForecast = mutableSetOf<CacheKey>()

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    init {
        // Hydrate from disk synchronously so the first widget render hits the
        // in-memory cache instead of a network round-trip. Done in `init` (not
        // inside the coroutine below) so the cache is ready before any caller
        // can invoke `loadWeatherDataForCity`.
        hydrateCacheFromDisk()
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
     * Stale-while-revalidate: cached payloads are surfaced immediately (no
     * Loading flicker); a background refresh fires only when the cached entry
     * is older than [CACHE_TTL_MS]. Background-refresh failures are swallowed
     * so we keep showing the stale data rather than flashing an error.
     */
    fun loadWeatherDataForCity(cityName: String, forceRefresh: Boolean = false) {
        val providerId = weatherRepository.activeProviderId()
        loadCurrent(cityName, providerId, forceRefresh)
        loadForecast(cityName, providerId, forceRefresh)
        selectedCity = cityName
    }

    private fun loadCurrent(cityName: String, providerId: String, forceRefresh: Boolean) {
        if (cityName.isBlank()) return
        val key = CacheKey(providerId, cityName)
        val cached = currentCache[key]
        if (cached != null && !forceRefresh) {
            state = WeatherState.Success(cached.data, cached.location)
            if (nowMs() - cached.fetchedAtMs > CACHE_TTL_MS) {
                refreshCurrentInBackground(cityName, key)
            }
            return
        }
        viewModelScope.launch {
            if (cached == null) state = WeatherState.Loading
            fetchAndStoreCurrent(cityName, key)
        }
    }

    private fun refreshCurrentInBackground(cityName: String, key: CacheKey) {
        if (!refreshingCurrent.add(key)) return
        viewModelScope.launch {
            try {
                fetchAndStoreCurrent(cityName, key)
            } finally {
                refreshingCurrent -= key
            }
        }
    }

    private suspend fun fetchAndStoreCurrent(cityName: String, key: CacheKey) {
        val result = weatherRepository.current(cityName)
        result.fold(
            onSuccess = { current ->
                val location = Location(
                    latitude = 0.0,
                    longitude = 0.0,
                    city = current.city.ifBlank { cityName },
                    country = "",
                )
                currentCache[key] = CurrentEntry(current, location, nowMs())
                state = WeatherState.Success(current, location)
                _syncStatus.value = WeatherSyncStatus.Healthy
                persistCache()
            },
            onFailure = { err ->
                when {
                    // Unconfigured is its own actionable state regardless of cache.
                    err is WeatherProviderUnconfigured -> {
                        if (currentCache[key] == null) state = WeatherState.Unconfigured
                        _syncStatus.value = WeatherSyncStatus.Unconfigured
                    }
                    // Refresh failed but we have a last-good reading: keep it
                    // visible and flag the staleness instead of failing silently.
                    currentCache[key] != null ->
                        _syncStatus.value = WeatherSyncStatus.Offline
                    // No data to fall back on — surface a hard error.
                    else -> {
                        state = WeatherState.Error(err.message ?: "Unknown error")
                        _syncStatus.value = WeatherSyncStatus.Failed
                    }
                }
            },
        )
    }

    private fun loadForecast(cityName: String, providerId: String, forceRefresh: Boolean) {
        if (cityName.isBlank()) return
        val key = CacheKey(providerId, cityName)
        val cached = forecastCache[key]
        if (cached != null && !forceRefresh) {
            _forecast.value = ForecastState.Loaded(cached.days)
            if (nowMs() - cached.fetchedAtMs > CACHE_TTL_MS) {
                refreshForecastInBackground(cityName, key)
            }
            return
        }
        viewModelScope.launch {
            _forecast.value = ForecastState.Loading
            fetchAndStoreForecast(cityName, key)
        }
    }

    private fun refreshForecastInBackground(cityName: String, key: CacheKey) {
        if (!refreshingForecast.add(key)) return
        viewModelScope.launch {
            try {
                fetchAndStoreForecast(cityName, key)
            } finally {
                refreshingForecast -= key
            }
        }
    }

    private suspend fun fetchAndStoreForecast(cityName: String, key: CacheKey) {
        val result = weatherRepository.forecast(cityName, days = 5)
        result.fold(
            onSuccess = { days ->
                forecastCache[key] = ForecastEntry(days, nowMs())
                _forecast.value = ForecastState.Loaded(days)
                persistCache()
            },
            onFailure = { err ->
                if (forecastCache[key] != null) return@fold
                _forecast.value = if (err is WeatherProviderUnconfigured) ForecastState.Unconfigured
                else ForecastState.Failed
            },
        )
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

    private fun hydrateCacheFromDisk() {
        val snapshot = cacheStore.loadSync()
        snapshot.entries.forEach { entry ->
            val key = CacheKey(entry.providerId, entry.city)
            if (entry.current != null && entry.currentFetchedAtMs != null) {
                val current = CurrentWeather(
                    tempC = entry.current.tempC,
                    feelsLikeC = entry.current.feelsLikeC,
                    humidity = entry.current.humidity,
                    conditionCode = entry.current.conditionCode,
                    conditionText = entry.current.conditionText,
                    city = entry.current.city,
                    iconUrl = entry.current.iconUrl,
                )
                val location = Location(
                    latitude = 0.0,
                    longitude = 0.0,
                    city = entry.current.city.ifBlank { entry.city },
                    country = "",
                )
                currentCache[key] = CurrentEntry(current, location, entry.currentFetchedAtMs)
            }
            if (entry.forecast != null && entry.forecastFetchedAtMs != null) {
                val days = entry.forecast.mapNotNull { day ->
                    runCatching {
                        DayForecast(
                            date = LocalDate.parse(day.dateIso),
                            high = day.high,
                            low = day.low,
                            conditionCode = day.conditionCode,
                            conditionText = day.conditionText,
                            iconUrl = day.iconUrl,
                        )
                    }.getOrNull()
                }
                if (days.isNotEmpty()) {
                    forecastCache[key] = ForecastEntry(days, entry.forecastFetchedAtMs)
                }
            }
        }
    }

    /**
     * Snapshot both in-memory caches into one file. Called after every
     * successful network fetch — the cost is one small JSON write per refresh,
     * which is well below the cache TTL granularity we care about.
     */
    private fun persistCache() {
        val keys = currentCache.keys + forecastCache.keys
        val entries = keys.map { key ->
            val cur = currentCache[key]
            val fc = forecastCache[key]
            WeatherCacheEntry(
                providerId = key.providerId,
                city = key.city,
                current = cur?.let {
                    WeatherCacheCurrent(
                        tempC = it.data.tempC,
                        feelsLikeC = it.data.feelsLikeC,
                        humidity = it.data.humidity,
                        conditionCode = it.data.conditionCode,
                        conditionText = it.data.conditionText,
                        city = it.data.city,
                        iconUrl = it.data.iconUrl,
                    )
                },
                currentFetchedAtMs = cur?.fetchedAtMs,
                forecast = fc?.days?.map { day ->
                    WeatherCacheDay(
                        dateIso = day.date.toString(),
                        high = day.high,
                        low = day.low,
                        conditionCode = day.conditionCode,
                        conditionText = day.conditionText,
                        iconUrl = day.iconUrl,
                    )
                },
                forecastFetchedAtMs = fc?.fetchedAtMs,
            )
        }
        viewModelScope.launch {
            cacheStore.save(WeatherCacheSnapshot(entries))
        }
    }

    private companion object {
        // 10 minutes — long enough that revisiting the dashboard is a cache hit,
        // short enough that "feels like" / conditions don't drift past noticeable.
        const val CACHE_TTL_MS: Long = 10 * 60 * 1000
    }
}

/**
 * Health of the most recent weather refresh, surfaced as one calm status line.
 * Orthogonal to [WeatherState]: it reports *how the last fetch went*, so the
 * widget can flag stale/offline data while still rendering the cached reading.
 */
enum class WeatherSyncStatus {
    /** The latest fetch succeeded (or there's nothing degraded to report). */
    Healthy,

    /** Refresh failed but a cached reading is still on screen. */
    Offline,

    /** The active provider needs an API key the host hasn't configured. */
    Unconfigured,

    /** Fetch failed with no prior data to fall back on. */
    Failed,
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
