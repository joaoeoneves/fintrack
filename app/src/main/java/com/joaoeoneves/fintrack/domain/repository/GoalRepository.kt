package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    /** The goals (archived ones included), sorted by order then id. */
    fun observeGoals(): Flow<List<SavingsGoal>>

    /** Creates or replaces a goal (a blank id creates one); returns the goal's id. */
    suspend fun saveGoal(goal: SavingsGoal): Result<String>

    /** Deletes a goal; a missing one is a no-op success. */
    suspend fun deleteGoal(id: String): Result<Unit>
}
