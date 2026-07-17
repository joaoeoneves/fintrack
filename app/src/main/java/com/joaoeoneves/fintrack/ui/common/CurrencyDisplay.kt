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

/**
 * Where the currency symbol is placed relative to the formatted amount.
 */
enum class SymbolPosition { PREFIX, SUFFIX }

/**
 * Decimal separator character used when formatting a [CurrencyOption]'s amounts.
 */
val CurrencyOption.decimalSeparator: Char
    get() =
        when (this) {
            CurrencyOption.EUR -> ','
            else -> '.'
        }

/**
 * Where the [symbol] is placed relative to the formatted amount for a [CurrencyOption].
 */
val CurrencyOption.symbolPosition: SymbolPosition
    get() =
        when (this) {
            CurrencyOption.EUR -> SymbolPosition.SUFFIX
            else -> SymbolPosition.PREFIX
        }
