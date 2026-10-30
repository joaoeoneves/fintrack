package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.requireValidDaysOff
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
            months: Collection<YearMonth>,
            vacation: Int,
            other: Int,
        ): Result<Unit> {
            requireValidDaysOff(months, vacation, other)
            val keys = months.map { it.toString() }.toSet()
            return store.update { doc ->
                val kept = doc.monthSettings.filterNot { it.month in keys }
                val set =
                    if (vacation + other == 0) {
                        emptyList()
                    } else {
                        keys
                            .map { LocalMonthSettings(it, vacation, otherDays = other) }
                            .onEach { validateMonthSettings(it) }
                    }
                doc.copy(monthSettings = kept + set) to Unit
            }
        }
    }
