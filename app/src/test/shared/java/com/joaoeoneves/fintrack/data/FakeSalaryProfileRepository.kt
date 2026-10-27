package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** In-memory [SalaryProfileRepository] intended for tests. */
class FakeSalaryProfileRepository(
    initialProfile: SalaryProfile? = null,
    var nextObserveError: Throwable? = null,
    var nextSaveError: Throwable? = null,
    var nextDeleteError: Throwable? = null,
) : SalaryProfileRepository {
    private val profileFlow = MutableStateFlow(initialProfile)

    /** Current profile, for assertions. */
    val profile: SalaryProfile? get() = profileFlow.value

    override fun observeSalaryProfile(): Flow<SalaryProfile?> {
        nextObserveError?.let { error -> return flow { throw error } }
        return profileFlow
    }

    override suspend fun saveSalaryProfile(profile: SalaryProfile): Result<Unit> {
        nextSaveError?.let { error ->
            nextSaveError = null
            return Result.failure(error)
        }
        profileFlow.value = profile
        return Result.success(Unit)
    }

    override suspend fun deleteSalaryProfile(): Result<Unit> {
        nextDeleteError?.let { error ->
            nextDeleteError = null
            return Result.failure(error)
        }
        profileFlow.value = null
        return Result.success(Unit)
    }
}
