package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [PlannedIncomeRepository] intended for tests. Lines are exposed sorted by order then id,
 * like the real implementations; a blank id on save gets a generated one.
 */
class FakePlannedIncomeRepository(
    initialLines: List<PlannedIncome> = emptyList(),
    var nextObserveError: Throwable? = null,
    var nextSaveError: Throwable? = null,
    var nextDeleteError: Throwable? = null,
) : PlannedIncomeRepository {
    private val linesFlow = MutableStateFlow(initialLines)
    private var nextId = 1

    /** Current lines in insertion order (unsorted), for assertions. */
    val lines: List<PlannedIncome> get() = linesFlow.value

    override fun observePlannedIncome(): Flow<List<PlannedIncome>> {
        nextObserveError?.let { error -> return flow { throw error } }
        return linesFlow.map { list -> list.sortedWith(compareBy({ it.order }, { it.id })) }
    }

    override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> {
        nextSaveError?.let { error ->
            nextSaveError = null
            return Result.failure(error)
        }
        val saved = if (line.id.isBlank()) line.copy(id = "line${nextId++}") else line
        val current = linesFlow.value
        linesFlow.value =
            if (current.any { it.id == saved.id }) {
                current.map { if (it.id == saved.id) saved else it }
            } else {
                current + saved
            }
        return Result.success(saved.id)
    }

    override suspend fun deletePlannedIncome(id: String): Result<Unit> {
        nextDeleteError?.let { error ->
            nextDeleteError = null
            return Result.failure(error)
        }
        linesFlow.value = linesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
