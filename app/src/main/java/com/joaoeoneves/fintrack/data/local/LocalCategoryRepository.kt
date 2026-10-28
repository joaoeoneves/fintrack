package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in overrides and user categories kept in the guest-mode [LocalDataStore]. */
@Singleton
class LocalCategoryRepository
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val idGenerator: IdGenerator,
    ) : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> = store.data.map { it.catalog() }

        override suspend fun addCategory(
            name: String,
            iconKey: String,
            colorKey: String,
            kind: CategoryKind,
            groupId: String?,
        ): Result<CategoryId> =
            store.update { doc ->
                val nextOrder =
                    doc
                        .catalog()
                        .map { it.order }
                        .filter { it != Int.MAX_VALUE }
                        .maxOrNull()
                        ?.plus(1) ?: 0
                val record =
                    LocalCategory(
                        id = idGenerator.newId(),
                        name = name,
                        iconKey = iconKey,
                        colorKey = colorKey,
                        kind = kind.name,
                        order = nextOrder,
                        groupId = groupId,
                    )
                validateCategory(record)
                doc.copy(categories = doc.categories + record) to CategoryId(record.id)
            }

        override suspend fun updateCategory(category: Category): Result<Unit> =
            store.update { doc -> doc.withCategories(listOf(category.toLocal())) to Unit }

        override suspend fun setArchived(
            id: CategoryId,
            archived: Boolean,
        ): Result<Unit> =
            store.update { doc ->
                val current = requireNotNull(doc.catalog().find { it.id == id }) { "Unknown category ${id.value}" }
                doc.withCategories(listOf(current.copy(archived = archived).toLocal())) to Unit
            }

        override suspend fun setGroup(
            id: CategoryId,
            groupId: String?,
        ): Result<Unit> =
            store.update { doc ->
                val current = requireNotNull(doc.catalog().find { it.id == id }) { "Unknown category ${id.value}" }
                doc.withCategories(listOf(current.copy(groupId = groupId).toLocal())) to Unit
            }

        override suspend fun reorder(orderedIds: List<CategoryId>): Result<Unit> =
            store.update { doc ->
                val catalog = doc.catalog().associateBy { it.id }
                val reordered =
                    orderedIds.mapIndexed { index, id ->
                        requireNotNull(catalog[id]) { "Unknown category ${id.value}" }.copy(order = index).toLocal()
                    }
                doc.withCategories(reordered) to Unit
            }

        override suspend fun deleteCategory(id: CategoryId): Result<Unit> =
            store.update { doc ->
                require(!BuiltInCategories.isBuiltIn(id)) { "Built-in category ${id.value} can't be deleted" }
                doc.copy(categories = doc.categories.filterNot { it.id == id.value }) to Unit
            }
    }
