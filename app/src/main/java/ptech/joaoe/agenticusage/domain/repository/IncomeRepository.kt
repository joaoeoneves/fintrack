package ptech.joaoe.agenticusage.domain.repository

import kotlinx.coroutines.flow.Flow
import ptech.joaoe.agenticusage.domain.model.Income
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
