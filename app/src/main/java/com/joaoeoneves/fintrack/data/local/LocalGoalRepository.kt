package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Savings goals kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalGoalRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : GoalRepository {
        override fun observeGoals(): Flow<List<SavingsGoal>> =
            store.data.map { doc ->
                doc.goals.mapNotNull { it.toDomainOrNull() }.sortedWith(compareBy({ it.order }, { it.id }))
            }

        override suspend fun saveGoal(goal: SavingsGoal): Result<String> =
            store.update { doc ->
                val record = goal.toLocal().let { it.copy(id = it.id.ifBlank { idGenerator.newId() }) }
                validateGoal(record)
                doc.copy(goals = doc.goals.upserted(record) { it.id == record.id }) to record.id
            }

        override suspend fun deleteGoal(id: String): Result<Unit> =
            store.update { doc -> doc.copy(goals = doc.goals.filterNot { it.id == id }) to Unit }
    }
