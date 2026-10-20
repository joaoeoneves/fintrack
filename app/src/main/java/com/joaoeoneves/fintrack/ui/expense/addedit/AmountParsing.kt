package com.joaoeoneves.fintrack.ui.expense.addedit

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Plain decimal amount: optional sign, digits with an optional '.' or ',' fraction, or a bare
 * fraction (".5" / ",5"). Rejects exponents ("1e3"), grouping separators and anything else
 * [BigDecimal] would otherwise accept.
 */
private val AMOUNT_PATTERN = Regex("""^[+-]?(\d+([.,]\d*)?|[.,]\d+)$""")

private const val CENTS_SCALE = 2

/**
 * Parses a user-entered amount string (e.g. "12.50" or "12,50") into positive integer cents,
 * using [BigDecimal] to avoid floating point rounding errors. Both '.' and ',' are accepted as the
 * decimal separator so the input matches whichever separator the selected currency displays.
 * Fails for blank/invalid input or non-positive amounts.
 */
fun parseAmountCents(amountText: String): Result<Long> {
    val trimmed = amountText.trim()
    return when {
        trimmed.isEmpty() -> Result.failure(NumberFormatException("Amount is required"))
        !AMOUNT_PATTERN.matches(trimmed) -> Result.failure(NumberFormatException("Invalid amount: '$trimmed'"))
        else -> parseValidatedAmountCents(trimmed.replace(',', '.'))
    }
}

/** Converts an [AMOUNT_PATTERN]-conforming, '.'-separated amount into positive cents. */
private fun parseValidatedAmountCents(normalized: String): Result<Long> =
    try {
        val cents = BigDecimal(normalized).movePointRight(CENTS_SCALE).setScale(0, RoundingMode.HALF_UP)
        if (cents.signum() <= 0) {
            Result.failure(ArithmeticException("Amount must be positive"))
        } else {
            Result.success(cents.toLong())
        }
    } catch (e: NumberFormatException) {
        Result.failure(e)
    } catch (e: ArithmeticException) {
        Result.failure(e)
    }

/**
 * Formats integer cents as an editable amount string with exactly two fraction digits (e.g.
 * 1250 -> "12.50"), using [decimalSeparator] so the prefilled text matches the selected currency's
 * display convention. The output always round-trips through [parseAmountCents].
 */
fun Long.toAmountInputText(decimalSeparator: Char = '.'): String =
    BigDecimal(this)
        .movePointLeft(CENTS_SCALE)
        .setScale(CENTS_SCALE)
        .toPlainString()
        .replace('.', decimalSeparator)
