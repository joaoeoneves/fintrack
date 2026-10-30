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
                val order = existing?.order ?: ((doc.categoryGroups.maxOfOrNull { it.order } ?: -1) + 1)
                val record = LocalCategoryGroup(id = id, name = edit.name, order = order)
                validateCategoryGroup(record)
                val members = edit.categoryIds.mapTo(mutableSetOf()) { it.value }
                val groups = doc.categoryGroups.upserted(record) { it.id == id }
                doc.withMembers(id, members).copy(categoryGroups = groups) to id
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
                doc
                    .withCategories(filed.map { it.copy(groupId = null).toLocal() })
                    .copy(
                        categoryGroups = doc.categoryGroups.filterNot { it.id == id },
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
