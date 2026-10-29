package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreRecurringRepository
import com.joaoeoneves.fintrack.data.local.LocalRecurringRepository
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** [RecurringRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionRecurringRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        @ApplicationScope scope: CoroutineScope,
        remote: Lazy<FirestoreRecurringRepository>,
        local: LocalRecurringRepository,
    ) : RecurringRepository {
        private val switch = SessionSwitch<RecurringRepository>(sessionRepository, remote, local)

        private val observeRecurringShared = switch.shared(scope, emptyList()) { it.observeRecurring() }

        override fun observeRecurring(): Flow<List<RecurringItem>> = observeRecurringShared

        override suspend fun saveRecurring(item: RecurringItem): Result<String> = switch.call { it.saveRecurring(item) }

        override suspend fun deleteRecurring(id: String): Result<Unit> = switch.call { it.deleteRecurring(id) }

        override suspend fun skipOccurrences(
            itemId: String,
            dates: Set<LocalDate>,
            advanceTo: LocalDate?,
        ): Result<Unit> = switch.call { it.skipOccurrences(itemId, dates, advanceTo) }

        override suspend fun unskipOccurrences(
            itemId: String,
            dates: Set<LocalDate>,
        ): Result<Unit> = switch.call { it.unskipOccurrences(itemId, dates) }

        override suspend fun moveRecurringCategory(
            source: CategoryId,
            target: CategoryId,
        ): Result<Int> = switch.call { it.moveRecurringCategory(source, target) }

        override suspend fun setHandledThrough(
            id: String,
            date: LocalDate?,
        ): Result<Unit> = switch.call { it.setHandledThrough(id, date) }

        override suspend fun confirmOccurrence(
            itemId: String,
            occurrence: LocalDate,
            transaction: Transaction,
        ): Result<String> = switch.call { it.confirmOccurrence(itemId, occurrence, transaction) }
    }
