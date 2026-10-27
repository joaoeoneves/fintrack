package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.mergeCategories
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [CategoryRepository] implementation intended for tests. Holds the stored category
 * documents (overrides of built-ins and user categories) and exposes them merged with the
 * built-ins, like the Firestore implementation. Not wired via Hilt; construct and pass it directly.
 */
class FakeCategoryRepository(
    initialDocs: List<Category> = emptyList(),
    var nextObserveCategoriesError: Throwable? = null,
    var nextWriteError: Throwable? = null,
) : CategoryRepository {
    private val docsFlow = MutableStateFlow(initialDocs)
    private var nextGeneratedId = 1

    /** The stored documents, as written (not merged with the built-ins). */
    val docs: List<Category> get() = docsFlow.value

    /** Replaces every stored document, e.g. to simulate a remote change. */
    fun setDocs(docs: List<Category>) {
        docsFlow.value = docs
    }

    override fun observeCategories(): Flow<List<Category>> {
        nextObserveCategoriesError?.let { error -> return flow { throw error } }
        return docsFlow.map { mergeCategories(it) }
    }

    override suspend fun addCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        kind: CategoryKind,
        groupId: String?,
    ): Result<CategoryId> =
        write {
            val catalog = mergeCategories(docsFlow.value)
            val nextOrder =
                catalog
                    .map { it.order }
                    .filter { it != Int.MAX_VALUE }
                    .maxOrNull()
                    ?.plus(1) ?: 0
            val id = CategoryId("custom-${nextGeneratedId++}")
            val category =
                Category(
                    id = id,
                    customName = name,
                    iconKey = iconKey,
                    colorKey = colorKey,
                    kind = kind,
                    order = nextOrder,
                    archived = false,
                    builtIn = false,
                    groupId = groupId,
                )
            upsert(category)
            id
        }

    override suspend fun updateCategory(category: Category): Result<Unit> = write { upsert(category) }

    override suspend fun setArchived(
        id: CategoryId,
        archived: Boolean,
    ): Result<Unit> =
        write {
            val current = requireNotNull(mergeCategories(docsFlow.value).find { it.id == id }) { "Unknown category" }
            upsert(current.copy(archived = archived))
        }

    override suspend fun setGroup(
        id: CategoryId,
        groupId: String?,
    ): Result<Unit> =
        write {
            val current = requireNotNull(mergeCategories(docsFlow.value).find { it.id == id }) { "Unknown category" }
            upsert(current.copy(groupId = groupId))
        }

    /** Clears the group of every stored category filed under [groupId], as deleting that group does. */
    fun ungroup(groupId: String) {
        docsFlow.value = docsFlow.value.map { if (it.groupId == groupId) it.copy(groupId = null) else it }
    }

    override suspend fun reorder(orderedIds: List<CategoryId>): Result<Unit> =
        write {
            val catalog = mergeCategories(docsFlow.value).associateBy { it.id }
            orderedIds.forEachIndexed { index, id ->
                upsert(requireNotNull(catalog[id]) { "Unknown category" }.copy(order = index))
            }
        }

    override suspend fun deleteCategory(id: CategoryId): Result<Unit> =
        write {
            require(!BuiltInCategories.isBuiltIn(id)) { "Built-in category ${id.value} can't be deleted" }
            docsFlow.value = docsFlow.value.filterNot { it.id == id }
        }

    private fun upsert(category: Category) {
        val current = docsFlow.value
        docsFlow.value =
            if (current.any { it.id == category.id }) {
                current.map { if (it.id == category.id) category else it }
            } else {
                current + category
            }
    }

    private inline fun <T> write(block: () -> T): Result<T> {
        nextWriteError?.let { error ->
            nextWriteError = null
            return Result.failure(error)
        }
        return runCatching(block)
    }
}
