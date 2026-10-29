package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.data.savedAs
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
                val order = existing?.order ?: ((doc.categoryGroups.maxOfOrNull { it.order } ?: -1) + 1)
                val template = Envelope(id, edit.name, order, edit.amountCents, edit.fundedByLineId)
                val saved = existing?.toDomain().savedAs(template, edit.amountCents, edit.effectiveFrom)
                val record =
                    LocalCategoryGroup(
                        id = id,
                        name = edit.name,
                        order = order,
                        amountCents = saved.amountCents,
                        fundedByLineId = edit.fundedByLineId,
                        startMonth = saved.startMonth?.toString(),
                        versions = saved.versions.toLocalVersions(),
                    )
                validateCategoryGroup(record)
                val known = knownCategoryIds(doc)
                edit.limits.forEach { (categoryId, budget) ->
                    budget?.let { validateBudget(categoryId.value, it.limitCents, known, it.skipMonths) }
                }
                val members = edit.categoryIds.mapTo(mutableSetOf()) { it.value }
                val limitRecords =
                    edit.limits.mapNotNull { (categoryId, budget) ->
                        budget?.let {
                            val old = doc.budgets.find { b -> b.categoryId == categoryId.value }?.toDomain()
                            val next = old.savedAs(categoryId, it.limitCents, it.skipMonths, edit.effectiveFrom)
                            LocalBudget(
                                categoryId.value,
                                next.limitCents,
                                it.skipMonths.sorted(),
                                next.startMonth?.toString(),
                                next.versions.toLocalVersions(),
                            )
                        }
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
                val withMembers = doc.withMembers(id, members)
                withMembers.copy(categoryGroups = groups, budgets = budgets) to id
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
                val filed = doc.catalog().filter { it.groupId == id }
                val members = filed.mapTo(mutableSetOf()) { it.id.value }
                doc
                    .withCategories(filed.map { it.copy(groupId = null).toLocal() })
                    .copy(
                        categoryGroups = doc.categoryGroups.filterNot { it.id == id },
                        budgets = doc.budgets.filterNot { it.categoryId in members },
                    ) to Unit
            }

        /** This document with exactly the [members] filed under envelope [id], upserting built-ins as overrides. */
        private fun LocalDataDocument.withMembers(
            id: String,
            members: Set<String>,
        ): LocalDataDocument {
            val refiled =
                catalog().mapNotNull { category ->
                    when {
                        category.id.value in members && category.groupId != id -> category.copy(groupId = id)
                        category.id.value !in members && category.groupId == id -> category.copy(groupId = null)
                        else -> null
                    }
                }
            return withCategories(refiled.map { it.toLocal() })
        }
    }
