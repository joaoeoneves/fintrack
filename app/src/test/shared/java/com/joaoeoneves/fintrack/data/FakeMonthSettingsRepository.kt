package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.time.YearMonth

/** In-memory [MonthSettingsRepository] intended for tests; a `null` override removes the month's entry. */
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

    override suspend fun setWorkingDaysOverride(
        month: YearMonth,
        days: Int?,
    ): Result<Unit> {
        nextSetError?.let { error ->
            nextSetError = null
            return Result.failure(error)
        }
        val others = settingsFlow.value.filterNot { it.month == month }
        settingsFlow.value = if (days == null) others else others + MonthSettings(month, days)
        return Result.success(Unit)
    }
}
