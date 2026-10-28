package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.repository.LegacyExtraNamesProvider
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The salary profile kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalSalaryProfileRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val legacyNames: LegacyExtraNamesProvider,
    ) : SalaryProfileRepository {
        override fun observeSalaryProfile(): Flow<SalaryProfile?> = store.data.map { it.salaryProfile?.domain() }

        private fun LocalSalaryProfile.domain() = toDomain(legacyNames.names())

        override suspend fun saveSalaryProfile(profile: SalaryProfile): Result<Unit> =
            store.update { doc ->
                val record = profile.toLocal()
                validateSalaryProfile(record)
                doc.copy(salaryProfile = record) to Unit
            }

        override suspend fun deleteSalaryProfile() = store.update { doc -> doc.copy(salaryProfile = null) to Unit }
    }
