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

        override suspend fun updateLineCursors(
            set: Map<String, YearMonth>,
            remove: Set<String>,
            clearLegacyPayday: Boolean,
        ): Result<Unit> =
            store.update { doc ->
                val current = doc.automation ?: LocalAutomation()
                val cursors = current.lineThrough - remove + set.mapValues { it.value.toString() }
                val updated = current.copy(lineThrough = cursors)
                doc.copy(
                    automation = if (clearLegacyPayday) updated.copy(paydayEnabled = false) else updated,
                ) to Unit
            }

        override suspend fun setSalaryPaysNextMonth(enabled: Boolean): Result<Unit> =
            store.update { doc ->
                doc.copy(automation = (doc.automation ?: LocalAutomation()).copy(salaryPaysNextMonth = enabled)) to Unit
            }

        override suspend fun recordPayday(
            incomes: List<Income>,
            cursors: Map<String, YearMonth>,
        ): Result<Unit> =
            store.update { doc ->
                val records = incomes.map { it.toLocal().also(::validateIncome) }
                val ids = records.mapTo(mutableSetOf()) { it.id }
                val current = doc.automation ?: LocalAutomation()
                doc.copy(
                    income = doc.income.filterNot { it.id in ids } + records,
                    automation =
                        current.copy(lineThrough = current.lineThrough + cursors.mapValues { it.value.toString() }),
                ) to Unit
            }
    }
