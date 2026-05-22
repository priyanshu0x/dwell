package com.droidslife.screensaver.settings

import kotlinx.coroutines.flow.Flow

/**
 * Interface for the preferences repository.
 */
interface PreferencesRepository {
    /**
     * Gets the current settings.
     * @return A flow of settings.
     */
    fun getSettings(): Flow<SettingsModel>

    /**
     * Updates the settings.
     * @param settings The new settings.
     */
    suspend fun updateSettings(settings: SettingsModel)

    /**
     * Gets the current city for weather.
     * @return The current city, or null if not set.
     */
    suspend fun getCurrentCity(): String?

    /**
     * Sets the current city for weather.
     * @param city The city to set.
     */
    suspend fun setCurrentCity(city: String)
}

expect fun createPreferencesRepository(): PreferencesRepository
