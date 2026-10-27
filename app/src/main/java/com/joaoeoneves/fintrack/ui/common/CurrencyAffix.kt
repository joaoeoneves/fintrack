package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/** The currency symbol of the active currency/locale and whether it is written before the number. */
@Immutable
data class CurrencyAffix(
    val symbol: String,
    val leads: Boolean,
)

/** Computes the [CurrencyAffix] once per currency/locale change instead of on every recomposition. */
@Composable
fun rememberCurrencyAffix(): CurrencyAffix {
    val currency = LocalCurrency.current
    val locale = currentLocale()
    return remember(currency, locale) { CurrencyAffix(currency.symbol(locale), currency.symbolLeads(locale)) }
}
