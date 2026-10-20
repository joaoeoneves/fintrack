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
 * rather than floating point to avoid rounding errors.
 */
fun formatAmount(
    amountCents: Long,
    currency: CurrencyOption,
    locale: Locale,
): String = currencyFormatter(locale, currency).format(BigDecimal.valueOf(amountCents, CENTS_SCALE))

/**
 * Composable counterpart of [formatAmount], using the [LocalCurrency] and the configuration's
 * locale (see [currentLocale]). Negative amounts are formatted natively by the locale.
 */
@Composable
fun formatAmountCents(amountCents: Long): String {
    val locale = currentLocale()
    val currency = LocalCurrency.current
    val formatter = remember(locale, currency) { currencyFormatter(locale, currency) }
    return formatter.format(BigDecimal.valueOf(amountCents, CENTS_SCALE))
}
