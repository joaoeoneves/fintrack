package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The dismissed suggestions kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalSuggestionRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
    ) : SuggestionRepository {
        override fun observeDismissedRecurring(): Flow<Set<String>> =
            store.data.map { it.dismissedRecurringSuggestions.toSet() }.distinctUntilChanged()

        override suspend fun dismissRecurring(key: String): Result<Unit> =
            store.update { doc ->
                doc.copy(dismissedRecurringSuggestions = (doc.dismissedRecurringSuggestions + key).distinct()) to Unit
            }

        override suspend fun undismissRecurring(key: String): Result<Unit> =
            store.update { doc ->
                doc.copy(dismissedRecurringSuggestions = doc.dismissedRecurringSuggestions - key) to Unit
            }
    }
