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

    /**
     * Gets the auto play status.
     * @return Whether auto play is enabled.
     */
    suspend fun getAutoPlayStatus(): Boolean

    /**
     * Sets the auto play status.
     * @param enabled Whether auto play should be enabled.
     */
    suspend fun setAutoPlayStatus(enabled: Boolean)

    /**
     * Gets the shuffle status.
     * @return Whether shuffle is enabled.
     */
    suspend fun getShuffleStatus(): Boolean

    /**
     * Sets the shuffle status.
     * @param enabled Whether shuffle should be enabled.
     */
    suspend fun setShuffleStatus(enabled: Boolean)

    /**
     * Gets the currently selected design.
     * @return The design ID.
     */
    suspend fun getSelectedDesign(): Int

    /**
     * Sets the currently selected design.
     * @param designId The design ID to set.
     */
    suspend fun setSelectedDesign(designId: Int)
}

expect fun createPreferencesRepository(): PreferencesRepository
