package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SuggestionRepository] intended for tests; the dismissed keys are exposed as a set. */
class FakeSuggestionRepository(
    initialDismissed: Set<String> = emptySet(),
    var nextError: Throwable? = null,
) : SuggestionRepository {
    private val dismissed = MutableStateFlow(initialDismissed)

    /** The keys dismissed right now, for assertions. */
    val keys: Set<String> get() = dismissed.value

    override fun observeDismissedRecurring(): Flow<Set<String>> = dismissed

    override suspend fun dismissRecurring(key: String): Result<Unit> = change { it + key }

    override suspend fun undismissRecurring(key: String): Result<Unit> = change { it - key }

    private fun change(transform: (Set<String>) -> Set<String>): Result<Unit> {
        nextError?.let { error ->
            nextError = null
            return Result.failure(error)
        }
        dismissed.value = transform(dismissed.value)
        return Result.success(Unit)
    }
}
