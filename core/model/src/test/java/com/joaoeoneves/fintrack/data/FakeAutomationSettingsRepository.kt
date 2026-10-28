package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.time.YearMonth

/**
 * In-memory [AutomationSettingsRepository] intended for tests. [recordPayday] stores the incomes in [incomeRepo]
 * (replacing any with the same id, like the real atomic write) and moves the cursor, so tests can assert both.
 */
class FakeAutomationSettingsRepository(
    initial: AutomationSettings = AutomationSettings(),
    val incomeRepo: FakeIncomeRepository = FakeIncomeRepository(),
    var nextObserveError: Throwable? = null,
    var nextWriteError: Throwable? = null,
) : AutomationSettingsRepository {
    private val settingsFlow = MutableStateFlow(initial)

    /** Current settings, for assertions. */
    val settings: AutomationSettings get() = settingsFlow.value

    /** Replaces the stored settings outright, for arranging a test. */
    fun replaceSettings(settings: AutomationSettings) {
        settingsFlow.value = settings
    }

    /** Every [recordPayday] call as (month, incomes), in order. */
    val recorded = mutableListOf<Pair<YearMonth, List<Income>>>()

    override fun observeAutomationSettings(): Flow<AutomationSettings> {
        nextObserveError?.let { error -> return flow { throw error } }
        return settingsFlow
    }

    override suspend fun setPaydayEnabled(
        enabled: Boolean,
        cursor: YearMonth?,
    ): Result<Unit> {
        failIfRequested()?.let { return Result.failure(it) }
        settingsFlow.value =
            settingsFlow.value.copy(
                paydayEnabled = enabled,
                paydayRecordedThrough = cursor ?: settingsFlow.value.paydayRecordedThrough,
            )
        return Result.success(Unit)
    }

    override suspend fun recordPayday(
        month: YearMonth,
        incomes: List<Income>,
    ): Result<Unit> {
        failIfRequested()?.let { return Result.failure(it) }
        recorded += month to incomes
        incomes.forEach { incomeRepo.deleteIncome(it.id) }
        incomes.forEach { incomeRepo.addIncome(it) }
        settingsFlow.value = settingsFlow.value.copy(paydayRecordedThrough = month)
        return Result.success(Unit)
    }

    private fun failIfRequested(): Throwable? = nextWriteError.also { nextWriteError = null }
}
