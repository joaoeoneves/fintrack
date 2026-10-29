package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [PlanSettingsRepository]; `null` (the default) means the user never chose the salary shift. */
class FakePlanSettingsRepository(
    initial: Boolean? = null,
) : PlanSettingsRepository {
    private val salaryPaysNextMonth = MutableStateFlow(initial)

    override fun observeSalaryPaysNextMonth(): Flow<Boolean?> = salaryPaysNextMonth

    override suspend fun setSalaryPaysNextMonth(enabled: Boolean) {
        salaryPaysNextMonth.value = enabled
    }
}
