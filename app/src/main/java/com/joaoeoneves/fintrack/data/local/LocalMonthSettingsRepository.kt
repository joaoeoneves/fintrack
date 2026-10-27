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

        override suspend fun setWorkingDaysOverride(
            month: YearMonth,
            days: Int?,
        ): Result<Unit> =
            store.update { doc ->
                val key = month.toString()
                val others = doc.monthSettings.filterNot { it.month == key }
                if (days == null) {
                    doc.copy(monthSettings = others) to Unit
                } else {
                    val record = localMonthSettingsOf(month, days)
                    validateMonthSettings(record)
                    doc.copy(monthSettings = doc.monthSettings.upserted(record) { it.month == key }) to Unit
                }
            }
    }
