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
 *
 * Deliberately NOT localized via `stringResource()`: `CurrencyDisplayTest` asserts on this
 * property directly from plain (non-Composable) JUnit test methods, so it must stay a synchronous,
 * Context-free `String` getter. Currency *names* (unlike every other UI string in the app) are
 * therefore only in English regardless of the in-app language -- a deliberate, flagged compromise,
 * not an oversight. See the language-feature report for the full rationale.
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
