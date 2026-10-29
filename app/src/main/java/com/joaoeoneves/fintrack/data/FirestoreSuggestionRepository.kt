package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Signed-in [SuggestionRepository]; stub until the Firestore settings document is wired in. */
class FirestoreSuggestionRepository
    @Inject
    constructor() : SuggestionRepository {
        override fun observeDismissedRecurring(): Flow<Set<String>> = flowOf(emptySet())

        override suspend fun dismissRecurring(key: String): Result<Unit> = Result.success(Unit)

        override suspend fun undismissRecurring(key: String): Result<Unit> = Result.success(Unit)
    }
