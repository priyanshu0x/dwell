package com.droidslife.screensaver.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/**
 * Service for getting the current location.
 * v1 derives a practical default from the current system timezone instead of
 * requesting precise geolocation permissions.
 */
class LocationService {
    /**
     * Gets the current location.
     * @return The current location.
     */
    suspend fun getCurrentLocation(): Location = withContext(Dispatchers.Default) {
        // Get the current timezone to determine an appropriate default location
        val timezone = TimeZone.currentSystemDefault().id

        if (timezone == "Asia/Kolkata") {
            return@withContext Location(19.0760, 72.8777, "Mumbai", "IN")
        }

        return@withContext Location(40.7128, -74.0060, "New York", "US")
    }
}

/**
 * Data class representing a location.
 * @param latitude The latitude of the location.
 * @param longitude The longitude of the location.
 * @param city The city name.
 * @param country The country code.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val country: String
)
