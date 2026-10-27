package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Expected-income lines of the monthly plan kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalPlannedIncomeRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : PlannedIncomeRepository {
        override fun observePlannedIncome(): Flow<List<PlannedIncome>> =
            store.data.map { doc ->
                doc.plannedIncome.map { it.toDomain() }.sortedWith(compareBy({ it.order }, { it.id }))
            }

        override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> =
            store.update { doc ->
                val record = line.toLocal().let { it.copy(id = it.id.ifBlank { idGenerator.newId() }) }
                validatePlannedIncome(record)
                doc.copy(plannedIncome = doc.plannedIncome.upserted(record) { it.id == record.id }) to record.id
            }

        override suspend fun deletePlannedIncome(id: String): Result<Unit> =
            store.update { doc -> doc.copy(plannedIncome = doc.plannedIncome.filterNot { it.id == id }) to Unit }
    }
