package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.CurrencyOption

/**
 * Symbol printed alongside formatted amounts for a [CurrencyOption], shared across the currency
 * picker and [formatAmountCents].
 */
val CurrencyOption.symbol: String
    get() =
        when (this) {
            CurrencyOption.USD -> "$"
            CurrencyOption.EUR -> "€"
            CurrencyOption.GBP -> "£"
            CurrencyOption.JPY -> "¥"
            CurrencyOption.CAD -> "CA$"
            CurrencyOption.AUD -> "A$"
        }

/**
 * Human-readable label for a [CurrencyOption], used in the Settings currency picker.
 */
val CurrencyOption.displayName: String
    get() =
        when (this) {
            CurrencyOption.USD -> "US Dollar"
            CurrencyOption.EUR -> "Euro"
            CurrencyOption.GBP -> "British Pound"
            CurrencyOption.JPY -> "Japanese Yen"
            CurrencyOption.CAD -> "Canadian Dollar"
            CurrencyOption.AUD -> "Australian Dollar"
        }
