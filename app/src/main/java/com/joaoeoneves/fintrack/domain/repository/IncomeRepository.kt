package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface IncomeRepository {
    fun observeIncome(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Income>>

    suspend fun addIncome(income: Income): Result<String>

    suspend fun updateIncome(income: Income): Result<Unit>

    suspend fun deleteIncome(id: String): Result<Unit>

    suspend fun getIncome(id: String): Income?

    suspend fun getAllIncome(): Result<List<Income>>

    suspend fun addIncomeList(income: List<Income>): Result<Int>
}
