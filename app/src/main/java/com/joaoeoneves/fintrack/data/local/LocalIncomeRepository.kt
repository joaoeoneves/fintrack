package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Income entries kept in the guest-mode [LocalDataStore], validated like firestore.rules does. */
@Singleton
class LocalIncomeRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : IncomeRepository {
        override fun observeIncome(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Income>> =
            store.data.map { doc ->
                doc.income
                    .mapNotNull { it.toDomainOrNull() }
                    .filter { it.date >= startInclusive && it.date < endExclusive }
                    .sortedBy { it.date }
            }

        override suspend fun addIncome(income: Income): Result<String> =
            store.update { doc ->
                val record = income.toLocal().let { it.copy(id = it.id.ifBlank { idGenerator.newId() }) }
                validateIncome(record)
                doc.copy(income = doc.income.upserted(record) { it.id == record.id }) to record.id
            }

        override suspend fun updateIncome(income: Income): Result<Unit> =
            store.update { doc ->
                val record = income.toLocal()
                require(doc.income.any { it.id == record.id }) { "Income ${record.id} does not exist" }
                validateIncome(record)
                doc.copy(income = doc.income.map { if (it.id == record.id) record else it }) to Unit
            }

        override suspend fun deleteIncome(id: String): Result<Unit> =
            store.update { doc -> doc.copy(income = doc.income.filterNot { it.id == id }) to Unit }

        override suspend fun getIncome(id: String): Result<Income?> =
            runCatchingCancellable {
                store.data
                    .first()
                    .income
                    .find { it.id == id }
                    ?.toDomainOrNull()
            }

        override suspend fun getAllIncome(): Result<List<Income>> =
            runCatchingCancellable {
                store.data
                    .first()
                    .income
                    .mapNotNull { it.toDomainOrNull() }
            }

        override suspend fun addIncomeList(income: List<Income>): BulkAddResult =
            store
                .update { doc ->
                    val records =
                        income.map { entry ->
                            entry.toLocal().copy(id = idGenerator.newId()).also { validateIncome(it) }
                        }
                    doc.copy(income = doc.income + records) to records.size
                }.fold(
                    onSuccess = { BulkAddResult(it, null) },
                    onFailure = { BulkAddResult(0, it) },
                )
    }
