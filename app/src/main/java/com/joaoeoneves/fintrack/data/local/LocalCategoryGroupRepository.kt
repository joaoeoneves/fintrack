package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Category groups kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalCategoryGroupRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : CategoryGroupRepository {
        override fun observeGroups(): Flow<List<CategoryGroup>> =
            store.data.map { doc ->
                doc.categoryGroups.map { it.toDomain() }.sortedWith(compareBy({ it.order }, { it.id }))
            }

        override suspend fun addGroup(name: String): Result<String> =
            store.update { doc ->
                val record =
                    LocalCategoryGroup(
                        id = idGenerator.newId(),
                        name = name,
                        order = (doc.categoryGroups.maxOfOrNull { it.order } ?: -1) + 1,
                    )
                validateCategoryGroup(record)
                doc.copy(categoryGroups = doc.categoryGroups + record) to record.id
            }

        override suspend fun renameGroup(
            id: String,
            name: String,
        ): Result<Unit> =
            store.update { doc ->
                val current = requireNotNull(doc.categoryGroups.find { it.id == id }) { "Unknown group $id" }
                val renamed = current.copy(name = name)
                validateCategoryGroup(renamed)
                doc.copy(categoryGroups = doc.categoryGroups.upserted(renamed) { it.id == id }) to Unit
            }

        override suspend fun reorderGroups(orderedIds: List<String>): Result<Unit> =
            store.update { doc ->
                val byId = doc.categoryGroups.associateBy { it.id }
                val reordered =
                    orderedIds.mapIndexed { index, id ->
                        requireNotNull(byId[id]) { "Unknown group $id" }.copy(order = index)
                    }
                val untouched = doc.categoryGroups.filter { it.id !in orderedIds }
                doc.copy(categoryGroups = reordered + untouched) to Unit
            }

        override suspend fun deleteGroup(id: String): Result<Unit> =
            store.update { doc ->
                doc.copy(
                    categoryGroups = doc.categoryGroups.filterNot { it.id == id },
                    categories = doc.categories.map { if (it.groupId == id) it.copy(groupId = null) else it },
                ) to Unit
            }
    }
