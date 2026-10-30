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
 * (replacing any with the same id, like the real atomic write) and moves the cursors, so tests can assert both.
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

    /** Every [recordPayday] call as (cursors, incomes), in order. */
    val recorded = mutableListOf<Pair<Map<String, YearMonth>, List<Income>>>()

    /** Every [updateLineCursors] call as (set, remove), in order. */
    val cursorUpdates = mutableListOf<Pair<Map<String, YearMonth>, Set<String>>>()

    override fun observeAutomationSettings(): Flow<AutomationSettings> {
        nextObserveError?.let { error -> return flow { throw error } }
        return settingsFlow
    }

    override suspend fun updateLineCursors(
        set: Map<String, YearMonth>,
        remove: Set<String>,
    ): Result<Unit> {
        failIfRequested()?.let { return Result.failure(it) }
        cursorUpdates += set to remove
        val current = settingsFlow.value
        settingsFlow.value =
            current.copy(
                lineRecordedThrough = current.lineRecordedThrough - remove + set,
            )
        return Result.success(Unit)
    }

    override suspend fun recordPayday(
        incomes: List<Income>,
        cursors: Map<String, YearMonth>,
    ): Result<Unit> {
        failIfRequested()?.let { return Result.failure(it) }
        recorded += cursors to incomes
        incomes.forEach { incomeRepo.deleteIncome(it.id) }
        incomes.forEach { incomeRepo.addIncome(it) }
        val current = settingsFlow.value
        settingsFlow.value = current.copy(lineRecordedThrough = current.lineRecordedThrough + cursors)
        return Result.success(Unit)
    }

    private fun failIfRequested(): Throwable? = nextWriteError.also { nextWriteError = null }
}
