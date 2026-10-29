package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreSuggestionRepository
import com.joaoeoneves.fintrack.data.local.LocalSuggestionRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [SuggestionRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionSuggestionRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreSuggestionRepository>,
        local: LocalSuggestionRepository,
    ) : SuggestionRepository {
        private val switch = SessionSwitch<SuggestionRepository>(sessionRepository, remote, local)

        override fun observeDismissedRecurring(): Flow<Set<String>> =
            switch.flow(emptySet(), SuggestionRepository::observeDismissedRecurring)

        override suspend fun dismissRecurring(key: String): Result<Unit> = switch.call { it.dismissRecurring(key) }

        override suspend fun undismissRecurring(key: String): Result<Unit> = switch.call { it.undismissRecurring(key) }
    }
