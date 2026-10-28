package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Envelopes kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalEnvelopeRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : EnvelopeRepository {
        override fun observeEnvelopes(): Flow<List<Envelope>> =
            store.data.map { doc ->
                doc.categoryGroups.map { it.toDomain() }.sortedWith(compareBy({ it.order }, { it.id }))
            }

        override suspend fun saveEnvelope(edit: EnvelopeEdit): Result<String> =
            store.update { doc ->
                val existing = doc.categoryGroups.find { it.id == edit.id }
                val id = edit.id ?: idGenerator.newId()
                val record =
                    LocalCategoryGroup(
                        id = id,
                        name = edit.name,
                        order = existing?.order ?: ((doc.categoryGroups.maxOfOrNull { it.order } ?: -1) + 1),
                        amountCents = edit.amountCents,
                        fundedByLineId = edit.fundedByLineId,
                    )
                validateCategoryGroup(record)
                val known = knownCategoryIds(doc)
                edit.limits.forEach { (categoryId, budget) ->
                    budget?.let { validateBudget(categoryId.value, it.limitCents, known, it.skipMonths) }
                }
                val members = edit.categoryIds.mapTo(mutableSetOf()) { it.value }
                val limitRecords =
                    edit.limits.mapNotNull { (categoryId, budget) ->
                        budget?.let { LocalBudget(categoryId.value, it.limitCents, it.skipMonths.sorted()) }
                    }
                val touched = edit.limits.keys.mapTo(mutableSetOf()) { it.value }
                val budgets =
                    (doc.budgets.filterNot { it.categoryId in touched } + limitRecords)
                        .filter { edit.amountCents == null || it.categoryId !in members }
                val groups =
                    doc.categoryGroups
                        .map { group ->
                            val sameLine = edit.fundedByLineId != null && group.fundedByLineId == edit.fundedByLineId
                            if (group.id != id && sameLine) group.copy(fundedByLineId = null) else group
                        }.upserted(record) { it.id == id }
                val categories =
                    doc.categories.map { category ->
                        when {
                            category.id in members -> category.copy(groupId = id)
                            category.groupId == id -> category.copy(groupId = null)
                            else -> category
                        }
                    }
                doc.copy(categoryGroups = groups, categories = categories, budgets = budgets) to id
            }

        override suspend fun reorderEnvelopes(orderedIds: List<String>): Result<Unit> =
            store.update { doc ->
                val byId = doc.categoryGroups.associateBy { it.id }
                val reordered =
                    orderedIds.mapIndexed { index, id ->
                        requireNotNull(byId[id]) { "Unknown envelope $id" }.copy(order = index)
                    }
                val untouched = doc.categoryGroups.filter { it.id !in orderedIds }
                doc.copy(categoryGroups = reordered + untouched) to Unit
            }

        override suspend fun deleteEnvelope(id: String): Result<Unit> =
            store.update { doc ->
                val members = doc.categories.filter { it.groupId == id }.mapTo(mutableSetOf()) { it.id }
                doc.copy(
                    categoryGroups = doc.categoryGroups.filterNot { it.id == id },
                    categories = doc.categories.map { if (it.groupId == id) it.copy(groupId = null) else it },
                    budgets = doc.budgets.filterNot { it.categoryId in members },
                ) to Unit
            }
    }
