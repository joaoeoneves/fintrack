package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    /** The merged catalog (built-ins plus user categories, archived included); never empty. */
    fun observeCategories(): Flow<List<Category>>

    suspend fun addCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        kind: CategoryKind,
        groupId: String? = null,
    ): Result<CategoryId>

    suspend fun updateCategory(category: Category): Result<Unit>

    suspend fun setArchived(
        id: CategoryId,
        archived: Boolean,
    ): Result<Unit>

    /**
     * Files the category under the group [groupId] (`null` ungroups it). For a built-in this writes its
     * override document.
     */
    suspend fun setGroup(
        id: CategoryId,
        groupId: String?,
    ): Result<Unit>

    /** Persists [orderedIds]' positions as each category's order. */
    suspend fun reorder(orderedIds: List<CategoryId>): Result<Unit>

    /** Deletes a user category; fails with [IllegalArgumentException] for a built-in. */
    suspend fun deleteCategory(id: CategoryId): Result<Unit>
}
