package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.repository.DataResetRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Guest-mode wipe: replaces the local data set with an empty one at the current schema version. */
@Singleton
class LocalDataResetRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
    ) : DataResetRepository {
        override suspend fun deleteAllData(): Result<Unit> = store.replaceAll(LocalDataDocument())
    }
