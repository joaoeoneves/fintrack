package com.joaoeoneves.fintrack.domain.repository

import kotlinx.coroutines.flow.Flow

/** User-dismissed suggestions, kept so a dismissed suggestion does not come back. */
interface SuggestionRepository {
    /** The normalized names of the recurring-expense suggestions the user dismissed. */
    fun observeDismissedRecurring(): Flow<Set<String>>

    suspend fun dismissRecurring(key: String): Result<Unit>

    suspend fun undismissRecurring(key: String): Result<Unit>
}
