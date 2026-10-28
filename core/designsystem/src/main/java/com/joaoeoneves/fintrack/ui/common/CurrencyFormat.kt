package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

// Amounts are stored as integer cents, i.e. with two implied decimal places.
private const val CENTS_SCALE = 2

/**
 * The currently selected display currency, provided by [com.joaoeoneves.fintrack.MainActivity]
 * (via `CurrencyViewModel`) above the signed-in nav graph, same pattern as
 * [com.joaoeoneves.fintrack.ui.theme.LocalDarkTheme]. Defaults to [CurrencyOption.EUR].
 */
val LocalCurrency = staticCompositionLocalOf { CurrencyOption.EUR }

/**
 * A currency [NumberFormat] for [currency] following [locale]'s conventions (symbol placement,
 * decimal and grouping separators, negative sign), with exactly the currency's default number of
 * fraction digits (e.g. 2 for EUR, 0 for JPY), rounding half-up. Not thread-safe; create one per
 * caller.
 */
fun currencyFormatter(
    locale: Locale,
    currency: CurrencyOption,
): NumberFormat {
    val javaCurrency = Currency.getInstance(currency.name)
    return NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = javaCurrency
        minimumFractionDigits = javaCurrency.defaultFractionDigits
        maximumFractionDigits = javaCurrency.defaultFractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
}

/**
 * Formats [amountCents] (integer minor units, always 2 implied decimals) as [currency] in
 * [locale], e.g. 123456 EUR is "1.234,56 €" in pt-PT and "€1,234.56" in en-US. Uses [BigDecimal]
 * rather than floating point to avoid rounding errors. Plain ASCII sign: for CSV, parsing and
 * accessibility text; on-screen text goes through [formatAmountCents].
 */
fun formatAmount(
    amountCents: Long,
    currency: CurrencyOption,
    locale: Locale,
): String = currencyFormatter(locale, currency).format(BigDecimal.valueOf(amountCents, CENTS_SCALE))

/**
 * Composable counterpart of [formatAmount], using the [LocalCurrency] and the configuration's
 * locale (see [currentLocale]). Negative amounts use the locale's layout with a typographic minus ([MINUS_SIGN])
 * unless [typographic] is false (use that for content descriptions, which keep the ASCII hyphen).
 */
@Composable
fun formatAmountCents(
    amountCents: Long,
    typographic: Boolean = true,
): String {
    val locale = currentLocale()
    val currency = LocalCurrency.current
    val formatter = remember(locale, currency) { currencyFormatter(locale, currency) }
    val text = formatter.format(BigDecimal.valueOf(amountCents, CENTS_SCALE))
    return if (typographic) text.withTypographicMinus() else text
}

/** Typographic minus sign (U+2212), wider and better aligned with tabular figures than the ASCII hyphen. */
const val MINUS_SIGN = "\u2212"

/**
 * Swaps the ASCII hyphen-minus a locale puts on a negative amount for [MINUS_SIGN] (display only).
 * Only the first hyphen is replaced: a formatted amount carries at most one sign.
 */
fun String.withTypographicMinus(): String = replaceFirst("-", MINUS_SIGN)
