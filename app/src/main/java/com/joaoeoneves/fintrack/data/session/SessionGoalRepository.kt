package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreGoalRepository
import com.joaoeoneves.fintrack.data.local.LocalGoalRepository
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [GoalRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionGoalRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        @ApplicationScope scope: CoroutineScope,
        remote: Lazy<FirestoreGoalRepository>,
        local: LocalGoalRepository,
    ) : GoalRepository {
        private val switch = SessionSwitch<GoalRepository>(sessionRepository, remote, local)

        private val observeGoalsShared = switch.shared(scope, emptyList()) { it.observeGoals() }

        override fun observeGoals(): Flow<List<SavingsGoal>> = observeGoalsShared

        override suspend fun saveGoal(goal: SavingsGoal): Result<String> = switch.call { it.saveGoal(goal) }

        override suspend fun deleteGoal(id: String): Result<Unit> = switch.call { it.deleteGoal(id) }
    }
