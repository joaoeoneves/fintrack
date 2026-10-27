package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Minimal in-memory [CurrencyRepository] fake shared by ViewModel tests (no fake for this
 * repository exists under `app/src/main`).
 */
class FakeCurrencyRepository(
    initial: CurrencyOption = CurrencyOption.USD,
) : CurrencyRepository {
    private val currencyFlow = MutableStateFlow(initial)

    override fun observeCurrency(): Flow<CurrencyOption> = currencyFlow

    override suspend fun setCurrency(currency: CurrencyOption) {
        currencyFlow.value = currency
    }

    fun currentCurrency(): CurrencyOption = currencyFlow.value
}
