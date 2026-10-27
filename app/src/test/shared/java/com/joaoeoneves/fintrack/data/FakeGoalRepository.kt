package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [GoalRepository] intended for tests. Goals are exposed sorted by order then id; a blank id on save
 * gets a generated one. Each `next*Error` fails exactly the next call of that kind (observe errors persist).
 */
class FakeGoalRepository(
    initialGoals: List<SavingsGoal> = emptyList(),
    var nextObserveError: Throwable? = null,
    var nextSaveError: Throwable? = null,
    var nextDeleteError: Throwable? = null,
) : GoalRepository {
    private val goalsFlow = MutableStateFlow(initialGoals)
    private var nextId = 1

    /** Current goals in insertion order, for assertions. */
    val goals: List<SavingsGoal> get() = goalsFlow.value

    /** Number of successful saves, for assertions. */
    var saveCount: Int = 0
        private set

    override fun observeGoals(): Flow<List<SavingsGoal>> {
        nextObserveError?.let { error -> return flow { throw error } }
        return goalsFlow.map { list -> list.sortedWith(compareBy({ it.order }, { it.id })) }
    }

    override suspend fun saveGoal(goal: SavingsGoal): Result<String> {
        nextSaveError?.let { error ->
            nextSaveError = null
            return Result.failure(error)
        }
        val id = goal.id.ifBlank { "goal${nextId++}" }
        val stored = goal.copy(id = id)
        val current = goalsFlow.value
        goalsFlow.value =
            if (current.any { it.id == id }) current.map { if (it.id == id) stored else it } else current + stored
        saveCount++
        return Result.success(id)
    }

    override suspend fun deleteGoal(id: String): Result<Unit> {
        nextDeleteError?.let { error ->
            nextDeleteError = null
            return Result.failure(error)
        }
        goalsFlow.value = goalsFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
