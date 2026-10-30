package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

// Amounts are stored as integer cents, i.e. with two implied decimal places.
private const val CENTS_SCALE = 2

/**
 * A euro [NumberFormat] following [locale]'s conventions (symbol placement, decimal and grouping
 * separators, negative sign), with exactly two fraction digits, rounding half-up. Not thread-safe;
 * create one per caller.
 */
fun currencyFormatter(locale: Locale): NumberFormat {
    val euro = Currency.getInstance("EUR")
    return NumberFormat.getCurrencyInstance(locale).apply {
        currency = euro
        minimumFractionDigits = euro.defaultFractionDigits
        maximumFractionDigits = euro.defaultFractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
}

/**
 * Formats [amountCents] (integer minor units, always 2 implied decimals) as euros in [locale], e.g.
 * 123456 is "1 234,56 €" in pt-PT and "€1,234.56" in en-US. Uses [BigDecimal] rather than floating
 * point to avoid rounding errors. Plain ASCII sign: for CSV, parsing and accessibility text; on-screen
 * text goes through [formatAmountCents].
 */
fun formatAmount(
    amountCents: Long,
    locale: Locale,
): String = currencyFormatter(locale).formatCents(amountCents, locale)

/**
 * Composable counterpart of [formatAmount], using the configuration's
 * locale (see [currentLocale]). Negative amounts use the locale's layout with a typographic minus ([MINUS_SIGN])
 * unless [typographic] is false (use that for content descriptions, which keep the ASCII hyphen).
 */
@Composable
fun formatAmountCents(
    amountCents: Long,
    typographic: Boolean = true,
): String {
    val locale = currentLocale()
    val formatter = remember(locale) { currencyFormatter(locale) }
    val text = formatter.formatCents(amountCents, locale)
    return if (typographic) text.withTypographicMinus() else text
}

private val FOUR_DIGITS = Regex("""(?<![\d.,])(\d)(\d{3})(?![\d])""")

/**
 * Formats [amountCents] with this formatter. Portuguese CLDR data does not group four-digit integers ("1234,56")
 * while larger ones are grouped ("12 345,67"), and Android's ICU and the JVM disagree on it; this groups from four
 * digits explicitly so every platform prints "1 234,56 €".
 */
private fun NumberFormat.formatCents(
    amountCents: Long,
    locale: Locale,
): String {
    val text = format(BigDecimal.valueOf(amountCents, CENTS_SCALE))
    val match = if (locale.language == "pt" && isGroupingUsed) FOUR_DIGITS.find(text) else null
    val separator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
    return if (match == null) {
        text
    } else {
        text.replaceRange(match.range, "${match.groupValues[1]}$separator${match.groupValues[2]}")
    }
}

/** Typographic minus sign (U+2212), wider and better aligned with tabular figures than the ASCII hyphen. */
const val MINUS_SIGN = "\u2212"

/**
 * Swaps the ASCII hyphen-minus a locale puts on a negative amount for [MINUS_SIGN] (display only).
 * Only the first hyphen is replaced: a formatted amount carries at most one sign.
 */
fun String.withTypographicMinus(): String = replaceFirst("-", MINUS_SIGN)
