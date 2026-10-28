package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val NBSP = ' '
private const val GROUP_SIZE = 3
private const val CENTS_DIGITS = 2
private const val MAX_DIGITS = 12

/** The decimal and grouping separators used when typing/showing money in a locale. */
data class MoneySymbols(
    val decimal: Char,
    val grouping: Char,
)

/** Separators for [locale]; Portuguese always uses "," and a space (never a dot). */
fun moneySymbolsFor(locale: Locale): MoneySymbols =
    if (locale.language == "pt") {
        MoneySymbols(decimal = ',', grouping = NBSP)
    } else {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        MoneySymbols(decimal = symbols.decimalSeparator, grouping = symbols.groupingSeparator)
    }

/**
 * Cash-register edit: keeps only the digits of [newRaw] as the new cents value. Keystrokes that would
 * exceed [max] (or 12 digits) are ignored by returning [current].
 */
fun applyCentsEdit(
    current: Long,
    newRaw: String,
    max: Long = MAX_AMOUNT_CENTS,
): Long {
    val digits = newRaw.filter { it in '0'..'9' }.trimStart('0')
    return when {
        digits.isEmpty() -> 0L
        digits.length > MAX_DIGITS -> current
        else -> digits.toLong().takeIf { it <= max } ?: current
    }
}

/** The raw digits of this cents value, "" for zero. */
fun Long.toCentsRaw(): String = if (this <= 0L) "" else toString()

/** [cents] with grouping and always two decimals, e.g. "1 234,56". */
fun formatCentsGrouped(
    cents: Long,
    symbols: MoneySymbols,
): String {
    val digits = cents.coerceAtLeast(0L).toString().padStart(CENTS_DIGITS + 1, '0')
    val whole = digits.dropLast(CENTS_DIGITS)
    val fraction = digits.takeLast(CENTS_DIGITS)
    val grouped =
        whole
            .reversed()
            .chunked(GROUP_SIZE)
            .joinToString(symbols.grouping.toString())
            .reversed()
    return grouped + symbols.decimal + fraction
}

/** Maps '.'/',' to [decimal], keeping digits and only the first separator. */
fun normalizeDecimalInput(
    text: String,
    decimal: Char,
): String {
    var seen = false
    return buildString {
        for (c in text) {
            when {
                c in '0'..'9' -> append(c)
                (c == '.' || c == ',') && !seen -> {
                    seen = true
                    append(decimal)
                }
            }
        }
    }
}
