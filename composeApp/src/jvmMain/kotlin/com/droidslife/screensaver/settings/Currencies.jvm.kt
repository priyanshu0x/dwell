package com.droidslife.screensaver.settings

import java.util.Currency

actual fun availableCurrencies(): List<CurrencyEntry> {
    return Currency.getAvailableCurrencies()
        .map { CurrencyEntry(code = it.currencyCode, displayName = it.displayName) }
        .sortedBy { it.code }
}
