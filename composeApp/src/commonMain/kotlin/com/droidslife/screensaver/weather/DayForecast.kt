package com.droidslife.screensaver.weather

import kotlinx.datetime.LocalDate

/**
 * Domain-level forecast entry for a single day. Lighter than the raw
 * [ForecastDay] response shape; used by the ViewModel + widgets.
 */
data class DayForecast(
    val date: LocalDate,
    val high: Int,        // celsius rounded
    val low: Int,
    val conditionCode: Int,
    val conditionText: String,
    val iconUrl: String,
)
