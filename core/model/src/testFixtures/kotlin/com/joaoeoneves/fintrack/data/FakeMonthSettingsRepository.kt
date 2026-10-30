package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.requireValidDaysOff
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.time.YearMonth

/**
 * In-memory [MonthSettingsRepository] intended for tests; a record is removed when its days off are 0.
 */
class FakeMonthSettingsRepository(
    initialSettings: List<MonthSettings> = emptyList(),
    var nextObserveError: Throwable? = null,
    var nextSetError: Throwable? = null,
) : MonthSettingsRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)

    /** Current settings, for assertions. */
    val settings: List<MonthSettings> get() = settingsFlow.value

    override fun observeMonthSettings(): Flow<List<MonthSettings>> {
        nextObserveError?.let { error -> return flow { throw error } }
        return settingsFlow
    }

    override suspend fun setDaysOff(
        months: Collection<YearMonth>,
        vacation: Int,
        sick: Int,
        other: Int,
    ): Result<Unit> {
        requireValidDaysOff(months, vacation, sick, other)
        return change(months.toSet()) { MonthSettings(it, vacation, sick, other) }
    }

    private fun change(
        months: Set<YearMonth>,
        edit: (YearMonth) -> MonthSettings,
    ): Result<Unit> {
        nextSetError?.let { error ->
            nextSetError = null
            return Result.failure(error)
        }
        val updated = months.map(edit)
        val others = settingsFlow.value.filterNot { it.month in months }
        settingsFlow.value = others + updated.filterNot { it.isBlank }
        return Result.success(Unit)
    }
}
