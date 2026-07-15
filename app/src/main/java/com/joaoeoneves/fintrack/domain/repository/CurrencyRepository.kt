package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    fun observeCurrency(): Flow<CurrencyOption>

    suspend fun setCurrency(currency: CurrencyOption)
}
