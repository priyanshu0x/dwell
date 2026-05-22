package com.droidslife.screensaver.location

/**
 * Single source of truth for the app's fallback city string. Used only when
 * no user-configured city is available (no widget config, no timezone match).
 * Everything else should read the city from the Weather widget config or the
 * WeatherViewModel — do not hard-code city names anywhere else.
 */
const val FALLBACK_CITY: String = "Rewari"
