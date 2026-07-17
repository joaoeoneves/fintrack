package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The currently selected display currency, provided by [com.joaoeoneves.fintrack.MainActivity]
 * (via `CurrencyViewModel`) above the signed-in nav graph, same pattern as
 * [com.joaoeoneves.fintrack.ui.theme.LocalDarkTheme]. Defaults to [CurrencyOption.EUR].
 */
val LocalCurrency = staticCompositionLocalOf { CurrencyOption.EUR }

/**
 * Formats an amount stored as integer minor units (cents) into a display string like "$12.50",
 * using the currency symbol from [LocalCurrency]. Uses [BigDecimal] rather than floating point to
 * avoid rounding errors. Uniform 2-decimal formatting is used for every [CurrencyOption], including
 * [CurrencyOption.JPY] — a deliberate scope decision, not a bug.
 */
@Composable
fun formatAmountCents(amountCents: Long): String {
    val amount = BigDecimal(amountCents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
    return "${LocalCurrency.current.symbol}$amount"
}
