package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Guest [SuggestionRepository]; stub until backed by the local store. */
@Singleton
class LocalSuggestionRepository
    @Inject
    constructor() : SuggestionRepository {
        override fun observeDismissedRecurring(): Flow<Set<String>> = flowOf(emptySet())

        override suspend fun dismissRecurring(key: String): Result<Unit> = Result.success(Unit)

        override suspend fun undismissRecurring(key: String): Result<Unit> = Result.success(Unit)
    }
