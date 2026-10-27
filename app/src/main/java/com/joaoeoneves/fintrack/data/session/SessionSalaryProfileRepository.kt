package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreSalaryProfileRepository
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [SalaryProfileRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionSalaryProfileRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreSalaryProfileRepository>,
        local: LocalSalaryProfileRepository,
    ) : SalaryProfileRepository {
        private val switch = SessionSwitch<SalaryProfileRepository>(sessionRepository, remote, local)

        override fun observeSalaryProfile(): Flow<SalaryProfile?> =
            switch.flow(null) {
                it.observeSalaryProfile()
            }

        override suspend fun saveSalaryProfile(profile: SalaryProfile): Result<Unit> =
            switch.call {
                it.saveSalaryProfile(profile)
            }

        override suspend fun deleteSalaryProfile(): Result<Unit> = switch.call { it.deleteSalaryProfile() }
    }
