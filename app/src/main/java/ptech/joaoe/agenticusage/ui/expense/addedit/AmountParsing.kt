package ptech.joaoe.agenticusage.ui.expense.addedit

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses a user-entered amount string (e.g. "12.50") into positive integer cents, using
 * [BigDecimal] to avoid floating point rounding errors. Fails for blank/invalid input or
 * non-positive amounts.
 */
fun parseAmountCents(amountText: String): Result<Long> {
    val trimmed = amountText.trim()
    if (trimmed.isEmpty()) {
        return Result.failure(NumberFormatException("Amount is required"))
    }
    return try {
        val cents = BigDecimal(trimmed).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
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
}
