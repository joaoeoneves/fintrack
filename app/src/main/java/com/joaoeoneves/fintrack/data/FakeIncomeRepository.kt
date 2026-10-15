package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * In-memory [IncomeRepository] implementation intended for tests. Not wired via Hilt; construct
 * and pass it directly where a fake repository is needed.
 */
class FakeIncomeRepository(
    initialIncome: List<Income> = emptyList(),
    var nextObserveIncomeError: Throwable? = null,
    var nextGetIncomeError: Throwable? = null,
) : IncomeRepository {
    private val incomeFlow = MutableStateFlow(initialIncome)

    override fun observeIncome(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Income>> {
        nextObserveIncomeError?.let { error -> return flow { throw error } }
        return incomeFlow.map { income ->
            income.filter { it.date >= startInclusive && it.date < endExclusive }
        }
    }

    override suspend fun addIncome(income: Income): Result<String> {
        val id = income.id.ifBlank { UUID.randomUUID().toString() }
        val toStore = income.copy(id = id)
        incomeFlow.value = incomeFlow.value + toStore
        return Result.success(id)
    }

    override suspend fun updateIncome(income: Income): Result<Unit> {
        val current = incomeFlow.value
        if (current.none { it.id == income.id }) {
            return Result.failure(NoSuchElementException("Income with id ${income.id} not found"))
        }
        incomeFlow.value = current.map { if (it.id == income.id) income else it }
        return Result.success(Unit)
    }

    override suspend fun deleteIncome(id: String): Result<Unit> {
        val current = incomeFlow.value
        if (current.none { it.id == id }) {
            return Result.failure(NoSuchElementException("Income with id $id not found"))
        }
        incomeFlow.value = current.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun getIncome(id: String): Result<Income?> {
        nextGetIncomeError?.let { error ->
            nextGetIncomeError = null
            return Result.failure(error)
        }
        return Result.success(incomeFlow.value.find { it.id == id })
    }

    override suspend fun getAllIncome(): Result<List<Income>> = Result.success(incomeFlow.value)

    override suspend fun addIncomeList(income: List<Income>): Result<Int> {
        val toStore = income.map { it.copy(id = UUID.randomUUID().toString()) }
        incomeFlow.value = incomeFlow.value + toStore
        return Result.success(income.size)
    }
}
