package com.droidslife.screensaver.settings

/**
 * A currency entry surfaced in the Settings UI.
 *
 * @property code ISO 4217 currency code (e.g. `USD`).
 * @property displayName Human-readable name (e.g. `US Dollar`). Empty when the
 *   platform cannot supply one.
 */
data class CurrencyEntry(val code: String, val displayName: String)

/**
 * Returns the list of ISO 4217 currencies available on the current platform.
 *
 * The list is sorted by code. On JVM this returns
 * `java.util.Currency.getAvailableCurrencies()`.
 */
expect fun availableCurrencies(): List<CurrencyEntry>
