package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import java.util.Currency
import java.util.Locale

/** The euro symbol as written in the active locale and whether it is written before the number. */
@Immutable
data class CurrencyAffix(
    val symbol: String,
    val leads: Boolean,
)

/** Computes the [CurrencyAffix] once per locale change instead of on every recomposition. */
@Composable
fun rememberCurrencyAffix(): CurrencyAffix {
    val locale = currentLocale()
    return remember(locale) { euroAffix(locale) }
}

private fun euroAffix(locale: Locale): CurrencyAffix {
    val symbol = Currency.getInstance("EUR").getSymbol(locale)
    return CurrencyAffix(symbol, formatAmount(1L, locale).startsWith(symbol))
}
