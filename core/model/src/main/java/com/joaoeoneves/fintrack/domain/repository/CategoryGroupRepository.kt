package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import kotlinx.coroutines.flow.Flow

interface CategoryGroupRepository {
    /** The groups sorted by order, then id. */
    fun observeGroups(): Flow<List<CategoryGroup>>

    /** Creates a group at the end of the list; returns its id. */
    suspend fun addGroup(name: String): Result<String>

    suspend fun renameGroup(
        id: String,
        name: String,
    ): Result<Unit>

    /** Persists [orderedIds]' positions as each group's order. */
    suspend fun reorderGroups(orderedIds: List<String>): Result<Unit>

    /** Deletes a group and clears [com.joaoeoneves.fintrack.domain.model.Category.groupId] on its categories. */
    suspend fun deleteGroup(id: String): Result<Unit>
}
