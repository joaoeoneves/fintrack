package com.joaoeoneves.fintrack.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Plan preferences the numbers depend on. */
interface PlanSettingsRepository {
    /**
     * Whether the salary pays for the next month: `null` while the user never chose (the plan then follows whether
     * a salary profile exists, see `salaryShiftEnabled`).
     */
    fun observeSalaryPaysNextMonth(): Flow<Boolean?>

    suspend fun setSalaryPaysNextMonth(enabled: Boolean)
}

/** A settings source with nothing chosen and no shift; the default where none is injected (tests). */
object NoPlanSettings : PlanSettingsRepository {
    override fun observeSalaryPaysNextMonth(): Flow<Boolean?> = flowOf(false)

    override suspend fun setSalaryPaysNextMonth(enabled: Boolean) = Unit
}
