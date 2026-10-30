package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Per-month plan settings kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalMonthSettingsRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
    ) : MonthSettingsRepository {
        override fun observeMonthSettings(): Flow<List<MonthSettings>> =
            store.data.map { doc -> doc.monthSettings.mapNotNull { it.toDomainOrNull() } }

        override suspend fun setDaysOff(
            month: YearMonth,
            days: Int,
        ): Result<Unit> = change(month) { it.copy(daysOff = days) }

        /** Applies [edit] to [month]'s record (a blank one if absent); a record left with nothing set is removed. */
        private suspend fun change(
            month: YearMonth,
            edit: (LocalMonthSettings) -> LocalMonthSettings,
        ): Result<Unit> =
            store.update { doc ->
                val key = month.toString()
                val current = doc.monthSettings.firstOrNull { it.month == key } ?: LocalMonthSettings(month = key)
                val record = edit(current)
                if (record.daysOff == 0) {
                    doc.copy(monthSettings = doc.monthSettings.filterNot { it.month == key }) to Unit
                } else {
                    validateMonthSettings(record)
                    doc.copy(monthSettings = doc.monthSettings.upserted(record) { it.month == key }) to Unit
                }
            }
    }
