package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreIncomeRepository
import com.joaoeoneves.fintrack.data.local.LocalIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** [IncomeRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionIncomeRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreIncomeRepository>,
        local: LocalIncomeRepository,
    ) : IncomeRepository {
        private val switch = SessionSwitch<IncomeRepository>(sessionRepository, remote, local)

        override fun observeIncome(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Income>> = switch.flow(emptyList()) { it.observeIncome(startInclusive, endExclusive) }

        override fun observeNeedingReview(): Flow<List<Income>> = switch.flow(emptyList()) { it.observeNeedingReview() }

        override suspend fun markReviewed(ids: Collection<String>): Result<Unit> = switch.call { it.markReviewed(ids) }

        override suspend fun addIncome(income: Income): Result<String> = switch.call { it.addIncome(income) }

        override suspend fun updateIncome(income: Income): Result<Unit> = switch.call { it.updateIncome(income) }

        override suspend fun deleteIncome(id: String): Result<Unit> = switch.call { it.deleteIncome(id) }

        override suspend fun getIncome(id: String): Result<Income?> = switch.call { it.getIncome(id) }

        override suspend fun getAllIncome(): Result<List<Income>> = switch.call { it.getAllIncome() }

        override suspend fun addIncomeList(income: List<Income>): BulkAddResult =
            switch.call {
                it.addIncomeList(income)
            }
    }
