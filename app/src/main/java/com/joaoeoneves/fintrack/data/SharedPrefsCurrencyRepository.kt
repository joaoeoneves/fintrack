package com.joaoeoneves.fintrack.data

import android.content.Context
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "currency_prefs"
private const val KEY_CURRENCY = "currency"

/**
 * [CurrencyRepository] backed by [android.content.SharedPreferences], storing the [CurrencyOption]
 * enum name as a string. Absent/unrecognized values default to [CurrencyOption.USD].
 *
 * Scoped as a singleton because it caches state in [currencyFlow]; a second instance would seed
 * its own copy from disk and never observe writes made through the first instance.
 */
@Singleton
class SharedPrefsCurrencyRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : CurrencyRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private val currencyFlow = MutableStateFlow(readStoredCurrency())

        override fun observeCurrency(): Flow<CurrencyOption> = currencyFlow

        override suspend fun setCurrency(currency: CurrencyOption) {
            prefs.edit().putString(KEY_CURRENCY, currency.name).apply()
            currencyFlow.value = currency
        }

        private fun readStoredCurrency(): CurrencyOption {
            val stored = prefs.getString(KEY_CURRENCY, null) ?: return CurrencyOption.USD
            return CurrencyOption.entries.find { it.name == stored } ?: CurrencyOption.USD
        }
    }
