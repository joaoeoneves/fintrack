package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The automation settings kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalAutomationSettingsRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
    ) : AutomationSettingsRepository {
        override fun observeAutomationSettings(): Flow<AutomationSettings> =
            store.data.map { it.automation?.toDomain() ?: AutomationSettings() }

        override suspend fun setPaydayEnabled(
            enabled: Boolean,
            cursor: YearMonth?,
        ): Result<Unit> =
            store.update { doc ->
                val current = doc.automation ?: LocalAutomation()
                doc.copy(
                    automation =
                        current.copy(
                            paydayEnabled = enabled,
                            paydayThrough = cursor?.toString() ?: current.paydayThrough,
                        ),
                ) to Unit
            }

        override suspend fun recordPayday(
            month: YearMonth,
            incomes: List<Income>,
        ): Result<Unit> =
            store.update { doc ->
                val records = incomes.map { it.toLocal().also(::validateIncome) }
                val ids = records.mapTo(mutableSetOf()) { it.id }
                doc.copy(
                    income = doc.income.filterNot { it.id in ids } + records,
                    automation = (doc.automation ?: LocalAutomation()).copy(paydayThrough = month.toString()),
                ) to Unit
            }
    }
