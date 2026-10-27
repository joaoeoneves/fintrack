package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.coroutines.flow.Flow

interface SalaryProfileRepository {
    /** The salary profile, or `null` while none is set; re-emits on every change. */
    fun observeSalaryProfile(): Flow<SalaryProfile?>

    /** Creates or replaces the single profile. */
    suspend fun saveSalaryProfile(profile: SalaryProfile): Result<Unit>

    /** Deletes the profile; a missing one is a no-op success. */
    suspend fun deleteSalaryProfile(): Result<Unit>
}
