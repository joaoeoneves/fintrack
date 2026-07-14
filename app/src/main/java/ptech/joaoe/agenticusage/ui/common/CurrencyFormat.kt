package ptech.joaoe.agenticusage.ui.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats an amount stored as integer minor units (cents) into a display string like "$12.50".
 * Uses [BigDecimal] rather than floating point to avoid rounding errors.
 */
fun formatAmountCents(amountCents: Long): String {
    val amount = BigDecimal(amountCents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
    return "$$amount"
}
