package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestorePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [PlannedIncomeRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionPlannedIncomeRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestorePlannedIncomeRepository>,
        local: LocalPlannedIncomeRepository,
    ) : PlannedIncomeRepository {
        private val switch = SessionSwitch<PlannedIncomeRepository>(sessionRepository, remote, local)

        override fun observePlannedIncome(): Flow<List<PlannedIncome>> =
            switch.flow(emptyList()) {
                it.observePlannedIncome()
            }

        override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> =
            switch.call {
                it.savePlannedIncome(line)
            }

        override suspend fun deletePlannedIncome(id: String): Result<Unit> = switch.call { it.deletePlannedIncome(id) }
    }
