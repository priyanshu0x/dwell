package com.droidslife.screensaver.di

import com.droidslife.screensaver.location.LocationService
import com.droidslife.screensaver.network.KtorClient
import com.droidslife.screensaver.network.BackendClient
import com.droidslife.screensaver.network.BackendGateway
import com.droidslife.screensaver.settings.PreferencesRepository
import com.droidslife.screensaver.settings.SecretStorage
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.settings.StartupRegistration
import com.droidslife.screensaver.settings.createPreferencesRepository
import com.droidslife.screensaver.settings.createSecretStorage
import com.droidslife.screensaver.settings.createStartupRegistration
import com.droidslife.screensaver.widget.builtin.CalendarWidgetFactory
import com.droidslife.screensaver.widget.builtin.ExpensesWidgetFactory
import com.droidslife.screensaver.widget.builtin.ClockWidgetFactory
import com.droidslife.screensaver.widget.builtin.IdleCounterWidgetFactory
import com.droidslife.screensaver.widget.builtin.PomodoroWidgetFactory
import com.droidslife.screensaver.widget.builtin.TodosWidgetFactory
import com.droidslife.screensaver.widget.builtin.WeatherWidgetFactory
import com.droidslife.screensaver.widget.host.WidgetRegistry
import com.droidslife.screensaver.weather.WeatherApi
import com.droidslife.screensaver.weather.WeatherRepository
import com.droidslife.screensaver.weather.WeatherViewModel
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Koin module for the application.
 */
val appModule = module {
    // HTTP Client
    single<HttpClient> { KtorClient.createWithRetry() }

    // Location Service
    single { LocationService() }

    // Preferences Repository
    single<PreferencesRepository> { createPreferencesRepository() }
    single<SecretStorage> { createSecretStorage() }
    single<StartupRegistration> { createStartupRegistration() }

    // Weather API
    single {
        val settingsViewModel: SettingsViewModel = get()
        WeatherApi(
            client = get(),
            secretStorage = get(),
            weatherApiKeySecretIdProvider = { settingsViewModel.settings.weatherApiKeySecretId },
        )
    }

    // Settings ViewModel (declared before WeatherRepository so the repo can
    // depend on it for resolving the active provider + WeatherAPI key.)
    single { SettingsViewModel(get(), get(), get()) }

    // Weather Repository — provider-agnostic façade. Picks an adapter per call
    // based on the user's saved widget configuration.
    single<WeatherRepository> {
        WeatherRepository(
            httpClient = get(),
            locationService = get(),
            settingsViewModel = get(),
            secretStorage = get(),
        )
    }

    // Weather ViewModel
    single { WeatherViewModel(get(), get(), get()) }

    // Backend sync
    single<BackendGateway> {
        val settingsViewModel: SettingsViewModel = get()
        val secretStorage: SecretStorage = get()
        BackendClient(
            httpClient = get(),
            settingsProvider = { settingsViewModel.settings },
            tokenProvider = { secretStorage.read(settingsViewModel.settings.backendApiKeySecretId) },
        )
    }

    // Built-in widgets
    single { ClockWidgetFactory(get()) }
    single { WeatherWidgetFactory(get(), get()) }
    single { TodosWidgetFactory(get()) }
    single { ExpensesWidgetFactory(get()) }
    single { CalendarWidgetFactory() }
    single { IdleCounterWidgetFactory() }
    single { PomodoroWidgetFactory() }

    // Widget registry
    single {
        WidgetRegistry(
            listOf(
                get<ClockWidgetFactory>(),
                get<WeatherWidgetFactory>(),
                get<TodosWidgetFactory>(),
                get<ExpensesWidgetFactory>(),
                get<CalendarWidgetFactory>(),
                get<IdleCounterWidgetFactory>(),
                get<PomodoroWidgetFactory>(),
            ),
            get(),
            get(),
        )
    }
}
