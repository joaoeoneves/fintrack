package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

interface IncomeRepository {
    fun observeIncome(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Income>>

    /** The income entries flagged [Income.needsReview], across all months. */
    fun observeNeedingReview(): Flow<List<Income>> = flowOf(emptyList())

    /** Clears [Income.needsReview] on the entries with these [ids] (missing ids are ignored). */
    suspend fun markReviewed(ids: Collection<String>): Result<Unit> = Result.success(Unit)

    suspend fun addIncome(income: Income): Result<String>

    suspend fun updateIncome(income: Income): Result<Unit>

    suspend fun deleteIncome(id: String): Result<Unit>

    suspend fun getIncome(id: String): Result<Income?>

    suspend fun getAllIncome(): Result<List<Income>>

    suspend fun addIncomeList(income: List<Income>): BulkAddResult
}
