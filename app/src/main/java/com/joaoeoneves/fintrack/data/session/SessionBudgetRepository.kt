package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreBudgetRepository
import com.joaoeoneves.fintrack.data.local.LocalBudgetRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [BudgetRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionBudgetRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreBudgetRepository>,
        local: LocalBudgetRepository,
    ) : BudgetRepository {
        private val switch = SessionSwitch<BudgetRepository>(sessionRepository, remote, local)

        override fun observeBudgets(): Flow<List<Budget>> = switch.flow(emptyList()) { it.observeBudgets() }

        override suspend fun setBudget(
            categoryId: CategoryId,
            limitCents: Long,
        ): Result<Unit> = switch.call { it.setBudget(categoryId, limitCents) }

        override suspend fun clearBudget(categoryId: CategoryId): Result<Unit> =
            switch.call {
                it.clearBudget(categoryId)
            }

        override suspend fun moveBudget(
            source: CategoryId,
            target: CategoryId,
        ): Result<Boolean> = switch.call { it.moveBudget(source, target) }
    }
