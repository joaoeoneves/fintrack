package com.joaoeoneves.fintrack.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CurrencyOption

/**
 * Symbol printed alongside formatted amounts for a [CurrencyOption], shared across the currency
 * picker.
 */
val CurrencyOption.symbol: String
    get() =
        when (this) {
            CurrencyOption.USD -> "$"
            CurrencyOption.EUR -> "€"
            CurrencyOption.GBP -> "£"
            CurrencyOption.JPY -> "¥"
            CurrencyOption.CAD -> "CA$"
            CurrencyOption.AUD -> "A$"
        }

/**
 * String resource of the human-readable name of a [CurrencyOption]. Plain (non-composable) so it
 * can be unit-tested and resolved outside composition.
 */
@get:StringRes
val CurrencyOption.displayNameRes: Int
    get() =
        when (this) {
            CurrencyOption.USD -> R.string.currency_usd
            CurrencyOption.EUR -> R.string.currency_eur
            CurrencyOption.GBP -> R.string.currency_gbp
            CurrencyOption.JPY -> R.string.currency_jpy
            CurrencyOption.CAD -> R.string.currency_cad
            CurrencyOption.AUD -> R.string.currency_aud
        }

/**
 * Localized human-readable label for a [CurrencyOption], used in the Settings currency picker.
 * `@Composable` because it resolves [displayNameRes] through [stringResource]; every call site is
 * inside a Composable already.
 */
val CurrencyOption.displayName: String
    @Composable
    get() = stringResource(displayNameRes)
